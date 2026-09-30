# ==========================================================
#  정류장 · 노선 추천 — "어디서 무엇을 타야 국민대에 가장 빨리 도착하나"
#  실행:  python recommend.py              (지금 집에서 출발)
#         python recommend.py 07:30        (07:30에 집에서 출발)
#         python recommend.py 07:30 0      (월요일 07:30 — 0=월 ... 4=금)
#  먼저 train.py 로 board_model.pkl 을 만들어야 합니다.
# ==========================================================
import sys
from datetime import datetime, timedelta

import joblib
import pandas as pd

from config import ROUTES, STOPS, DEFAULT_HEADWAY_MIN, DEFAULT_RIDE_MIN


MAX_MISSED = 4   # 만석으로 놓치는 버스는 최대 4대까지만 계산 (그 뒤엔 보통 혼잡이 풀림)


def expected_wait(headway, p):
    """평균 대기(배차의 절반) + 만석으로 놓쳐서 더 기다리는 시간.
    놓칠 때마다 한 배차씩 더 기다린다고 보면, 놓치는 횟수의 기댓값은 (1-p)/p."""
    missed = min((1 - p) / max(p, 0.01), MAX_MISSED)
    return headway / 2 + headway * missed


def main():
    now = datetime.now()
    leave = now
    if len(sys.argv) > 1:
        hh, mm = map(int, sys.argv[1].split(":"))
        leave = now.replace(hour=hh, minute=mm, second=0, microsecond=0)
    weekday = int(sys.argv[2]) if len(sys.argv) > 2 else leave.weekday()

    b = joblib.load("board_model.pkl")
    # 환승 경로(ODsay)·도보 시간(TMAP) — 하루 한 번 조회, 실패하면 config 값
    import transit
    legs, walks = transit.get_legs(), transit.get_walks()
    options = []
    for route, cfg in ROUTES.items():
        for stop_id in cfg["stops"]:
            stop = dict(STOPS[stop_id], walk_min=walks[stop_id])
            at_stop = leave + timedelta(minutes=stop["walk_min"])
            headway = b["headway"].get(route, DEFAULT_HEADWAY_MIN)
            x = pd.DataFrame([{
                "route_code": b["route_map"][route], "stop_code": b["stop_map"][stop_id],
                "weekday": weekday, "time_min": at_stop.hour * 60 + at_stop.minute,
                "headway_min": headway,
            }])[b["features"]]
            p = b["model"].predict_proba(x)[0, 1]
            wait = expected_wait(headway, p)
            ride = b["ride"].get((route, stop_id), DEFAULT_RIDE_MIN)
            transfer = legs[route]["minutes"]
            total = stop["walk_min"] + wait + ride + transfer
            # 비교용: 지도 앱처럼 '모든 버스를 탈 수 있다'고 가정한 시간
            naive = stop["walk_min"] + headway / 2 + ride + transfer
            options.append((total, naive, p, wait, route, stop, dict(cfg, transfer=legs[route]["transfer"])))

    options.sort(key=lambda o: o[0])
    day = "월화수목금토일"[weekday]
    print(f"\n[{day}요일 {leave:%H:%M} 집에서 출발 → 국민대]  기대 도착 시각이 빠른 순\n")
    print(f"{'순위':>2}  {'노선':>5}  {'정류장':<10} {'도보':>4} {'탑승확률':>6} {'기대대기':>6}  {'도착 예상':>6}  (지도 앱 기준)")
    for i, (total, naive, p, wait, route, stop, cfg) in enumerate(options, 1):
        eta, naive_eta = leave + timedelta(minutes=total), leave + timedelta(minutes=naive)
        print(f"{i:>3}.  {route:>5}  {stop['name']:<10} {stop['walk_min']:>3}분 {p:>7.0%} {wait:>6.0f}분  "
              f"{eta:%H:%M}  ({naive_eta:%H:%M}, {cfg['dest']} 경유)")

    best = options[0]
    print(f"\n→ 추천: {best[6]['dest']}행 {best[4]}번을 {best[5]['name']} 정류장에서 타세요 "
          f"(도보 {best[5]['walk_min']}분, 탑승 확률 {best[2]:.0%})")
    print(f"  서울 도착 후: {best[6]['transfer']}")


if __name__ == "__main__":
    main()
