# 출발지 · 도착지 모드(trip.py + server.py)가 약속한 형식대로 답하는지 — ODsay · 도착정보 API 는 가짜 응답으로
# 실행:  python -m unittest discover tests
import json
import os
import sys
import unittest
from unittest import mock

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
sys.path.insert(0, ROOT)
os.chdir(ROOT)
os.environ["SKIP_WARMUP"] = "1"
os.environ.pop("FIREBASE_PROJECT_ID", None)

from fastapi.testclient import TestClient   # noqa: E402

import anywhere    # noqa: E402
import incidents   # noqa: E402
import server      # noqa: E402
import trip        # noqa: E402

with open(os.path.join(ROOT, "tests", "fixtures", "odsay_trip.json"), encoding="utf-8") as f:
    PATHS = json.load(f)["result"]["path"]

# 서현역.AK프라자(206000299)에 다가오는 차: M4102 첫 차는 거의 만석, 두 번째는 여유 · 8100 은 여유
ARRIVALS = {"206000299": [
    {"routeName": "M4102", "routeId": "234001159", "routeTypeCd": 14, "staOrder": 6,
     "predictTime1": 6, "locationNo1": 2, "remainSeatCnt1": 1, "plateNo1": "경기77바1",
     "predictTime2": 16, "locationNo2": 6, "remainSeatCnt2": 30, "plateNo2": "경기77바2"},
    {"routeName": "8100", "routeId": "234000878", "routeTypeCd": 11, "staOrder": 7,
     "predictTime1": 9, "locationNo1": 3, "remainSeatCnt1": 22, "plateNo1": "경기70바3"},
]}
FROM = "from_lat=37.3850&from_lng=127.1233&from_name=현재 위치&to_lat=37.6110&to_lng=126.9952&to_name=국민대학교"


def fake_get(path, **p):
    return ARRIVALS.get(p.get("stationId"), []) if "arrival" in path else []


class TripTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.patches = [mock.patch.object(trip, "odsay_paths", lambda *a: PATHS),
                       mock.patch.object(anywhere, "get", fake_get),
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
        trip._arrivals.clear()

    def get(self, path):
        r = self.client.get(path)
        return r.status_code, r.json()

    def test_recommendations(self):
        code, body = self.get(f"/api/v1/recommendations?{FROM}")
        self.assertEqual(200, code, body)
        self.assertEqual({"name": "국민대학교", "lat": 37.611, "lng": 126.9952}, body["destination"])
        self.assertEqual("현재 위치", body["origin"]["name"])
        self.assertIsNone(body["my_route"])
        routes = {o["route"]: o for o in body["ranking"]}
        self.assertIn("M4102", routes)
        self.assertIn("8100", routes)
        self.assertIn("수인분당선", routes)
        self.assertNotIn("N4102", routes)                      # 예약 · 심야 경로는 뺌
        m = routes["M4102"]
        self.assertTrue(m["seat_limited"])
        self.assertTrue(m["seat_known"])
        self.assertEqual("bus", m["first_mode"])
        self.assertEqual("206000299", m["stop"]["id"])
        self.assertEqual(2, len(m["buses"]))
        self.assertLess(m["buses"][0]["board_prob"], m["buses"][1]["board_prob"])   # 거의 만석인 첫 차가 더 낮음
        sub = routes["수인분당선"]
        self.assertEqual(("지하철", "subway", 1.0, False), (sub["kind"], sub["first_mode"], sub["board_prob"], sub["seat_limited"]))
        for o in body["ranking"]:
            self.assertRegex(o["arrive_at"], r"^\d{2}:\d{2}$")
            self.assertTrue(o["summary"])
            self.assertIn(o["kind"], {"기다리기", "걸어가기", "다른 버스", "지하철"})
        totals = [o["total_min"] for o in body["ranking"]]
        self.assertEqual(sorted(totals), totals)

    def test_alternatives_and_subway(self):
        code, body = self.get(f"/api/v1/alternatives?{FROM}")
        self.assertEqual(200, code, body)
        self.assertEqual({"기다리기", "걸어가기", "다른 버스"}, set(body["alternatives"]))
        self.assertIsNotNone(body["subway"])
        self.assertIn("수인분당선", body["subway"]["summary"])

    def test_map_route_choices_incidents(self):
        code, geo = self.get(f"/api/v1/map?{FROM}")
        self.assertEqual(200, code)
        kinds = {f["properties"]["kind"] for f in geo["features"]}
        self.assertTrue({"me", "destination", "stop", "transfer"} <= kinds)
        code, rc = self.get(f"/api/v1/route-choices?{FROM}")
        self.assertEqual(200, code)
        self.assertTrue(rc["routes"] and rc["routes"][0]["choices"])
        code, inc = self.get(f"/api/v1/incidents?{FROM}")
        self.assertEqual(200, code)
        self.assertEqual([], inc["incidents"])
        self.assertIsNone(inc["push"])

    def test_seat_unknown_uses_prior(self):
        # 서울 등록 좌석버스처럼 좌석 수가 안 나오면 확률은 평소 탑승률, 만석 경고는 하지 않음
        no_seats = {"206000299": [dict(a, remainSeatCnt1=-1, remainSeatCnt2=-1) for a in ARRIVALS["206000299"]]}
        with mock.patch.object(anywhere, "get", lambda path, **p: no_seats.get(p.get("stationId"), [])):
            code, body = self.get(f"/api/v1/recommendations?{FROM}")
        m = {o["route"]: o for o in body["ranking"]}["M4102"]
        self.assertTrue(m["seat_limited"])
        self.assertFalse(m["seat_known"])
        self.assertIn(m["board_prob"], (trip.PRIOR_PEAK, trip.PRIOR_OTHER))
        self.assertNotIn("만석 위험", body["recommendation"]["message"])

    def test_missing_coordinate(self):
        code, body = self.get("/api/v1/recommendations?from_lat=37.38&from_lng=127.12&to_lat=37.61")
        self.assertEqual(400, code)
        self.assertEqual("bad_request", body["error"]["code"])

    def test_route_unavailable(self):
        def fail(*a):
            raise trip.RouteUnavailable("ApiKeyAuthFailed")
        with mock.patch.object(trip, "odsay_paths", fail), mock.patch.object(trip, "saved_nearby", lambda *a, **k: None):
            code, body = self.get(f"/api/v1/recommendations?{FROM}")
        self.assertEqual(503, code)
        self.assertEqual("route_unavailable", body["error"]["code"])

    def test_saved_nearby_when_odsay_down(self):
        # ODsay 한도 · IP 문제로 조회가 안 되면 1.5km 안 저장 경로 + 도보만 다시 계산
        def fail(*a):
            raise trip.RouteUnavailable("Daily quota exceeded")
        with mock.patch.object(trip, "odsay_paths", fail), \
                mock.patch.object(trip, "saved_nearby", lambda *a, **k: (PATHS, 0.6)):
            code, body = self.get("/api/v1/recommendations?from_lat=37.3953&from_lng=127.1281&to_lat=37.611&to_lng=126.9952")
        self.assertEqual(200, code, body)
        self.assertEqual("saved_nearby", body["route_source"])
        m = {o["route"]: o for o in body["ranking"]}["M4102"]
        self.assertGreater(m["walk_min"], 4)        # 실제 출발지에서 다시 계산한 도보 (저장 경로는 4분)
        with mock.patch.object(trip, "odsay_paths", fail), mock.patch.object(trip, "saved_nearby", lambda *a, **k: None):
            server._cache.clear()
            code, body = self.get(f"/api/v1/recommendations?{FROM}")
        self.assertEqual(503, code)
        self.assertIn("한도", body["error"]["message"])

    def test_home_area_merges_measured(self):
        # 집 근처 → 국민대: ODsay 에 없는 삼성1차아파트 8800 을 실측 추천에서 가져와 함께 순위
        home = {"generated_at": "2026-11-30T07:40:00+09:00", "day": {"label": "평일 출퇴근 시간대", "kind": "평일 출퇴근",
                                                                      "drop_factor": 1.0, "headway_factor": 1.0},
                "recommendation": {"kind": "기다리기", "route": "8800", "stop_name": "삼성1차아파트",
                                   "message": "기다리기 (8800번 · 삼성1차아파트)"},
                "incidents": [],
                "ranking": [{"kind": "기다리기", "route": "8800", "stop": {"id": "203000063", "name": "삼성1차아파트", "mobile_no": "04007"},
                             "walk_min": 0, "board_prob": 0.9, "arrive_at": "08:40", "total_min": 60, "delay_min": 0,
                             "skipped": False, "alight_stop": "서울시청",
                             "buses": [{"order": 1, "eta_min": 5, "seats_at_stop": 10, "board_prob": 0.9, "reachable": True}],
                             "route_choices": [{"arrive_at": "08:40", "transfers": 1, "walk_min": 6, "tags": ["추천"],
                                                "alight_stop": "서울시청", "summary": "도보 6분 → 버스 1711(서울신문사→국민대앞)"}]}]}
        with mock.patch.object(server, "near_home", lambda *a: ("203000063", 0.2)), \
                mock.patch.object(server, "live_result", lambda *a: home), \
                mock.patch.object(server, "stop_coords", lambda: {"203000063": (37.3850, 127.1233)}):   # 출발지 바로 옆
            code, body = self.get(f"/api/v1/recommendations?{FROM}")
        self.assertEqual(200, code, body)
        self.assertEqual("measured", body["route_source"])
        top = body["ranking"][0]
        self.assertEqual(("8800", "bus", True), (top["route"], top["first_mode"], top["seat_limited"]))
        self.assertIn("버스 8800(삼성1차아파트→서울시청)", top["summary"])
        self.assertIn("수인분당선", {o["route"] for o in body["ranking"]})   # ODsay 쪽 지하철도 함께

    def test_night_bus_leg_replaced(self):
        # 광역버스 → 심야버스(N) 경로는 버리지 않고, 심야 구간을 낮 경로로 바꿔 붙임
        night = {"pathType": 2, "info": {"totalTime": 90, "mapObj": "x"}, "subPath": [
            {"trafficType": 3, "sectionTime": 5},
            {"trafficType": 2, "sectionTime": 50, "lane": [{"busNo": "3007", "type": 4}], "startName": "A", "startX": 127.0, "startY": 37.3,
             "endName": "강남역", "endX": 127.03, "endY": 37.5},
            {"trafficType": 2, "sectionTime": 35, "lane": [{"busNo": "N31", "type": 11}], "startName": "강남역",
             "startX": 127.03, "startY": 37.5, "endName": "국민대", "endX": 126.99, "endY": 37.61}]}
        day = [{"pathType": 1, "info": {"totalTime": 40}, "subPath": [
            {"trafficType": 1, "sectionTime": 40, "lane": [{"name": "수도권 4호선"}], "startName": "강남", "endName": "길음"}]}]
        with mock.patch.object(trip, "odsay_paths", lambda *a: day):
            out = trip.splice_night([night], 126.99, 37.61)
        self.assertEqual(1, len(out))
        self.assertEqual(95, out[0]["info"]["totalTime"])     # 앞 55분 + 낮 경로 40분
        self.assertFalse(trip.special(out[0]))

    def test_places(self):
        with mock.patch.object(trip, "places", lambda q: [{"name": "국민대학교", "address": "서울 성북구",
                                                            "lat": 37.611, "lng": 126.995}]):
            code, body = self.get("/api/v1/places?q=국민대")
        self.assertEqual(200, code)
        self.assertEqual("국민대학교", body["places"][0]["name"])

    def test_feedback_any_route(self):
        import tempfile
        with tempfile.TemporaryDirectory() as d, mock.patch.object(server, "FEEDBACK_FILE", os.path.join(d, "f.jsonl")):
            self._post_feedback()

    def _post_feedback(self):
        r = self.client.post("/api/v1/feedback", json={"date": "2026-11-26", "route": "M4102", "stop_id": "206000299",
                                                       "boarded": False, "predicted_prob": 0.4})
        self.assertEqual(201, r.status_code)


if __name__ == "__main__":
    unittest.main()
