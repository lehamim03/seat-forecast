"""
좌석예보 B · D 담당 API 서버 (13주차) — docs/api.md ③ 실시간 대안 · ⑦ 탑승 피드백

    GET  /api/v1/alternatives?home_stop=203000063&route=8800   (B 실시간 대안)
    POST /api/v1/feedback                                      (D 탑승 피드백)

실행 (저장소 루트에서):
    pip install -r server-bd/requirements.txt
    uvicorn app:app --app-dir server-bd --host 0.0.0.0 --port 8000

- 인증: 헤더 Authorization: Bearer <Firebase ID 토큰> 을 Firebase 로 확인합니다.
  FIREBASE_AUTH_EMULATOR_HOST(예: 127.0.0.1:9099) 가 있으면 로컬 Firebase 에뮬레이터 토큰을 받습니다.
- 대안 계산: 저장소 루트에 api_key.py 와 실시간 계산 환경이 있으면 live_recommend.py 로 지금 상황을 계산하고,
  없거나 실패하면 docs/examples/alternatives.json (예시 응답)을 돌려줍니다. 응답 헤더 X-Data-Source 로 구분.
- 피드백: SQLite (기본 server-bd/feedback.db) 에 저장하고 201 {"id": "..."} 을 돌려줍니다.

팀 공통 서버가 생기면 이 파일의 두 엔드포인트를 그쪽으로 옮기면 됩니다 (다른 화면 API 는 여기에 넣지 않음).
"""
from __future__ import annotations

import json
import os
import re
import sqlite3
import threading
import uuid
from datetime import datetime
from typing import Optional

from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

import auth

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
EXAMPLE = os.path.join(ROOT, "docs", "examples", "alternatives.json")
DB_PATH = os.environ.get("SEAT_FEEDBACK_DB", os.path.join(os.path.dirname(__file__), "feedback.db"))

# config.py 의 STOPS · ROUTES 와 같음 (config.py 는 api_key.py 가 있어야 읽히므로 여기 따로 둠 — test 가 같은지 검사)
STOPS = {"203000063": "삼성1차아파트", "203000161": "자유총연맹", "203000162": "매탄위브하늘채.경기아트센터"}
ROUTES = ("8800", "3008", "3007", "7001")
ROUTE_STOPS = {
    "8800": ("203000063", "203000162"),
    "3008": ("203000063", "203000161", "203000162"),
    "3007": ("203000063", "203000161", "203000162"),
    "7001": ("203000063", "203000161", "203000162"),
}
ALT_KEYS = ("generated_at", "my_route", "home_stop", "day", "recommendation", "alternatives", "subway")

app = FastAPI(title="좌석예보 B·D API", version="13")


# ---------------------------------------------------------------- 오류 형식 {"error": {"code", "message"}}
class ApiError(Exception):
    def __init__(self, status: int, code: str, message: str):
        self.status, self.code, self.message = status, code, message


def error(status: int, code: str, message: str) -> JSONResponse:
    return JSONResponse(status_code=status, content={"error": {"code": code, "message": message}})


@app.exception_handler(ApiError)
async def _api_error(_: Request, e: ApiError):
    return error(e.status, e.code, e.message)


@app.exception_handler(RequestValidationError)
async def _validation(_: Request, e: RequestValidationError):
    first = e.errors()[0] if e.errors() else {}
    where = ".".join(str(x) for x in first.get("loc", []) if x not in ("body", "query"))
    return error(400, "bad_request", f"요청 값이 올바르지 않습니다: {where or '본문'}")


@app.exception_handler(StarletteHTTPException)
async def _http(_: Request, e: StarletteHTTPException):
    return error(e.status_code, "not_found" if e.status_code == 404 else "http_error", str(e.detail))


# ---------------------------------------------------------------- 인증
def current_uid(authorization: Optional[str] = Header(default=None)) -> str:
    if not authorization or not authorization.startswith("Bearer ") or len(authorization) <= 7:
        raise ApiError(401, "unauthorized", "로그인이 필요합니다.")
    uid = auth.verify(authorization[7:])
    if uid is None:
        raise ApiError(401, "invalid_token", "로그인 정보가 만료되었습니다. 다시 로그인해 주세요.")
    return uid


@app.get("/health")
def health():
    return {"ok": True, "live": live_available(), "auth": auth.mode()}


# ---------------------------------------------------------------- ③ 실시간 대안 (B)
_live_lock = threading.Lock()


def live_available() -> bool:
    return os.environ.get("SEAT_LIVE", "1") != "0" and os.path.exists(os.path.join(ROOT, "api_key.py"))


def live_alternatives(home_stop: str, route: str) -> dict:
    """live_recommend.py 로 지금 상황 계산 — 평소 정류장 · 노선만 바꿔서 그대로 부름 (계산 로직은 고치지 않음)"""
    import sys
    if ROOT not in sys.path:
        sys.path.insert(0, ROOT)
    import contextlib
    import io
    import live_recommend
    with _live_lock:   # 모듈 전역(MY_STOP · sys.argv · 작업 폴더)을 잠깐 바꾸므로 한 번에 하나씩
        old_stop, old_argv, old_cwd = live_recommend.MY_STOP, sys.argv, os.getcwd()
        try:
            live_recommend.MY_STOP = home_stop
            sys.argv = ["live_recommend.py", route]
            os.chdir(ROOT)   # board_model.pkl · 저장해 둔 경로를 루트 기준으로 찾음
            with contextlib.redirect_stdout(io.StringIO()):
                result = live_recommend.main()
        finally:
            live_recommend.MY_STOP, sys.argv = old_stop, old_argv
            os.chdir(old_cwd)
    return {k: result.get(k) for k in ALT_KEYS}


def example_alternatives() -> dict:
    with open(EXAMPLE, encoding="utf-8") as f:
        data = json.load(f)
    data.setdefault("subway", None)
    return {k: data.get(k) for k in ALT_KEYS}


@app.get("/api/v1/alternatives")
def alternatives(home_stop: str = Query(...), route: str = Query(...), uid: str = Depends(current_uid)):
    if home_stop not in STOPS:
        raise ApiError(400, "unknown_stop", f"알 수 없는 정류장입니다: {home_stop}")
    if route not in ROUTES:
        raise ApiError(400, "unknown_route", f"알 수 없는 노선입니다: {route}")
    if home_stop not in ROUTE_STOPS[route]:
        raise ApiError(400, "route_not_at_stop", f"{route}번은 {STOPS[home_stop]}에 서지 않습니다.")
    source = "example"
    body = None
    if live_available():
        try:
            body = live_alternatives(home_stop, route)
            source = "live"
        except Exception:   # 공공데이터 API 접속 실패 · 키 없음 등 → 예시 응답으로 대신
            body = None
    if body is None:
        body = example_alternatives()
    return JSONResponse(content=body, headers={"X-Data-Source": source})


# ---------------------------------------------------------------- ⑦ 탑승 피드백 (D)
class Feedback(BaseModel):
    uid: str = Field(min_length=1, max_length=128)
    date: str
    route: str
    stop_id: str
    boarded: bool
    predicted_prob: Optional[float] = Field(default=None, ge=0.0, le=1.0)
    memo: str = Field(default="", max_length=200)


_db_lock = threading.Lock()


def db() -> sqlite3.Connection:
    con = sqlite3.connect(DB_PATH)
    con.execute("""CREATE TABLE IF NOT EXISTS feedback (
        id TEXT PRIMARY KEY, uid TEXT NOT NULL, date TEXT NOT NULL, route TEXT NOT NULL, stop_id TEXT NOT NULL,
        boarded INTEGER NOT NULL, predicted_prob REAL, memo TEXT NOT NULL, received_at TEXT NOT NULL)""")
    return con


@app.post("/api/v1/feedback", status_code=201)
def feedback(fb: Feedback, uid: str = Depends(current_uid)):
    if fb.uid != uid:
        raise ApiError(403, "uid_mismatch", "다른 사용자의 피드백은 보낼 수 없습니다.")
    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", fb.date):
        raise ApiError(400, "bad_date", "날짜는 YYYY-MM-DD 형식이어야 합니다.")
    try:
        datetime.strptime(fb.date, "%Y-%m-%d")
    except ValueError:
        raise ApiError(400, "bad_date", "날짜가 올바르지 않습니다.")
    if fb.route not in ROUTES:
        raise ApiError(400, "unknown_route", f"알 수 없는 노선입니다: {fb.route}")
    if fb.stop_id not in STOPS:
        raise ApiError(400, "unknown_stop", f"알 수 없는 정류장입니다: {fb.stop_id}")
    fid = "fb_" + uuid.uuid4().hex[:12]
    with _db_lock:
        con = db()
        try:
            con.execute("INSERT INTO feedback VALUES (?,?,?,?,?,?,?,?,?)",
                        (fid, uid, fb.date, fb.route, fb.stop_id, int(fb.boarded), fb.predicted_prob, fb.memo,
                         datetime.now().isoformat(timespec="seconds")))
            con.commit()
        finally:
            con.close()
    return {"id": fid}
