# ==========================================================
#  실시간 대안 안내 — "기다릴까, 걸어갈까, 다른 버스를 탈까?"
#  실행:  python live_recommend.py          (평소 노선 = config.MY_ROUTE)
#         python live_recommend.py 3007     (평소 노선을 3007로)
#
#  지금 이 순간의 버스 위치·잔여 좌석(실시간 API)으로 세 가지 선택지를 비교합니다.
#   ① 기다리기   : 평소 정류장에서 평소 노선을 기다린다 (첫 차가 만석이면 다음 차)
#   ② 걸어가기   : 전 정류장으로 걸어가서 평소 노선을 먼저 탄다
#   ③ 다른 버스  : 다른 노선을 타고 다른 환승 경로로 국민대에 간다
#  train.py 로 만든 board_model.pkl 이 있으면 실측값을, 없으면 config 기본값을 씁니다.
# ==========================================================
import math
import os
import sys
from datetime import datetime, timedelta

import joblib
import requests

from config import (SERVICE_KEY, ROUTES, STOPS, MY_STOP, MY_ROUTE, DEFAULT_HEADWAY_MIN,
                    DEFAULT_RIDE_MIN, DEFAULT_DROP_PER_STOP, DEFAULT_MIN_PER_STOP)

BASE = "https://apis.data.go.kr/6410000"
LOOKAHEAD = 3        # 노선·정류장마다 다가오는 버스 몇 대까지 볼지
BUFFER_MIN = 1       # 정류장에 버스보다 최소 1분 먼저 도착해야 탈 수 있다고 봄
USE_MODEL = True     # False면 board_model.pkl 없이 config 기본값만 사용 (시연용)
USE_TRANSIT = True   # False면 ODsay·TMAP 조회 없이 config 의 환승·도보 값 사용 (시연용)


def get(path, **params):
    params.update(serviceKey=SERVICE_KEY, format="json")
    try:
        body = requests.get(f"{BASE}/{path}", params=params, timeout=10).json()["response"]["msgBody"]
    except Exception:
        return []
    items = next(iter((body or {}).values()), []) if body else []
    return items if isinstance(items, list) else [items]


def num(v, default=None):
    try:
        return float(v)
    except (TypeError, ValueError):
        return default


def board_prob(seats_at_stop):
    """정류장 도착 때 예상 잔여 좌석 → 탑승 확률 (좌석 1석이 경계, 부드럽게 변환).
    예상 3석이면 약 80%, 0석이면 약 35%, -2석이면 약 12%."""
    return 1 / (1 + math.exp(-(seats_at_stop - 1) / 1.5))


def main():
    my_route = sys.argv[1] if len(sys.argv) > 1 else MY_ROUTE
    now = datetime.now()
    b = joblib.load("board_model.pkl") if USE_MODEL and os.path.exists("board_model.pkl") else {}

    # 환승 경로(ODsay)와 도보 시간(TMAP) — 하루 한 번 조회해 저장, 실패하면 config 값
    if USE_TRANSIT:
        import transit
        legs, walks = transit.get_legs(), transit.get_walks()
    else:
        legs = {r: {"minutes": c["to_kookmin_min"], "transfer": c["transfer"]} for r, c in ROUTES.items()}
        walks = {s: abs(v["walk_min"] - STOPS[MY_STOP]["walk_min"]) for s, v in STOPS.items()}

    # 1) 노선마다 달리고 있는 모든 차량 (위치 · 현재 잔여 좌석)
    buses = {r: get("buslocationservice/v2/getBusLocationListv2", routeId=c["route_id"])
             for r, c in ROUTES.items()}
    # 2) 후보 정류장마다 첫 차의 정확한 도착 예정 시간 (차량번호 → 분)
    eta_exact = {}
    for stop_id in STOPS:
        for a in get("busarrivalservice/v2/getBusArrivalListv2", stationId=stop_id):
            for n in ("1", "2"):
                if a.get(f"plateNo{n}") and num(a.get(f"predictTime{n}")) is not None:
                    eta_exact[(stop_id, a[f"plateNo{n}"])] = num(a[f"predictTime{n}"])

    options = []
    for route, cfg in ROUTES.items():
        headway = b.get("headway", {}).get(route, DEFAULT_HEADWAY_MIN)
        per_stop = b.get("min_per_stop", {}).get(route, DEFAULT_MIN_PER_STOP)
        drop = b.get("drop", {}).get((route, now.hour), DEFAULT_DROP_PER_STOP)
        for stop_id, stop_seq in cfg["stops"].items():
            walk = walks[stop_id]   # 평소 정류장에서 걸어가는 시간
            coming = sorted((stop_seq - int(x["stationSeq"]), x) for x in buses[route]
                            if int(x.get("stationSeq", 999)) < stop_seq)[:LOOKAHEAD]
            cands = []
            for n_stops, x in coming:
                eta = eta_exact.get((stop_id, x.get("plateNo")), n_stops * per_stop)
                seats_now = num(x.get("remainSeatCnt"), -1)
                seats_at_stop = seats_now - drop * n_stops if seats_now >= 0 else None
                p = board_prob(seats_at_stop) if seats_at_stop is not None else 0.5
                cands.append({"eta": eta, "seats": seats_at_stop, "p": p, "ok": eta >= walk + BUFFER_MIN})
            reachable = [c for c in cands if c["ok"]]

            # 기대 탑승 시각: 첫 차부터 차례로 시도, 모두 놓치면 배차간격마다 한 대씩 더 기다림
            expected, miss = 0.0, 1.0
            for c in reachable:
                expected += miss * c["p"] * c["eta"]
                miss *= 1 - c["p"]
            # 실시간으로 안 보이는 뒤차: 앞차들이 만석이면 뒤차도 만석일 가능성이 높으므로
            # 보이는 차들의 평균 탑승 확률을 적용 (놓치는 차는 최대 4대까지만 계산)
            last = reachable[-1]["eta"] if reachable else walk + headway / 2
            p_later = max(sum(c["p"] for c in reachable) / len(reachable), 0.2) if reachable else 0.5
            expected += miss * (last + headway * min(1 / p_later, 4))
            # 서울까지 이동시간: 실측값이 없으면, 앞 정류장에서 탈수록 더 오래 타는 만큼 더한다
            my_seq = cfg["stops"].get(MY_STOP, stop_seq)
            ride = b.get("ride", {}).get((route, stop_id), DEFAULT_RIDE_MIN + (my_seq - stop_seq) * per_stop)
            total = expected + ride + legs[route]["minutes"]

            kind = ("기다리기" if stop_id == MY_STOP else "걸어가기") if route == my_route else "다른 버스"
            options.append({"kind": kind, "route": route, "stop": STOPS[stop_id], "walk": walk,
                            "cands": cands, "board": expected, "total": total, "cfg": cfg,
                            "transfer": legs[route]["transfer"]})

    # 3) 선택지별 최선 + 전체 추천
    print(f"\n[{now:%H:%M} 기준 · 평소 {STOPS[MY_STOP]['name']}에서 {my_route}번]\n")
    best_all = min(options, key=lambda o: o["total"]) if options else None
    for kind, icon in (("기다리기", "①"), ("걸어가기", "②"), ("다른 버스", "③")):
        group = sorted((o for o in options if o["kind"] == kind), key=lambda o: o["total"])
        if not group:
            print(f"{icon} {kind}: 해당 없음\n")
            continue
        o = group[0]
        eta = now + timedelta(minutes=o["total"])
        mark = "  ★ 추천" if o is best_all else ""
        where = f"{o['stop']['name']}" + (f" (도보 {o['walk']}분)" if o["walk"] else "")
        print(f"{icon} {kind}: {o['route']}번 · {where} → {o['cfg']['dest']} → 국민대 {eta:%H:%M} 도착 예상{mark}")
        for i, c in enumerate(o["cands"], 1):
            seats = f"도착 때 약 {max(c['seats'], 0):.0f}석" if c["seats"] is not None else "좌석 정보 없음"
            note = "" if c["ok"] else " (걸어가는 동안 지나감)"
            print(f"     {i}번째 차: {c['eta']:.0f}분 후 · {seats} · 탑승 확률 {c['p']:.0%}{note}")
        if not o["cands"]:
            print("     다가오는 차량 없음 — 배차간격 기준으로 추정")
        print(f"     환승: {o['transfer']}\n")

    if best_all:
        o = best_all
        first = next((c for c in o["cands"] if c["ok"]), None)
        tip = ""
        if o["kind"] == "기다리기" and first and first["p"] < 0.5:
            tip = " — 첫 차는 만석 위험이 커서 보내고 다음 차를 타는 게 낫습니다"
        elif o["kind"] == "걸어가기":
            tip = f" — 지금 {o['stop']['name']}(으)로 출발하면 좌석이 남은 차를 먼저 탈 수 있습니다"
        elif o["kind"] == "다른 버스":
            tip = f" — {o['cfg']['dest']}에서 환승하는 경로가 더 빠릅니다"
        print(f"→ 추천: {o['kind']} ({o['route']}번 · {o['stop']['name']}){tip}")


if __name__ == "__main__":
    main()
