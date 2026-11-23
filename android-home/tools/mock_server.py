# ==========================================================
#  A 홈 — 테스트 서버: GET /api/v1/recommendations (docs/api.md ② 형식 그대로)
#  예측 서버(FastAPI)가 완성되기 전에 앱의 '서버 연결'(HTTP · Bearer · 오류 처리)을 확인합니다.
#  파이썬 기본 라이브러리만 씁니다 (설치할 것 없음).
#
#  실행 (저장소 루트에서):
#    python android-home/tools/mock_server.py                 → http://0.0.0.0:8000
#    python android-home/tools/mock_server.py --delay 3       → 3초 늦게 응답 (로딩 · 새로고침 확인)
#    python android-home/tools/mock_server.py --fail 500      → 항상 500 오류
#    python android-home/tools/mock_server.py --reject-token demo-id-token
#                                                             → 그 토큰이면 401 (앱이 새 토큰으로 다시 요청하는지)
#    python android-home/tools/mock_server.py --fixed         → 예시 시각(07:40) 그대로
#
#  앱: 시연 앱을 demo_mode server 로 실행 (에뮬레이터는 10.0.2.2:8000 = 이 컴퓨터).
#  응답 내용은 docs/examples/recommendations.json 이고, 기본으로 generated_at · arrive_at 을
#  지금 시각에 맞춰 옮깁니다 (예시 07:40 → 지금). 버스 위치 · 확률은 예시 값 그대로입니다.
# ==========================================================
import argparse
import copy
import json
import os
import re
import sys
import time
from datetime import datetime, timedelta, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, urlparse

KST = timezone(timedelta(hours=9))
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
EXAMPLE = os.path.join(ROOT, "docs", "examples", "recommendations.json")
HHMM = re.compile(r"^\d{2}:\d{2}$")


def shift_hhmm(value, delta):
    if not isinstance(value, str) or not HHMM.match(value):
        return value
    h, m = map(int, value.split(":"))
    total = (h * 60 + m + int(delta.total_seconds() // 60)) % (24 * 60)
    return f"{total // 60:02d}:{total % 60:02d}"


def body_for(example, now, fixed, route):
    """예시 응답을 지금 시각으로 옮김. ranking 순서 · 값은 그대로."""
    data = copy.deepcopy(example)
    data["my_route"] = route
    if fixed:
        return data
    base = datetime.fromisoformat(data["generated_at"])
    now = now.replace(second=0, microsecond=0)
    delta = now - base.replace(tzinfo=KST)
    data["generated_at"] = now.isoformat()
    for o in data.get("ranking") or []:
        o["arrive_at"] = shift_hhmm(o.get("arrive_at"), delta)
        for ch in o.get("route_choices") or []:
            ch["arrive_at"] = shift_hhmm(ch.get("arrive_at"), delta)
    return data


class Handler(BaseHTTPRequestHandler):
    server_version = "SeatForecastMock/1.0"
    opts = None
    example = None

    def send_json(self, status, obj):
        raw = json.dumps(obj, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def error(self, status, code, message):
        self.send_json(status, {"error": {"code": code, "message": message}})

    def do_GET(self):
        url = urlparse(self.path)
        if url.path == "/":
            self.send_json(200, {"ok": True, "try": "/api/v1/recommendations?home_stop=203000063&route=8800"})
            return
        if url.path != "/api/v1/recommendations":
            self.error(404, "not_found", f"{url.path} 은(는) 없는 주소입니다.")
            return

        opts = self.opts
        if opts.delay:
            time.sleep(opts.delay)

        auth = self.headers.get("Authorization", "")
        if not auth.startswith("Bearer ") or not auth[7:].strip():
            self.error(401, "unauthorized", "로그인이 필요합니다.")
            return
        token = auth[7:].strip()
        if opts.reject_token and token in opts.reject_token:
            self.error(401, "token_expired", "로그인이 만료되었습니다.")
            return

        q = parse_qs(url.query)
        home_stop = (q.get("home_stop") or [""])[0]
        route = (q.get("route") or [""])[0]
        if not home_stop or not route:
            self.error(400, "bad_request", "home_stop 과 route 를 함께 보내 주세요.")
            return

        if opts.fail:
            self.error(opts.fail, "mock_failure", f"테스트 서버가 일부러 {opts.fail} 을(를) 돌려줍니다.")
            return

        self.send_json(200, body_for(self.example, datetime.now(KST), opts.fixed, route))

    def log_message(self, fmt, *args):
        auth = self.headers.get("Authorization", "") if hasattr(self, "headers") else ""
        # 로그는 영문만 (Windows 콘솔 인코딩이 달라도 깨지지 않게) — 토큰은 앞 8글자만
        who = "Bearer " + (auth[7:15] + "..." if len(auth) > 15 else auth[7:]) if auth else "no token"
        sys.stderr.write(f"{datetime.now():%H:%M:%S} {self.address_string()} {fmt % args} [{who}]\n")


def main():
    p = argparse.ArgumentParser(description="A 홈 테스트 서버 (GET /api/v1/recommendations)")
    p.add_argument("--host", default="0.0.0.0")
    p.add_argument("--port", type=int, default=8000)
    p.add_argument("--file", default=EXAMPLE, help="응답으로 쓸 JSON (기본: docs/examples/recommendations.json)")
    p.add_argument("--delay", type=float, default=0, help="응답 전에 기다릴 초")
    p.add_argument("--fail", type=int, default=0, help="이 HTTP 상태 코드로 항상 실패 (예: 500, 503)")
    p.add_argument("--reject-token", action="append", default=[], help="이 토큰이면 401 (여러 번 줄 수 있음)")
    p.add_argument("--fixed", action="store_true", help="시각을 옮기지 않고 예시 그대로")
    opts = p.parse_args()

    with open(opts.file, encoding="utf-8") as f:
        Handler.example = json.load(f)
    Handler.opts = opts

    srv = ThreadingHTTPServer((opts.host, opts.port), Handler)
    print(f"테스트 서버: http://{opts.host}:{opts.port}/api/v1/recommendations  (응답: {os.path.relpath(opts.file, ROOT)})")
    print("에뮬레이터 → http://10.0.2.2:%d/  ·  같은 와이파이의 휴대폰 → http://<이 컴퓨터 IP>:%d/" % (opts.port, opts.port))
    try:
        srv.serve_forever()
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
