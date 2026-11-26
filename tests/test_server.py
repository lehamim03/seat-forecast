# 예측 서버(server.py)가 docs/api.md 형식대로 답하는지 — 실제 공공 API 대신 가상의 버스 위치를 넣어 시험
# 실행:  python -m unittest discover tests      (저장소 루트에서)
import json
import os
import sys
import tempfile
import unittest
from unittest import mock

import pandas as pd

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
os.chdir(ROOT)
os.environ["SKIP_WARMUP"] = "1"
os.environ.pop("FIREBASE_PROJECT_ID", None)

from fastapi.testclient import TestClient   # noqa: E402

import events      # noqa: E402
import incidents   # noqa: E402
import live_recommend as L   # noqa: E402
import server      # noqa: E402

# 가상 상황: 8800 첫 차는 거의 만석, 3007은 여유 (demo_scenarios.py 상황 3과 비슷)
BUSES = {
    "200000205": [{"plateNo": "8800-A", "stationSeq": 5, "remainSeatCnt": 1},
                  {"plateNo": "8800-B", "stationSeq": 2, "remainSeatCnt": 4}],
    "200000110": [{"plateNo": "3007-A", "stationSeq": 8, "remainSeatCnt": 25}],
    "200000119": [], "200000274": [],
}


def fake_get(path, **p):
    return BUSES[p["routeId"]] if "location" in path else []


def example(name):
    with open(os.path.join("docs", "examples", name), encoding="utf-8") as f:
        return json.load(f)


class ServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.patches = [mock.patch.object(L, "get", fake_get), mock.patch.object(L, "USE_TRANSIT", "cache"),
                       mock.patch.object(L, "INCIDENT_EVENTS", []),
                       mock.patch.object(L, "INCIDENT_RECENT", pd.DataFrame(
                           columns=["collected_at", "route_name", "plate_no", "station_seq"])),
                       mock.patch.object(events, "USE_TOURAPI", False),
                       mock.patch.object(incidents, "fetch_events", lambda: [])]
        for p in cls.patches:
            p.start()
        cls.client = TestClient(server.app)

    @classmethod
    def tearDownClass(cls):
        for p in cls.patches:
            p.stop()

    def setUp(self):
        server._cache.clear()

    def get(self, path):
        r = self.client.get(path)
        return r.status_code, r.json()

    def test_recommendations_same_shape_as_example(self):
        code, body = self.get("/api/v1/recommendations?home_stop=203000063&route=8800")
        self.assertEqual(200, code)
        ex = example("recommendations.json")
        self.assertEqual(set(ex), set(body))
        self.assertEqual(set(ex["ranking"][0]), set(body["ranking"][0]))
        self.assertEqual(set(ex["ranking"][0]["buses"][0]), set(body["ranking"][0]["buses"][0]))
        self.assertEqual({"id": "203000063", "name": "삼성1차아파트"}, body["home_stop"])
        self.assertTrue(all(0.0 <= o["board_prob"] <= 1.0 for o in body["ranking"]))
        self.assertRegex(body["ranking"][0]["arrive_at"], r"^\d{2}:\d{2}$")

    def test_alternatives_same_shape_as_example(self):
        code, body = self.get("/api/v1/alternatives?home_stop=203000063&route=8800")
        self.assertEqual(200, code)
        self.assertEqual(set(example("alternatives.json")) | {"subway"}, set(body))
        self.assertEqual({"기다리기", "걸어가기", "다른 버스"}, set(body["alternatives"]))

    def test_incidents_without_incident_has_no_push(self):
        code, body = self.get("/api/v1/incidents?home_stop=203000063&route=8800")
        self.assertEqual(200, code)
        self.assertEqual(set(example("incidents.json")), set(body))
        self.assertEqual([], body["incidents"])
        self.assertIsNone(body["push"])

    def test_push_when_my_route_has_incident(self):
        r = {"my_route": "8800", "recommendation": {"route": "7001", "message": "다른 버스 (7001번) — 돌아가는 게 빠릅니다"},
             "incidents": [{"route": "8800", "delay_min": 20}]}
        self.assertEqual({"title": "8800번 지연 · 7001번 추천", "body": "돌아가는 게 빠릅니다",
                          "data": {"screen": "alternatives", "route": "7001"}}, server.push_for(r))

    def test_other_home_stop(self):
        code, body = self.get("/api/v1/recommendations?home_stop=203000161&route=3007")
        self.assertEqual(200, code)
        self.assertEqual("203000161", body["home_stop"]["id"])
        self.assertEqual("3007", body["my_route"])
        self.assertEqual("203000063", L.MY_STOP)   # 요청이 끝나면 원래 값으로

    def test_errors_use_common_format(self):
        for path, status, code in [("/api/v1/recommendations?home_stop=1&route=8800", 400, "unknown_stop"),
                                   ("/api/v1/recommendations?home_stop=203000063&route=9999", 400, "unknown_route"),
                                   ("/api/v1/nothing", 404, "not_found")]:
            got_status, body = self.get(path)
            self.assertEqual(status, got_status, path)
            self.assertEqual(code, body["error"]["code"], path)
            self.assertTrue(body["error"]["message"])

    def test_feedback_saved(self):
        with tempfile.TemporaryDirectory() as d, mock.patch.object(server, "FEEDBACK_FILE", os.path.join(d, "f.jsonl")):
            r = self.client.post("/api/v1/feedback", json=example("feedback.json"))
            self.assertEqual(201, r.status_code)
            fid = r.json()["id"]
            with open(os.path.join(d, "f.jsonl"), encoding="utf-8") as f:
                row = json.loads(f.readline())
            self.assertEqual(fid, row["id"])
            self.assertTrue(row["boarded"])
            bad = self.client.post("/api/v1/feedback", json={"route": "8800"})
            self.assertEqual(400, bad.status_code)
            self.assertEqual("bad_request", bad.json()["error"]["code"])

    def test_auth_required_when_project_set(self):
        with mock.patch.object(server, "FIREBASE_PROJECT_ID", "seat-forecast-test"):
            code, body = self.get("/api/v1/recommendations")
            self.assertEqual(401, code)
            self.assertEqual("unauthorized", body["error"]["code"])
            def reject(_):
                raise ValueError("만료된 토큰")
            with mock.patch.object(server, "verify_token", reject):
                r = self.client.get("/api/v1/recommendations", headers={"Authorization": "Bearer expired"})
                self.assertEqual(401, r.status_code)
            with mock.patch.object(server, "verify_token", lambda t: {"user_id": "u1"}):
                r = self.client.get("/api/v1/recommendations", headers={"Authorization": "Bearer ok"})
                self.assertEqual(200, r.status_code)

    def test_replay_past_morning(self):
        # data/ 에 있는 지난 평일 아침을 재생 — 추천 + 실제 결과(actual)
        code, body = self.get("/api/v1/recommendations?replay=2026-11-12T07:40")
        self.assertEqual(200, code, body)
        self.assertTrue(body["generated_at"].startswith("2026-11-12T07:40"))
        self.assertEqual("2026-11-12", body["replay"]["date"])
        a = body["ranking"][0]["actual"]
        self.assertEqual({"boarded_at", "full_skipped", "arrive_at", "total_min"}, set(a))
        self.assertTrue(body["replay"]["message"])
        code, days = self.get("/api/v1/replay/days")
        self.assertIn("2026-11-12", days["days"])

    def test_replay_errors(self):
        code, body = self.get("/api/v1/recommendations?replay=2020-01-01T07:40")
        self.assertEqual((400, "replay_no_data"), (code, body["error"]["code"]))
        code, body = self.get("/api/v1/recommendations?replay=어제")
        self.assertEqual((400, "bad_request"), (code, body["error"]["code"]))
        code, body = self.get("/api/v1/recommendations?replay=2026-11-12T07:40&from_lat=37.3&from_lng=127.1"
                              "&to_lat=37.6&to_lng=126.9")
        self.assertEqual((400, "bad_request"), (code, body["error"]["code"]))

    def test_health(self):
        code, body = self.get("/health")
        self.assertEqual(200, code)
        self.assertEqual("ok", body["status"])


if __name__ == "__main__":
    unittest.main()
