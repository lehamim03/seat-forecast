# ==========================================================
#  좌석예보 예측 서버 (FastAPI) — 앱이 부르는 API, 형식은 docs/api.md
#  실행:  uvicorn server:app --host 0.0.0.0 --port 8000
#         (안드로이드 에뮬레이터에서는 http://10.0.2.2:8000/ 이 이 서버)
#
#  GET  /api/v1/recommendations   ② 홈 — 정류장 × 노선 추천 순위
#  GET  /api/v1/alternatives      ③ 실시간 대안 — 기다리기 / 걸어가기 / 다른 버스 + 지하철
#  GET  /api/v1/map               ④ 지도 GeoJSON
#  GET  /api/v1/route-choices     ⑤ 서울에서 국민대까지 경로 여러 개
#  GET  /api/v1/incidents         ⑥ 돌발 알림 + 푸시 내용
#  POST /api/v1/feedback          ⑦ 탑승 피드백 (feedback/feedback.jsonl 에 저장)
#  GET  /health                   서버 상태
#
#  계산은 live_recommend.py · transit.py · map_data.py 를 그대로 씁니다 (실시간 API + board_model.pkl).
#  인증: 환경변수 FIREBASE_PROJECT_ID 를 정하면 Firebase ID 토큰을 확인하고, 없으면 확인하지 않습니다(개발용).
# ==========================================================
import base64
import contextlib
import io
import json
import logging
import os
import threading
import time
import uuid
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, Header, Query, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field
from starlette.exceptions import HTTPException as StarletteHTTPException

import config
import live_recommend as L

log = logging.getLogger("seat-forecast")
KST = timezone(timedelta(hours=9))

RECOMMEND_CACHE_SEC = 30      # 요청이 이어지는 정류장 · 노선은 이 간격으로 뒤에서 다시 계산
ROUTE_CHOICES_CACHE_SEC = 600
MAP_CACHE_SEC = 60
FEEDBACK_FILE = os.path.join("feedback", "feedback.jsonl")

FIREBASE_PROJECT_ID = os.environ.get("FIREBASE_PROJECT_ID", "").strip()
AUTH_EMULATOR = os.environ.get("FIREBASE_AUTH_EMULATOR_HOST", "").strip()   # Firebase 로컬 에뮬레이터 토큰 허용

@contextlib.asynccontextmanager
async def lifespan(_app):
    start_background()   # 서버가 켜질 때: 미리 계산 · 뒤에서 다시 계산 (아래에 정의)
    yield


app = FastAPI(title="좌석예보 예측 서버", version="1.0", lifespan=lifespan)
_lock = threading.Lock()      # 계산 모듈이 전역값(평소 정류장 · 노선)을 쓰므로 한 번에 하나씩 계산
_cache = {}
_feedback_lock = threading.Lock()


# ---------- 오류 응답: {"error": {"code", "message"}} ----------
class ApiError(Exception):
    def __init__(self, status, code, message):
        self.status, self.code, self.message = status, code, message


def error_body(code, message):
    return {"error": {"code": code, "message": message}}


@app.exception_handler(ApiError)
async def _api_error(_, e: ApiError):
    return JSONResponse(error_body(e.code, e.message), status_code=e.status)


@app.exception_handler(RequestValidationError)
async def _validation_error(_, e: RequestValidationError):
    where = ", ".join(".".join(str(x) for x in err["loc"][1:]) or "본문" for err in e.errors())
    return JSONResponse(error_body("bad_request", f"요청 값이 올바르지 않습니다: {where}"), status_code=400)


@app.exception_handler(StarletteHTTPException)
async def _http_error(_, e: StarletteHTTPException):
    code = {404: "not_found", 405: "method_not_allowed"}.get(e.status_code, "http_error")
    return JSONResponse(error_body(code, str(e.detail)), status_code=e.status_code)


@app.exception_handler(Exception)
async def _server_error(_, e: Exception):
    log.exception("처리 중 오류")
    return JSONResponse(error_body("internal", "서버에서 추천을 계산하지 못했습니다. 잠시 후 다시 시도해 주세요."),
                        status_code=500)


# ---------- 인증: Authorization: Bearer <Firebase ID 토큰> ----------
def _emulator_claims(token):
    """Firebase Auth 에뮬레이터 토큰은 서명이 없어 내용만 읽음 (FIREBASE_AUTH_EMULATOR_HOST 를 정했을 때만)."""
    payload = token.split(".")[1]
    claims = json.loads(base64.urlsafe_b64decode(payload + "=" * (-len(payload) % 4)))
    if claims.get("aud") != FIREBASE_PROJECT_ID:
        raise ValueError("다른 프로젝트의 토큰")
    return claims


def verify_token(token):
    if AUTH_EMULATOR:
        return _emulator_claims(token)
    from google.auth.transport import requests as google_requests
    from google.oauth2 import id_token
    return id_token.verify_firebase_token(token, google_requests.Request(), audience=FIREBASE_PROJECT_ID)


def current_user(authorization: str | None = Header(default=None)):
    """로그인한 사용자 uid. FIREBASE_PROJECT_ID 가 없으면 확인하지 않음(개발용 — uid None)."""
    if not FIREBASE_PROJECT_ID:
        return None
    if not authorization or not authorization.startswith("Bearer "):
        raise ApiError(401, "unauthorized", "로그인이 필요합니다 (Authorization: Bearer <Firebase ID 토큰>)")
    try:
        claims = verify_token(authorization[len("Bearer "):].strip())
    except Exception:
        raise ApiError(401, "unauthorized", "로그인 정보가 만료되었거나 올바르지 않습니다. 다시 로그인해 주세요.")
    return claims.get("user_id") or claims.get("sub")


# ---------- 계산 ----------
def check_stop_route(home_stop, route):
    if home_stop not in config.STOPS:
        raise ApiError(400, "unknown_stop", f"지원하지 않는 정류장입니다: {home_stop} (가능: {', '.join(config.STOPS)})")
    if route not in config.ROUTES:
        raise ApiError(400, "unknown_route", f"지원하지 않는 노선입니다: {route} (가능: {', '.join(config.ROUTES)})")


@contextlib.contextmanager
def as_user(home_stop, route):
    """계산 모듈들이 쓰는 평소 정류장 · 노선(config 값을 가져온 전역)을 이번 요청 값으로 잠시 바꿈."""
    import events, incidents, map_data, transit
    mods = [m for m in (L, transit, incidents, events, map_data)]
    saved = [(m, getattr(m, "MY_STOP", None), getattr(m, "MY_ROUTE", None)) for m in mods]
    try:
        for m in mods:
            if hasattr(m, "MY_STOP"):
                m.MY_STOP = home_stop
            if hasattr(m, "MY_ROUTE"):
                m.MY_ROUTE = route
        yield
    finally:
        for m, stop, r in saved:
            if stop is not None:
                m.MY_STOP = stop
            if r is not None:
                m.MY_ROUTE = r


def cached(key, seconds, fn):
    hit = _cache.get(key)
    if hit and time.monotonic() - hit[0] < seconds:
        return hit[1]
    value = fn()
    _cache[key] = (time.monotonic(), value)
    return value


def now_kst():
    """한국 시각 (서버 컴퓨터 시간대와 상관없이) — 계산 모듈은 시간대 없는 시각을 씀"""
    return datetime.now(KST).replace(tzinfo=None)


_jobs = {}   # 키 → [계산 함수, 다시 계산 간격(초), 마지막 요청 시각] — 최근 요청된 것만 뒤에서 미리 계산
ACTIVE_SEC = 300       # 5분 동안 요청이 없으면 미리 계산을 멈춤 (하루 API 호출 한도 아끼기)
STALE_SLACK_SEC = 60   # 미리 계산한 결과를 '간격 + 이 시간'까지는 그대로 돌려줌


def kept_fresh(key, interval, compute):
    """공공 API 가 느려(호출당 1~4초) 요청 때 계산하면 앱 제한 시간(10초)에 걸릴 수 있으므로,
    한 번 요청된 것은 ACTIVE_SEC 동안 뒤에서 interval 마다 다시 계산해 두고 요청에는 바로 돌려줌."""
    now = time.monotonic()
    _jobs[key] = [compute, interval, now]
    hit = _cache.get(key)
    if hit and now - hit[0] < interval + STALE_SLACK_SEC:
        return hit[1]
    value = compute()
    _cache[key] = (time.monotonic(), value)
    return value


def fresh_loop():
    while True:
        time.sleep(5)
        for key, (compute, interval, last) in list(_jobs.items()):
            now = time.monotonic()
            if now - last > ACTIVE_SEC:
                _jobs.pop(key, None)
                continue
            hit = _cache.get(key)
            if hit and now - hit[0] < interval:
                continue
            try:
                _cache[key] = (time.monotonic(), compute())
            except Exception:
                log.exception("미리 계산 실패 — 이전 결과 계속 사용: %s", key)


def _compute_live(home_stop, route):
    with _lock, as_user(home_stop, route), contextlib.redirect_stdout(io.StringIO()):
        L.NOW_OVERRIDE = now_kst()
        try:
            return L.main(route)
        finally:
            L.NOW_OVERRIDE = None


def live_result(home_stop, route):
    """live_recommend.main() 의 앱 응답 전체 (ranking · alternatives · incidents · subway …)"""
    check_stop_route(home_stop, route)
    return kept_fresh(("live", home_stop, route), RECOMMEND_CACHE_SEC, lambda: _compute_live(home_stop, route))

KEEP = ("generated_at", "my_route", "home_stop", "day", "recommendation")

ITS_CACHE_SEC = 60            # 국가교통정보센터 돌발상황 목록


def _memo_transit():
    """요청마다 다시 계산하면 느린 것들을 서버 안에서만 잠시 저장 — 계산 모듈 자체는 그대로.
    · transit.best_alight: 노선마다 하차 후보 × ODsay 조회(실패한 조회는 매번 다시 시도됨)라 수십 초 걸림
    · incidents.fetch_events: 돌발상황 API"""
    import incidents, transit
    orig_alight, orig_events = transit.best_alight, incidents.fetch_events

    def best_alight(route, now=None, fetch=True):
        key = ("alight", route, transit.is_peak(now), fetch or "cache")
        if key not in _legs:
            _legs[key] = (now, orig_alight(route, now, fetch))
        return _legs[key][1]

    def refresh_loop():
        """저장한 경로를 LEGS_REFRESH_SEC 마다 뒤에서 다시 계산해 바꿔 둠 — 요청이 기다리지 않도록"""
        while True:
            time.sleep(LEGS_REFRESH_SEC)
            for key, (now, _) in list(_legs.items()):
                route, _, _, fetch = key[1:]
                try:
                    with contextlib.redirect_stdout(io.StringIO()):
                        _legs[key] = (now, orig_alight(route, now, fetch is True))
                except Exception:
                    log.exception("경로 다시 계산 실패 — 이전 결과 계속 사용")

    transit.best_alight = best_alight
    incidents.fetch_events = lambda: cached(("its",), ITS_CACHE_SEC, orig_events)
    return refresh_loop


_legs = {}
LEGS_REFRESH_SEC = 1200
_refresh_loop = _memo_transit()


def warm_up():
    """서버를 켜자마자 한 번 계산해 둠 — 첫 요청이 오래 걸리지 않도록 (수집 데이터 읽기 · 경로 조회)"""
    try:
        live_result(config.MY_STOP, config.MY_ROUTE)
        route_choices(uid=None)
        map_api(home_stop=config.MY_STOP, uid=None)
        log.warning("미리 계산 완료 — 요청을 받을 준비가 됐습니다.")
    except Exception:
        log.exception("미리 계산 실패 (요청 때 다시 시도)")


def start_background():
    if os.environ.get("SKIP_WARMUP") != "1":
        threading.Thread(target=warm_up, daemon=True).start()
    threading.Thread(target=_refresh_loop, daemon=True).start()
    threading.Thread(target=fresh_loop, daemon=True).start()


# ---------- API ----------
@app.get("/health")
def health():
    return {"status": "ok", "time": datetime.now(KST).isoformat(timespec="seconds"),
            "model": os.path.exists("board_model.pkl"), "auth": bool(FIREBASE_PROJECT_ID)}


@app.get("/api/v1/recommendations")
def recommendations(home_stop: str = Query(config.MY_STOP), route: str = Query(config.MY_ROUTE),
                    uid=Depends(current_user)):
    r = live_result(home_stop, route)
    return {**{k: r[k] for k in KEEP}, "ranking": r["ranking"]}


@app.get("/api/v1/alternatives")
def alternatives(home_stop: str = Query(config.MY_STOP), route: str = Query(config.MY_ROUTE),
                 uid=Depends(current_user)):
    r = live_result(home_stop, route)
    return {**{k: r[k] for k in KEEP}, "alternatives": r["alternatives"], "subway": r.get("subway")}


def push_for(r):
    """평소 노선에 돌발이 있으면 FCM 알림 내용 (없으면 None) — api_examples.py 와 같은 문구"""
    mine = [i for i in r["incidents"] if i["route"] == r["my_route"]]
    rec = r["recommendation"]
    if not mine or not rec:
        return None
    head = f"{r['my_route']}번 지연" if mine[0]["delay_min"] else f"{r['my_route']}번 돌발상황"
    title = head if rec["route"] == r["my_route"] else f"{head} · {rec['route']}번 추천"
    return {"title": title, "body": rec["message"].split(" — ", 1)[-1],
            "data": {"screen": "alternatives", "route": rec["route"]}}


@app.get("/api/v1/incidents")
def incidents_api(home_stop: str = Query(config.MY_STOP), route: str = Query(config.MY_ROUTE),
                  uid=Depends(current_user)):
    r = live_result(home_stop, route)
    return {"generated_at": r["generated_at"], "incidents": r["incidents"], "recommendation": r["recommendation"],
            "alternatives": r["alternatives"], "push": push_for(r)}


@app.get("/api/v1/route-choices")
def route_choices(uid=Depends(current_user)):
    def compute():
        import transit
        now = now_kst()
        with _lock, contextlib.redirect_stdout(io.StringIO()):
            legs = transit.get_legs(now)
        return {"generated_at": now.strftime("%Y-%m-%dT%H:%M:%S+09:00"),
                "routes": [{"route": r, "alight_stop": l.get("from"), "minutes_from_seoul_first_stop": l["minutes"],
                            "choices": [{k: v for k, v in ch.items() if k != "map_obj"} for ch in l.get("choices", [])]}
                           for r, l in legs.items()]}
    return kept_fresh(("route-choices", transit_peak_key()), ROUTE_CHOICES_CACHE_SEC, compute)


def transit_peak_key():
    import transit
    return transit.is_peak(now_kst())


def _round(obj, nd=5):
    if isinstance(obj, list):
        return [_round(x, nd) for x in obj]
    return round(obj, nd) if isinstance(obj, float) else obj


@app.get("/api/v1/map")
def map_api(home_stop: str = Query(config.MY_STOP), uid=Depends(current_user)):
    check_stop_route(home_stop, config.MY_ROUTE)

    def compute():
        import map_data
        with _lock, as_user(home_stop, config.MY_ROUTE), contextlib.redirect_stdout(io.StringIO()):
            geo = map_data.build(live=True)
        for f in geo["features"]:
            f["geometry"]["coordinates"] = _round(f["geometry"]["coordinates"])
        return geo
    return kept_fresh(("map", home_stop), MAP_CACHE_SEC, compute)


class Feedback(BaseModel):
    date: str = Field(pattern=r"^\d{4}-\d{2}-\d{2}$")
    route: str
    stop_id: str
    boarded: bool
    predicted_prob: float | None = Field(default=None, ge=0.0, le=1.0)
    memo: str = ""
    uid: str | None = None


@app.post("/api/v1/feedback", status_code=201)
def feedback(body: Feedback, uid=Depends(current_user)):
    if body.route not in config.ROUTES:
        raise ApiError(400, "unknown_route", f"지원하지 않는 노선입니다: {body.route}")
    if body.stop_id not in config.STOPS:
        raise ApiError(400, "unknown_stop", f"지원하지 않는 정류장입니다: {body.stop_id}")
    fid = uuid.uuid4().hex
    row = {"id": fid, "received_at": datetime.now(KST).isoformat(timespec="seconds"),
           **body.model_dump(), "uid": uid or body.uid}   # 로그인했으면 토큰의 uid 를 씀
    os.makedirs(os.path.dirname(FEEDBACK_FILE), exist_ok=True)
    with _feedback_lock, open(FEEDBACK_FILE, "a", encoding="utf-8") as f:
        f.write(json.dumps(row, ensure_ascii=False) + "\n")
    return {"id": fid}


if not FIREBASE_PROJECT_ID:
    log.warning("FIREBASE_PROJECT_ID 가 없어 로그인 토큰을 확인하지 않습니다 (개발용).")


if __name__ == "__main__":
    # python server.py 로 실행해도 서버가 켜지도록 (uvicorn server:app --host 0.0.0.0 --port 8000 과 같음)
    import uvicorn
    port = int(os.environ.get("PORT", "8000"))
    print(f"좌석예보 예측 서버 시작 — http://localhost:{port}/health (끄려면 Ctrl+C)")
    uvicorn.run(app, host="0.0.0.0", port=port)
