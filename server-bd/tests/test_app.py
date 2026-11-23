"""server-bd 테스트 — 저장소 루트에서:  python -m pytest server-bd -q"""
import json
import os
import sqlite3
import sys
import types
import urllib.request

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
SERVER = os.path.dirname(HERE)
ROOT = os.path.dirname(SERVER)
sys.path.insert(0, SERVER)

import app as server  # noqa: E402
import auth  # noqa: E402
from fastapi.testclient import TestClient  # noqa: E402

DOCS = os.path.join(ROOT, "docs", "examples")
TOKEN = {"Authorization": "Bearer good-token"}


@pytest.fixture
def client(monkeypatch, tmp_path):
    monkeypatch.setattr(auth, "verify", lambda t: "uid-123" if t == "good-token" else None)
    monkeypatch.setattr(server, "DB_PATH", str(tmp_path / "feedback.db"))
    monkeypatch.setattr(server, "live_available", lambda: False)
    return TestClient(server.app)


def load(name):
    with open(os.path.join(DOCS, name), encoding="utf-8") as f:
        return json.load(f)


# ---------------------------------------------------------------- 정류장 · 노선 목록이 config.py 와 같은지
def test_stops_and_routes_match_config(monkeypatch):
    fake_key = types.ModuleType("api_key")
    fake_key.__getattr__ = lambda name: "test"
    monkeypatch.setitem(sys.modules, "api_key", fake_key)
    monkeypatch.syspath_prepend(ROOT)
    sys.modules.pop("config", None)
    import config
    assert set(server.STOPS) == set(config.STOPS)
    for sid, name in server.STOPS.items():
        assert config.STOPS[sid]["name"] == name
    assert set(server.ROUTES) == set(config.ROUTES)
    for r, stops in server.ROUTE_STOPS.items():
        assert set(stops) == set(config.ROUTES[r]["stops"])


# ---------------------------------------------------------------- ③ GET /api/v1/alternatives
def test_alternatives_returns_docs_example(client):
    res = client.get("/api/v1/alternatives", params={"home_stop": "203000063", "route": "8800"}, headers=TOKEN)
    assert res.status_code == 200
    assert res.headers["X-Data-Source"] == "example"
    body = res.json()
    assert list(body) == list(server.ALT_KEYS)
    expected = load("alternatives.json")
    for k in expected:
        assert body[k] == expected[k]
    assert body["subway"] is None
    assert set(body["alternatives"]) == {"기다리기", "걸어가기", "다른 버스"}


def test_alternatives_needs_login(client):
    res = client.get("/api/v1/alternatives", params={"home_stop": "203000063", "route": "8800"})
    assert res.status_code == 401
    assert res.json() == {"error": {"code": "unauthorized", "message": "로그인이 필요합니다."}}
    res = client.get("/api/v1/alternatives", params={"home_stop": "203000063", "route": "8800"},
                     headers={"Authorization": "Bearer expired"})
    assert res.status_code == 401
    assert res.json()["error"]["code"] == "invalid_token"


@pytest.mark.parametrize("params,code", [
    ({"home_stop": "999", "route": "8800"}, "unknown_stop"),
    ({"home_stop": "203000063", "route": "1234"}, "unknown_route"),
    ({"home_stop": "203000161", "route": "8800"}, "route_not_at_stop"),   # 8800 은 자유총연맹에 서지 않음
    ({"route": "8800"}, "bad_request"),
])
def test_alternatives_bad_request(client, params, code):
    res = client.get("/api/v1/alternatives", params=params, headers=TOKEN)
    assert res.status_code == 400
    assert res.json()["error"]["code"] == code


def test_alternatives_uses_live_when_available(client, monkeypatch):
    live = {k: None for k in server.ALT_KEYS}
    live.update(my_route="3007", alternatives={"기다리기": None, "걸어가기": None, "다른 버스": None})
    seen = {}

    def fake_live(home_stop, route):
        seen.update(home_stop=home_stop, route=route)
        return live

    monkeypatch.setattr(server, "live_available", lambda: True)
    monkeypatch.setattr(server, "live_alternatives", fake_live)
    res = client.get("/api/v1/alternatives", params={"home_stop": "203000161", "route": "3007"}, headers=TOKEN)
    assert res.status_code == 200
    assert res.headers["X-Data-Source"] == "live"
    assert res.json()["my_route"] == "3007"
    assert seen == {"home_stop": "203000161", "route": "3007"}


def test_alternatives_falls_back_when_live_fails(client, monkeypatch):
    def broken(home_stop, route):
        raise ConnectionError("공공데이터포털 접속 실패")

    monkeypatch.setattr(server, "live_available", lambda: True)
    monkeypatch.setattr(server, "live_alternatives", broken)
    res = client.get("/api/v1/alternatives", params={"home_stop": "203000063", "route": "8800"}, headers=TOKEN)
    assert res.status_code == 200
    assert res.headers["X-Data-Source"] == "example"


# ---------------------------------------------------------------- ⑦ POST /api/v1/feedback
def feedback_body(**change):
    body = load("feedback.json")
    body["uid"] = "uid-123"
    body.update(change)
    return body


def test_feedback_is_stored(client):
    res = client.post("/api/v1/feedback", json=feedback_body(memo="자리 있었어요"), headers=TOKEN)
    assert res.status_code == 201
    fid = res.json()["id"]
    assert fid.startswith("fb_")
    con = sqlite3.connect(server.DB_PATH)
    row = con.execute("SELECT uid, date, route, stop_id, boarded, predicted_prob, memo FROM feedback WHERE id=?",
                      (fid,)).fetchone()
    con.close()
    assert row == ("uid-123", "2026-10-07", "3007", "203000161", 1, 1.0, "자리 있었어요")


def test_feedback_null_prob_and_not_boarded(client):
    res = client.post("/api/v1/feedback", json=feedback_body(boarded=False, predicted_prob=None), headers=TOKEN)
    assert res.status_code == 201


def test_feedback_needs_login(client):
    assert client.post("/api/v1/feedback", json=feedback_body()).status_code == 401


def test_feedback_other_users_uid_is_forbidden(client):
    res = client.post("/api/v1/feedback", json=feedback_body(uid="someone-else"), headers=TOKEN)
    assert res.status_code == 403
    assert res.json()["error"]["code"] == "uid_mismatch"


@pytest.mark.parametrize("change,code", [
    ({"date": "2026/10/07"}, "bad_date"),
    ({"date": "2026-02-30"}, "bad_date"),
    ({"route": "9999"}, "unknown_route"),
    ({"stop_id": "1"}, "unknown_stop"),
    ({"predicted_prob": 1.5}, "bad_request"),
    ({"memo": "가" * 201}, "bad_request"),
    ({"boarded": None}, "bad_request"),
])
def test_feedback_bad_request(client, change, code):
    res = client.post("/api/v1/feedback", json=feedback_body(**change), headers=TOKEN)
    assert res.status_code == 400
    assert res.json()["error"]["code"] == code


# ---------------------------------------------------------------- 인증
def test_auth_off_mode_uses_token_as_uid(monkeypatch):
    monkeypatch.setenv("SEAT_AUTH", "off")
    assert auth.verify("abc") == "abc"


def test_auth_rejects_garbage_token(monkeypatch):
    monkeypatch.setenv("SEAT_AUTH", "firebase")
    monkeypatch.setenv("FIREBASE_AUTH_EMULATOR_HOST", "127.0.0.1:1")   # 연결 안 해도 형식에서 거절
    assert auth.verify("not-a-jwt") is None


@pytest.mark.skipif(not os.environ.get("FIREBASE_AUTH_EMULATOR_HOST"),
                    reason="Firebase Auth 에뮬레이터를 켜고 FIREBASE_AUTH_EMULATOR_HOST 를 넣었을 때만")
def test_real_emulator_token(monkeypatch):
    """로컬 Firebase 에뮬레이터에서 실제로 가입해 받은 ID 토큰을 서버가 받아들이는지"""
    monkeypatch.setenv("SEAT_AUTH", "firebase")
    host = os.environ["FIREBASE_AUTH_EMULATOR_HOST"]
    req = urllib.request.Request(
        f"http://{host}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo",
        data=json.dumps({"returnSecureToken": True}).encode(), headers={"Content-Type": "application/json"})
    signed = json.load(urllib.request.urlopen(req))
    assert auth.verify(signed["idToken"]) == signed["localId"]
