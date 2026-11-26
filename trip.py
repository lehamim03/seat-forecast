# ==========================================================
#  출발지 · 도착지 모드 — 지도 앱처럼 아무 출발지(현재 GPS 등) → 아무 도착지 경로를 탑승 확률로 계산
#  실행:  python trip.py 37.3850 127.1233 37.6110 126.9952      (출발 위도 경도 → 도착 위도 경도)
#
#  1) ODsay 대중교통 길찾기로 출발지 → 도착지 경로 후보 (첫 탈것 · 탈 정류장 · 노선 포함)
#  2) 첫 탈것이 버스면 그 정류장 도착정보 API 로 다가오는 차 · 남은 정류장 · 지금 좌석
#  3) 좌석제(직행좌석 · 좌석 · 광역급행) 버스만 만석이면 못 타므로 탑승 확률 계산 — 학습 데이터가 없는 곳이라
#     노선 번호 없이 학습한 공통 모델(train.py generic_model, 처음 보는 곳 검증은 generalize.py), 없으면 기존 공식
#     시내버스 · 지하철은 서서 탈 수 있어 확률 1.0
#  4) 기대 탑승 시각(첫 차부터 차례로 시도) + 나머지 경로 시간 → 도착 예상 순위
#  응답은 docs/api.md 의 기존 형식(ranking · alternatives · option)을 그대로 쓰고 origin · destination 등을 더함.
# ==========================================================
import json
import math
import os
import re
import sys
import time
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timedelta

import requests

import api_key
from config import (CITY_BUS_PEAK_FACTOR, DEFAULT_DROP_PER_STOP, TRANSFER_PENALTY_MIN, WAIT_WEIGHT,
                    WALK_WEIGHT)

SEAT_TYPES = {"11", "12", "14"}   # 직행좌석형 · 좌석형 · 광역급행(M버스) — 입석 금지라 만석이면 못 탐
BUFFER_MIN = 1                    # 정류장에 버스보다 1분 먼저 도착해야 탈 수 있다고 봄
MAX_PATHS = 6                     # ODsay 경로 후보 중 볼 개수
MAX_LANES = 3                     # 같은 구간을 가는 버스가 여러 개면 몇 개까지 선택지로
ODSAY_CACHE = "trip_odsay_cache.json"
ODSAY_DAYS = 14                   # 시간표 기반이라 2주는 그대로 씀 (ODsay 하루 한도 아끼기)
NEAR_SAVED_KM = 1.5               # ODsay 가 안 될 때(한도 · IP) 이 거리 안에 저장된 출발지 경로를 대신 씀
ARRIVAL_SEC = 30                  # 같은 정류장 도착정보는 이 시간 동안 다시 씀
# 좌석제 버스인데 좌석 수를 모를 때(서울 등록 노선 · 다가오는 차 없음) 쓰는 평소 탑승률 — 우리 수집 데이터 실측
# (train.py: 평일 출퇴근 92.7%, 전체 97.9%). 이때 앱에는 확률 대신 "좌석 정보 없음" (seat_known=false)
PRIOR_PEAK, PRIOR_OTHER = 0.93, 0.98
_arrivals, _model = {}, {}


class RouteUnavailable(Exception):
    """ODsay 경로 조회 실패 (IP 미등록 · 한도 · 경로 없음)"""


# ---------- 1) 경로 후보 (ODsay) ----------
def _key(sx, sy, ex, ey):
    # 같은 곳에서 조금씩 움직여도 다시 조회하지 않도록 약 100m 단위로 묶음
    return f"{sx:.3f},{sy:.3f}->{ex:.3f},{ey:.3f}"


def odsay_paths(sx, sy, ex, ey):
    """출발 (경도 sx, 위도 sy) → 도착. ODsay searchPubTransPathT 원본 경로 목록 (며칠 저장)"""
    key = _key(sx, sy, ex, ey)
    cache = {}
    if os.path.exists(ODSAY_CACHE):
        with open(ODSAY_CACHE, encoding="utf-8") as f:
            cache = json.load(f)
    hit = cache.get(key)
    if hit and time.time() - hit["at"] < ODSAY_DAYS * 86400:
        return hit["paths"]
    try:
        data = requests.get("https://api.odsay.com/v1/api/searchPubTransPathT", timeout=15, params={
            "SX": round(sx, 3), "SY": round(sy, 3), "EX": round(ex, 3), "EY": round(ey, 3),
            "apiKey": api_key.ODSAY_KEY}).json()
    except Exception as e:
        if hit:
            return hit["paths"]
        raise RouteUnavailable(f"경로 조회 실패: {e}")
    if "error" in data:
        if hit:
            return hit["paths"]
        err = data["error"]
        err = err[0] if isinstance(err, list) else err
        raise RouteUnavailable(str(err.get("message") or err.get("msg") or err)[:150])
    paths = data.get("result", {}).get("path") or []
    cache[key] = {"at": time.time(), "paths": paths}
    with open(ODSAY_CACHE, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False)
    return paths


def saved_nearby(sx, sy, ex, ey, max_km=NEAR_SAVED_KM):
    """ODsay 를 못 쓸 때: 같은 도착지(약 100m 안)로 저장된 경로 중 출발지가 가장 가까운 것 → (paths, 거리 km) 또는 None"""
    if not os.path.exists(ODSAY_CACHE):
        return None
    with open(ODSAY_CACHE, encoding="utf-8") as f:
        cache = json.load(f)
    best = None
    for key, hit in cache.items():
        try:
            a, b = key.split("->")
            ax, ay = map(float, a.split(","))
            bx, by = map(float, b.split(","))
        except ValueError:
            continue
        if math.hypot((bx - ex) * 88.8, (by - ey) * 111.0) > 0.15 or not hit.get("paths"):
            continue
        d = math.hypot((ax - sx) * 88.8, (ay - sy) * 111.0)
        if d <= max_km and (best is None or d < best[1]):
            best = (hit["paths"], d)
    return best


def bus_no(lane):
    return re.sub(r"\(.*?\)", "", str(lane.get("busNo", "")))


def describe(path):
    """'버스 M4102(서현역→숭례문) → 4호선(…) → 도보 5분' 한 줄"""
    steps = []
    for sp in path["subPath"]:
        if sp["trafficType"] == 1:
            steps.append(f"{sp['lane'][0]['name'].replace('수도권 ', '')}({sp['startName']}→{sp['endName']})")
        elif sp["trafficType"] == 2:
            steps.append(f"버스 {'/'.join(bus_no(l) for l in sp['lane'][:3])}({sp['startName']}→{sp['endName']})")
        elif sp.get("sectionTime", 0) >= 3:
            steps.append(f"도보 {sp['sectionTime']}분")
    return " → ".join(steps)


def special(path):
    """심야(N) · 예약버스가 들어간 경로는 출퇴근 시간에 탈 수 없어 뺌"""
    return any(sp["trafficType"] == 2 and any(bus_no(l).startswith("N") or "예약" in bus_no(l) for l in sp["lane"])
               for sp in path["subPath"])


def peak_now(now):
    try:
        import daytype
        return daytype.day_info(now)["peak"]
    except Exception:
        return now.weekday() < 5 and (390 <= now.hour * 60 + now.minute < 570 or 1020 <= now.hour * 60 + now.minute < 1200)


def path_shape(path):
    """경로 대략 모양 (지나는 정류장 좌표) — 돌발 위치 비교 · 지도용"""
    pts = []
    for sp in path["subPath"]:
        if sp["trafficType"] in (1, 2):
            for st in (sp.get("passStopList") or {}).get("stations", []):
                try:
                    pts.append((float(st["x"]), float(st["y"])))
                except (KeyError, ValueError, TypeError):
                    pass
            if not pts and sp.get("startX"):
                pts += [(float(sp["startX"]), float(sp["startY"])), (float(sp["endX"]), float(sp["endY"]))]
    return pts


# ---------- 2) 다가오는 버스 (정류장 도착정보) ----------
def arrivals(station_id):
    """{노선 이름: {"route_id", "type", "stop_seq", "buses": [...]}} — 30초 저장"""
    hit = _arrivals.get(station_id)
    if hit and time.monotonic() - hit[0] < ARRIVAL_SEC:
        return hit[1]
    import anywhere
    out = {}
    for a in anywhere.get("busarrivalservice/v2/getBusArrivalListv2", stationId=station_id):
        buses = []
        for n in ("1", "2"):
            eta = anywhere.num(a.get(f"predictTime{n}"))
            if eta is None:
                continue
            seats = anywhere.num(a.get(f"remainSeatCnt{n}"), -1)
            buses.append({"eta": eta, "n_stops": int(anywhere.num(a.get(f"locationNo{n}"), 0)),
                          "seats_now": None if seats < 0 else seats})
        out[str(a.get("routeName"))] = {"route_id": str(a.get("routeId")), "type": str(a.get("routeTypeCd")),
                                        "stop_seq": int(anywhere.num(a.get("staOrder"), 0)), "buses": buses}
    _arrivals[station_id] = (time.monotonic(), out)
    return out


def local_station(sp):
    """ODsay 구간의 출발 정류장 → 경기도 정류장 ID (ODsay 가 주면 그대로, 없으면 좌표 · 이름이 가장 가까운 곳)"""
    sid = str(sp.get("startLocalStationID") or "")
    if sid.isdigit() and len(sid) >= 8:
        return sid
    try:
        import anywhere
        near = anywhere.nearby(float(sp["startY"]), float(sp["startX"]), 120)
    except Exception:
        return None
    name = str(sp.get("startName", ""))
    same = [s for _, s in near if s["name"].split(".")[0] in name or name in s["name"]]
    pick = same or [s for _, s in near]
    return pick[0]["id"] if pick else None


# ---------- 3) 탑승 확률 ----------
def bundle():
    if "b" not in _model:
        import joblib
        _model["b"] = joblib.load("board_model.pkl") if os.path.exists("board_model.pkl") else {}
    return _model["b"]


def board_probs(buses, stop_seq, now, day_type, prior):
    """좌석제 버스의 차량별 탑승 확률 — 공통 모델 한 번에 계산 (없으면 기존 공식), 좌석 모르는 차는 prior"""
    b = bundle()
    known = [x for x in buses if x["seats_now"] is not None]
    if "generic_model" in b and known:
        import pandas as pd
        X = pd.DataFrame([{"n_stops": x["n_stops"], "seats_now": x["seats_now"],
                           "time_min": now.hour * 60 + now.minute, "day_type": day_type,
                           "stop_seq": stop_seq} for x in known])[b["generic_features"]]
        probs = iter(b["generic_model"].predict_proba(X)[:, 1])
    else:
        from live_recommend import board_prob
        probs = iter(board_prob(x["seats_now"] - DEFAULT_DROP_PER_STOP * x["n_stops"]) for x in known)
    return [next(probs) if x["seats_now"] is not None else prior for x in buses]


def expected_board(cands, walk, headway):
    """기대 탑승 시각(분) — live_recommend.py 와 같은 계산: 첫 차부터 차례로, 다 놓치면 배차간격마다"""
    reach = [c for c in cands if c["ok"]]
    expected, miss = 0.0, 1.0
    for c in reach:
        expected += miss * c["p"] * c["eta"]
        miss *= 1 - c["p"]
    last = reach[-1]["eta"] if reach else walk + headway / 2
    p_later = max(sum(c["p"] for c in reach) / len(reach), 0.2) if reach else 1.0
    return expected + miss * (last + headway * min(1 / p_later, 4))


# ---------- 4) 선택지 · 순위 ----------
def prefetch(paths):
    """경로마다 첫 버스 정류장의 도착정보를 한꺼번에 동시에 조회 (하나씩 부르면 정류장 수 × 1~4초)"""
    firsts = [next((sp for sp in p["subPath"] if sp["trafficType"] in (1, 2)), None) for p in paths]
    sids = {local_station(sp) for sp in firsts if sp and sp["trafficType"] == 2} - {None}
    with ThreadPoolExecutor(max_workers=8) as pool:
        list(pool.map(arrivals, sids))


def build_options(paths, now, day, origin=None):
    """origin=(위도, 경도) 를 주면 (근처 저장 경로를 쓸 때) 첫 탈 곳까지 걷는 시간을 실제 출발지 기준으로 다시 계산"""
    peak = peak_now(now)
    opts = []
    paths = [p for p in paths if not special(p)][:MAX_PATHS]
    prefetch(paths)
    for path in paths:
        subs = path["subPath"]
        legs = [sp for sp in subs if sp["trafficType"] in (1, 2)]
        if not legs:
            continue
        first = legs[0]
        i_first = subs.index(first)
        walk = round(sum(sp.get("sectionTime", 0) for sp in subs[:i_first] if sp["trafficType"] == 3))
        odsay_walk = walk
        if origin and first.get("startX"):
            # 직선 거리 × 1.3 (실제 길) ÷ 분당 75m
            m = math.hypot((float(first["startX"]) - origin[1]) * 88_400, (float(first["startY"]) - origin[0]) * 111_000)
            walk = round(m * 1.3 / 75)
        # 첫 탈것을 탄 뒤의 시간: ODsay 전체 시간 − 첫 걷기 + 갈아탈 때 기다림 + 출퇴근 시내버스 정체
        later_wait = sum((sp.get("intervalTime") or 10) / 2 for sp in legs[1:])
        citybus = sum(sp.get("sectionTime", 0) for sp in legs if sp["trafficType"] == 2)
        after = (path["info"]["totalTime"] - odsay_walk + later_wait
                 + ((CITY_BUS_PEAK_FACTOR - 1) * citybus if peak else 0))
        transfers = len(legs) - 1
        common = {"walk_min": walk, "transfers": transfers, "summary": describe(path),
                  "alight_stop": first.get("endName"), "map_obj": path["info"].get("mapObj"),
                  "shape": path_shape(path), "walk_total": path["info"].get("totalWalk")}
        headway = first.get("intervalTime") or 10
        if first["trafficType"] == 1:   # 지하철 — 만석이 없음
            board = walk + headway / 2
            opts.append(dict(common, kind="지하철", first_mode="subway", route=first["lane"][0]["name"].replace("수도권 ", ""),
                             stop={"id": str(first.get("startID", "")), "name": first.get("startName"), "mobile_no": "",
                                   "lat": float(first.get("startY", 0)), "lng": float(first.get("startX", 0))},
                             board_prob=1.0, seat_limited=False, buses=[], board=board, total=board + after))
            continue
        sid = local_station(first)
        arr = arrivals(sid) if sid else {}
        for lane in first["lane"][:MAX_LANES]:
            name = bus_no(lane)
            info = arr.get(name) or next((v for k, v in arr.items() if re.sub(r"\(.*?\)", "", k) == name), None)
            seat_limited = bool(info) and info["type"] in SEAT_TYPES
            buses = (info or {}).get("buses", [])
            prior = PRIOR_PEAK if peak else PRIOR_OTHER
            seat_known = seat_limited and any(x["seats_now"] is not None for x in buses)
            probs = (board_probs(buses, info["stop_seq"], now, day["day_type"], prior) if seat_limited
                     else [1.0] * len(buses))
            drop = DEFAULT_DROP_PER_STOP * day["drop"]
            cands = [{"eta": x["eta"], "p": p, "ok": x["eta"] >= walk + BUFFER_MIN,
                      "seats": None if x["seats_now"] is None or not seat_limited
                      else max(round(x["seats_now"] - drop * x["n_stops"]), 0)}
                     for x, p in zip(buses, probs)]
            hw = (buses[1]["eta"] - buses[0]["eta"]) if len(buses) >= 2 and buses[1]["eta"] > buses[0]["eta"] else headway
            board = expected_board(cands, walk, hw)
            first_p = next((c["p"] for c in cands if c["ok"]), prior if seat_limited else 1.0)
            opts.append(dict(common, kind="버스", first_mode="bus", route=name, seat_known=seat_known,
                             stop={"id": sid or "", "name": first.get("startName"), "mobile_no": str(first.get("startArsID", "")).replace("-", ""),
                                   "lat": float(first.get("startY", 0)), "lng": float(first.get("startX", 0))},
                             board_prob=first_p, seat_limited=seat_limited, cands=cands, board=board,
                             total=board + after))
    # 같은 노선 · 같은 정류장이 여러 경로에 나오면 가장 빠른 것만
    best = {}
    for o in sorted(opts, key=lambda o: o["total"]):
        best.setdefault((o["route"], o["stop"]["name"]), o)
    opts = list(best.values())
    # 종류: 가장 가까운 탈 곳 = 기다리기, 거기서도 타는 노선을 다른 정류장에서 = 걸어가기, 그 밖 = 다른 버스
    bus_opts = [o for o in opts if o["first_mode"] == "bus"]
    if bus_opts:
        nearest = min(bus_opts, key=lambda o: o["walk_min"])["stop"]["name"]
        at_nearest = {o["route"] for o in bus_opts if o["stop"]["name"] == nearest}
        for o in bus_opts:
            o["kind"] = ("기다리기" if o["stop"]["name"] == nearest
                         else "걸어가기" if o["route"] in at_nearest else "다른 버스")
    import ranking
    return ranking.rank(opts, total=lambda o: o["total"], route=lambda o: o["route"],
                        walk=lambda o: o["walk_min"], prob=lambda o: o["board_prob"])


def tags_for(opts):
    """경로 목록 꼬리표: 추천(체감 시간 최소) · 최단시간 · 최소환승 — docs/api.md ⑤ 와 같은 뜻"""
    if not opts:
        return {}
    feel = {id(o): o["total"] + (WALK_WEIGHT - 1) * o["walk_min"] + (WAIT_WEIGHT - 1) * max(o["board"] - o["walk_min"], 0)
            + TRANSFER_PENALTY_MIN * o["transfers"] for o in opts}
    t = {id(o): [] for o in opts}
    t[id(min(opts, key=lambda o: feel[id(o)]))].append("추천")
    t[id(min(opts, key=lambda o: o["total"]))].append("최단시간")
    t[id(min(opts, key=lambda o: (o["transfers"], o["total"])))].append("최소환승")
    return t


def incidents_on(opts):
    """경로 근처(0.4km)의 공식 돌발상황 (국가교통정보센터) — 버스 구간이 있는 상위 경로만"""
    try:
        import incidents
        events = incidents.fetch_events()
    except Exception:
        return []
    out = []
    for o in opts[:3]:
        if not o["shape"] or o["first_mode"] != "bus":
            continue
        for ev in events:
            try:
                ex, ey = float(ev["coordX"]), float(ev["coordY"])
            except (KeyError, ValueError, TypeError):
                continue
            near = min(math.hypot((ex - x) * 88.8, (ey - y) * 111.0) for x, y in o["shape"])
            delay = incidents.event_delay(ev)
            if near <= 0.4 and delay:
                msg = (ev.get("message") or "").strip() or ev.get("eventDetailType") or ev.get("eventType")
                out.append({"route": o["route"], "delay_min": round(delay), "skip_stops": [], "drop_factor": 1.0,
                            "notes": [f"{ev.get('roadName') or ''} {msg}".strip()],
                            "_point": {"x": ex, "y": ey, "type": ev.get("eventType"), "road": ev.get("roadName"),
                                       "message": msg}})
                o["delay"] = o.get("delay", 0) + delay
                o["total"] += delay
                break
    return out


def result(from_lat, from_lng, to_lat, to_lng, from_name="현재 위치", to_name="도착지", now=None):
    """앱 응답 — /recommendations · /alternatives · /incidents · /route-choices 에 필요한 것 전부"""
    now = now or datetime.now()
    import daytype
    day = daytype.factors(now)
    source, origin = "odsay", None
    try:
        paths = odsay_paths(from_lng, from_lat, to_lng, to_lat)
    except RouteUnavailable:
        near = saved_nearby(from_lng, from_lat, to_lng, to_lat)
        if not near:
            raise
        paths, _ = near
        source, origin = "saved_nearby", (from_lat, from_lng)
    if not paths:
        raise RouteUnavailable("출발지와 도착지 사이 대중교통 경로를 찾지 못했습니다")
    opts = build_options(paths, now, day, origin)
    incs = incidents_on(opts)
    opts.sort(key=lambda o: o["total"])
    tags = tags_for(opts)
    b = bundle()

    def option(o):
        eta = now + timedelta(minutes=o["total"])
        return {
            "kind": o["kind"], "route": o["route"], "stop": o["stop"], "walk_min": o["walk_min"],
            "board_prob": round(o["board_prob"], 2), "arrive_at": eta.strftime("%H:%M"),
            "total_min": round(o["total"]), "delay_min": round(o.get("delay", 0)), "skipped": False,
            "alight_stop": o["alight_stop"], "first_mode": o["first_mode"], "seat_limited": o["seat_limited"],
            "seat_known": o.get("seat_known", False),
            "transfers": o["transfers"], "summary": o["summary"],
            "buses": [{"order": i, "eta_min": round(c["eta"]), "seats_at_stop": c["seats"],
                       "board_prob": round(c["p"], 2), "reachable": c["ok"]}
                      for i, c in enumerate(o.get("cands", []), 1)],
            "route_choices": [{"arrive_at": eta.strftime("%H:%M"), "transfers": o["transfers"], "walk_min": o["walk_min"],
                               "tags": tags.get(id(o), []), "alight_stop": o["alight_stop"], "summary": o["summary"]}],
        }

    best = opts[0] if opts else None
    tip = ""
    if best:
        first = next((c for c in best.get("cands", []) if c["ok"]), None)
        if best.get("seat_known") and first and first["seats"] is not None and first["p"] < b.get("risk_threshold", 0.5):
            tip = " — 첫 차는 만석 위험이 커서 다음 차를 타는 게 낫습니다"
        elif best["first_mode"] == "subway":
            tip = " — 지하철이 가장 빠르고 좌석 걱정이 없습니다"
        elif best["seat_limited"] and not best.get("seat_known"):
            tip = f" — 좌석 정보가 없는 노선이라 평소 탑승률로 계산했습니다, {best['alight_stop']}에서 내립니다"
        elif best["seat_limited"]:
            tip = f" — 탑승 확률 {best['board_prob']:.0%}, {best['alight_stop']}에서 내립니다"
        else:
            tip = f" — {best['alight_stop']}에서 내립니다"
    by_kind = {}
    for o in opts:
        by_kind.setdefault(o["kind"], o)
    sub = by_kind.get("지하철")
    return {
        "generated_at": now.strftime("%Y-%m-%dT%H:%M:%S+09:00"),
        "origin": {"name": from_name, "lat": from_lat, "lng": from_lng},
        # odsay = 이 출발지로 조회한 경로 · saved_nearby = ODsay 를 못 써서 1.5km 안 저장 경로 + 도보만 다시 계산
        "route_source": source,
        "destination": {"name": to_name, "lat": to_lat, "lng": to_lng},
        "my_route": None,
        "home_stop": None if not best else {"id": best["stop"]["id"], "name": best["stop"]["name"]},
        "day": {"label": day["label"], "kind": day["key"], "drop_factor": day["drop"], "headway_factor": day["headway"]},
        "recommendation": None if not best else {
            "kind": best["kind"], "route": best["route"], "stop_name": best["stop"]["name"],
            "message": f"{best['kind']} ({best['route']}{'번' if best['first_mode'] == 'bus' else ''} · {best['stop']['name']}){tip}"},
        "ranking": [option(o) for o in opts],
        "alternatives": {k: option(by_kind[k]) if k in by_kind else None for k in ("기다리기", "걸어가기", "다른 버스")},
        "subway": None if not sub else {"arrive_at": (now + timedelta(minutes=sub["total"])).strftime("%H:%M"),
                                        "total_min": round(sub["total"]), "transfers": sub["transfers"],
                                        "walk_min": sub["walk_min"], "summary": sub["summary"]},
        "incidents": incs,
        "_shapes": [{"route": o["route"], "main": i == 0, "map_obj": o["map_obj"], "shape": o["shape"],
                     "tags": tags.get(id(o), []), "minutes": round(o["total"]), "transfers": o["transfers"],
                     "summary": o["summary"]} for i, o in enumerate(opts[:4])],
        "_stops": [o["stop"] for o in opts],
    }


def places(q, count=8):
    """장소 검색 (TMAP POI) → [{name, address, lat, lng}]"""
    res = requests.get("https://apis.openapi.sk.com/tmap/pois", timeout=10, params={
        "version": 1, "searchKeyword": q, "count": count, "appKey": api_key.TMAP_KEY})
    if res.status_code == 204 or not res.text.strip():
        return []
    out = []
    for p in res.json().get("searchPoiInfo", {}).get("pois", {}).get("poi", []):
        lat, lng = p.get("frontLat") or p.get("noorLat"), p.get("frontLon") or p.get("noorLon")
        if not lat or not lng:
            continue
        addr = " ".join(x for x in (p.get("upperAddrName"), p.get("middleAddrName"), p.get("lowerAddrName")) if x)
        out.append({"name": p.get("name"), "address": addr, "lat": round(float(lat), 6), "lng": round(float(lng), 6)})
    return out


# 현장 시험 · 시연에서 쓸 출발지 → 국민대 경로를 미리 저장 (ODsay 하루 한도가 남았을 때 한 번: python trip.py prefetch)
PREFETCH_TO = (37.6110, 126.9952)   # 국민대학교
PREFETCH_FROM = {
    "수원 삼성1차아파트": (37.2726, 127.0436), "분당 서현역": (37.3850, 127.1233), "분당 정자역": (37.3670, 127.1083),
    "용인 수지구청역": (37.3222, 127.0950), "용인 죽전역": (37.3247, 127.1074), "고양 주엽역": (37.6703, 126.7612),
    "고양 화정역": (37.6343, 126.8326), "남양주 도농역": (37.6085, 127.1615), "구리역": (37.6033, 127.1435),
    "하남 미사역": (37.5631, 127.1930), "김포 장기동": (37.6431, 126.6696), "화성 동탄역": (37.2005, 127.0957),
    "의정부역": (37.7385, 127.0459), "안양 평촌역": (37.3943, 126.9636), "광명 철산역": (37.4760, 126.8680),
    "부천 중동": (37.5035, 126.7650),
}


def prefetch_routes():
    for name, (lat, lng) in PREFETCH_FROM.items():
        try:
            n = len(odsay_paths(lng, lat, PREFETCH_TO[1], PREFETCH_TO[0]))
            print(f"  ✔ {name} → 국민대 경로 {n}개")
        except RouteUnavailable as e:
            print(f"  ✘ {name}: {e}")
            if any(w in str(e).lower() for w in ("한도", "limit", "quota")):
                break


if __name__ == "__main__":
    if sys.argv[1:2] == ["prefetch"]:
        prefetch_routes()
        sys.exit()
    a = [float(x) for x in sys.argv[1:5]]
    r = result(*a)
    print(r["recommendation"])
    for o in r["ranking"]:
        print(f"{o['arrive_at']} {o['kind']:5} {o['route']:>8} @{o['stop']['name']} 도보{o['walk_min']} "
              f"확률{o['board_prob']:.0%} 좌석제={o['seat_limited']} | {o['summary']}")
