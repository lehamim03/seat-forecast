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
INCIDENT_RECENT = None  # 시연용: 돌발 감지에 쓸 최근 기록(DataFrame)을 직접 넣을 때
INCIDENT_EVENTS = None  # 시연용: ITS 돌발상황 목록을 직접 넣을 때
NOW_OVERRIDE = None     # 시연용: 특정 날짜·시각(datetime)으로 판단할 때
LOCAL_EXTRA = None      # 시연용: 지역행사 목록을 직접 넣을 때 (config.LOCAL_EVENTS 형식)


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
    now = NOW_OVERRIDE or datetime.now()
    b = joblib.load("board_model.pkl") if USE_MODEL and os.path.exists("board_model.pkl") else {}

    # 요일 · 공휴일 · 출퇴근 시간대 — 평일 출퇴근 대비 좌석 감소 속도, 배차간격 배율
    import daytype
    day = daytype.factors(now)
    # 지역행사 — 행사장 근처 정류장의 승객 쏠림, 주변 정체
    import events
    ev = events.impact(now, LOCAL_EXTRA)

    # 환승 경로(ODsay)와 도보 시간(TMAP) — 하루 한 번 조회해 저장, 실패하면 config 값
    if USE_TRANSIT:
        import transit
        legs, walks = transit.get_legs(), transit.get_walks()
    else:
        import transit
        legs = {r: transit.fallback_leg(r) for r in ROUTES}
        walks = {s: abs(v["walk_min"] - STOPS[MY_STOP]["walk_min"]) for s, v in STOPS.items()}

    # 돌발상황 — 앞서 가는 같은 노선 버스들의 최근 움직임 (collect.py 기록 사용)
    import incidents
    inc = incidents.detect(now, b, INCIDENT_RECENT, INCIDENT_EVENTS)

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
        headway = b.get("headway", {}).get(route, DEFAULT_HEADWAY_MIN) * day["headway"]
        per_stop = b.get("min_per_stop", {}).get(route, DEFAULT_MIN_PER_STOP)
        # 정류장당 좌석 감소: 기본값 × 요일·시간대 배율 × 지금 감지된 승객 쏠림
        drop = (b.get("drop", {}).get((route, now.hour), DEFAULT_DROP_PER_STOP)
                * day["drop"] * inc[route].get("drop_factor", 1.0))
        for stop_id, stop_seq in cfg["stops"].items():
            walk = walks[stop_id]   # 평소 정류장에서 걸어가는 시간
            coming = sorted((stop_seq - int(x["stationSeq"]), x) for x in buses[route]
                            if int(x.get("stationSeq", 999)) < stop_seq)[:LOOKAHEAD]
            cands = []
            for n_stops, x in coming:
                eta = eta_exact.get((stop_id, x.get("plateNo")), n_stops * per_stop)
                seats_now = num(x.get("remainSeatCnt"), -1)
                bus_seq = int(x["stationSeq"])
                crowd = sum(n for seq, n in ev[route]["extra"].items() if bus_seq <= seq <= stop_seq)   # 행사 승객
                seats_at_stop = seats_now - drop * n_stops - crowd if seats_now >= 0 else None
                p = board_prob(seats_at_stop) if seats_at_stop is not None else 0.5
                cands.append({"eta": eta, "seats": seats_at_stop, "p": p, "ok": eta >= walk + BUFFER_MIN})
            # 우회로 이 정류장을 건너뛰는 중이면 여기서는 탈 수 없음
            skipped = stop_id in inc[route]["skip_stops"]
            if skipped:
                for c in cands:
                    c["p"], c["skip"] = 0.0, True
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
            delay = inc[route]["delay"] + ev[route]["delay"]   # 돌발 지연(사고·정체·시위) + 행사 주변 정체
            total = expected + ride + delay + legs[route]["minutes"]
            if skipped:
                total += 999                        # 사실상 선택 불가

            kind = ("기다리기" if stop_id == MY_STOP else "걸어가기") if route == my_route else "다른 버스"
            options.append({"kind": kind, "route": route, "stop": STOPS[stop_id], "walk": walk,
                            "cands": cands, "board": expected, "total": total,
                            "cfg": dict(cfg, dest=legs[route].get("from", "서울")),
                            "transfer": legs[route]["transfer"], "alts": legs[route].get("alts", []),
                            "delay": delay, "skipped": skipped})

    # 3) 선택지별 최선 + 전체 추천
    wd = "월화수목금토일"[now.weekday()]
    print(f"\n[{now:%m/%d}({wd}) {now:%H:%M} 기준 · {day['label']} · 평소 {STOPS[MY_STOP]['name']}에서 {my_route}번]")
    if day["key"] != "평일 출퇴근":
        print(f"  ※ 평일 출퇴근 시간대보다 한산해 좌석이 천천히 줄고(×{day['drop']}), 배차간격은 길어요(×{day['headway']})")
    print()
    ev_notes = [(r, v) for r, v in ev.items() if v["notes"]]
    if ev_notes:
        print("[지역행사]")
        for r, v in ev_notes:
            for n in v["notes"]:
                print(f"  🎪 {r}번: {n}")
        print()
    alerts = [(r, v) for r, v in inc.items() if v["delay"] or v["skip_stops"] or v.get("drop_factor", 1) > 1]
    if alerts:
        print("[돌발상황 감지]")
        for r, v in alerts:
            head = f"+{v['delay']:.0f}분 지연 예상" if v["delay"] else (
                "정류장 건너뜀" if v["skip_stops"] else f"좌석이 평소의 {v['drop_factor']:.1f}배 속도로 줄어듦")
            print(f"  ⚠ {r}번 {head}")
            for n in dict.fromkeys(v["notes"]):   # 같은 문구 중복 제거
                print(f"      - {n}")
        print()
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
        if o["skipped"]:
            print(f"{icon} {kind}: {o['route']}번 · {where} — 우회로 이 정류장을 건너뛰는 중이라 탈 수 없음\n")
            continue
        warn = f"  (돌발 지연 +{o['delay']:.0f}분 반영)" if o["delay"] else ""
        print(f"{icon} {kind}: {o['route']}번 · {where} → {o['cfg']['dest']} → 국민대 {eta:%H:%M} 도착 예상{mark}{warn}")
        for i, c in enumerate(o["cands"], 1):
            seats = f"도착 때 약 {max(c['seats'], 0):.0f}석" if c["seats"] is not None else "좌석 정보 없음"
            note = "" if c["ok"] else " (걸어가는 동안 지나감)"
            print(f"     {i}번째 차: {c['eta']:.0f}분 후 · {seats} · 탑승 확률 {c['p']:.0%}{note}")
        if not o["cands"]:
            print("     다가오는 차량 없음 — 배차간격 기준으로 추정")
        print(f"     환승: {o['transfer']}")
        for alt in o["alts"][:1]:
            print(f"     다른 환승: {alt['transfer']} ({alt['minutes']}분 · 환승 {alt['transfers']}회)")
        print()

    if best_all:
        o = best_all
        first = next((c for c in o["cands"] if c["ok"]), None)
        tip = ""
        if o["kind"] == "기다리기" and first and first["p"] < 0.5:
            tip = " — 첫 차는 만석 위험이 커서 보내고 다음 차를 타는 게 낫습니다"
        elif o["kind"] == "기다리기" and first and day["key"] != "평일 출퇴근":
            tip = f" — {day['label']}이라 한산해서 첫 차({first['eta']:.0f}분 후)도 탈 수 있어요"
        elif o["kind"] == "걸어가기":
            tip = f" — 지금 {o['stop']['name']}(으)로 출발하면 좌석이 남은 차를 먼저 탈 수 있습니다"
        elif o["kind"] == "다른 버스":
            mine = inc[my_route]
            if ev[my_route]["extra"] and not ev[o["route"]]["extra"]:
                tip = f" — {my_route}번은 지역행사로 앞 정류장에서 승객이 몰려, 행사장을 거치지 않는 {o['route']}번이 낫습니다"
            elif mine["delay"] or mine["skip_stops"]:
                tip = f" — {my_route}번 경로에 돌발상황이 있어 {o['cfg']['dest']} 경유로 돌아가는 게 빠릅니다"
            else:
                tip = f" — {o['cfg']['dest']}에서 환승하는 경로가 더 빠릅니다"
        print(f"→ 추천: {o['kind']} ({o['route']}번 · {o['stop']['name']}){tip}")


if __name__ == "__main__":
    main()
