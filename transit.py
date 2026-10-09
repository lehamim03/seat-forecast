# ==========================================================
#  환승 경로(ODsay) · 도보 시간(TMAP) 조회
#  실행:  python transit.py     (노선별 최적 하차 정류장 → 국민대 경로와 정류장 간 도보 시간 출력)
#  다른 코드에서는 get_legs(), get_walks() 를 불러 씁니다.
#  정류장 목록 · 도보 시간은 transit_cache.json 에 하루, ODsay 결과는 odsay_cache.json 에 7일 저장
#  (ODsay 는 하루 호출 한도가 작고, 결과가 시간표 기반이라 매일 바뀌지 않음).
# ==========================================================
import glob
import json
import math
import os
import re
from datetime import date, datetime, timedelta

import requests

import api_key
from config import (SERVICE_KEY, ROUTES, STOPS, MY_STOP, DEST_NAME, DEST_X, DEST_Y,
                    TRANSFER_PENALTY_MIN, WALK_WEIGHT, WAIT_WEIGHT, CITY_BUS_PEAK_FACTOR, PEAK_HOURS)

CACHE = "transit_cache.json"
# 경로 고르는 기준이 바뀌면 저장된 결과를 다시 조회하도록 설정값을 함께 기록
SIG = "v4;" + ";".join(f"{r}={c['route_id']}" for r, c in ROUTES.items())
ODSAY_CACHE = "odsay_cache.json"
ODSAY_DAYS = 7
MAX_CHOICES = 4   # 노선마다 보여줄 경로 수 (지도 앱처럼 여러 개)
SEOUL_BUS_KMH = 15   # 서울 구간 버스 속도 (실측 데이터가 없을 때만 사용)
SKIP_WORDS = ("(경유)", "(미정차)")   # 정류장 이름에 붙으면 서지 않는 지점


def _load_cache():
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            c = json.load(f)
        if c.get("date") == date.today().isoformat() and c.get("sig") == SIG:
            return c
    return {"date": date.today().isoformat(), "sig": SIG}


def _save_cache(c):
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump(c, f, ensure_ascii=False, indent=1)


def route_stations(route_id):
    """노선의 정류장 목록 {순번: {name, x, y, region, turn}} — 하루 한 번 조회해 저장."""
    c = _load_cache()
    stations = c.setdefault("stations", {})
    if route_id not in stations:
        res = requests.get("https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteStationListv2",
                           params={"serviceKey": SERVICE_KEY, "routeId": route_id, "format": "json"}, timeout=10)
        stations[route_id] = {str(s["stationSeq"]): {"name": s["stationName"], "x": float(s["x"]), "y": float(s["y"]),
                                                     "region": s.get("regionName") or "", "turn": s.get("turnYn") == "Y"}
                              for s in res.json()["response"]["msgBody"]["busRouteStationList"]}
        _save_cache(c)
    return {int(k): v for k, v in stations[route_id].items()}


def station_xy(route_id, seq):
    """노선의 seq번째 정류장 좌표 (공공데이터포털 노선 API)."""
    s = route_stations(route_id).get(seq)
    if s is None:
        raise ValueError("정류장을 찾지 못함")
    return s["x"], s["y"], s["name"]


def turn_seq(route):
    """노선이 서울에서 돌아 나오는 회차 정류장의 순번 — 이 뒤는 수원으로 돌아가는 방향."""
    st = route_stations(ROUTES[route]["route_id"])
    return next((q for q in sorted(st) if st[q]["turn"]), max(st))


def alight_candidates(route):
    """서울에서 내릴 수 있는 정류장 순번 — 회차 전까지, 서울 지역, 실제로 서는 곳."""
    st = route_stations(ROUTES[route]["route_id"])
    turn = turn_seq(route)
    seqs = [q for q in sorted(st) if q <= turn and "서울" in st[q]["region"]
            and not any(w in st[q]["name"] for w in SKIP_WORDS)]
    return seqs or [turn]


def alight_seq(route, now=None):
    """지금 고른 하차 정류장 순번 — 저장된 ODsay 결과로만 계산 (API 를 새로 부르지 않음). 없으면 회차 정류장."""
    try:
        return best_alight(route, now, fetch=False)["seq"]
    except Exception:
        return turn_seq(route)


def _km(a, b):
    dx = (a["x"] - b["x"]) * 88.8
    dy = (a["y"] - b["y"]) * 111.0
    return math.hypot(dx, dy)


_SEG = {}


def seoul_bus_min(route, a, b):
    """서울 구간 a → b 버스 이동시간(분): 수집 데이터 실측 중앙값, 없으면 거리로 추정."""
    if a == b:
        return 0.0
    if route not in _SEG:
        _SEG[route] = None
        files = glob.glob("data/bus_seats_*.csv")
        try:
            import pandas as pd
        except ImportError:   # pandas 가 없는 환경에서는 거리로 추정
            files = []
        if files:
            df = pd.concat([pd.read_csv(f, usecols=["collected_at", "route_name", "plate_no", "station_seq"])
                            for f in files])
            df = df[df["route_name"].astype(str) == route]
            df["collected_at"] = pd.to_datetime(df["collected_at"])
            # 차량 · 날짜마다 각 정류장 순번에서 처음 보인 시각
            df["day"] = df["collected_at"].dt.date
            _SEG[route] = df.groupby(["day", "plate_no", "station_seq"])["collected_at"].min().unstack()
    first = _SEG[route]
    if first is not None and a in first and b in first:
        m = ((first[b] - first[a]).dt.total_seconds() / 60).dropna()
        m = m[(m > 0) & (m < 90)]
        if len(m):
            return float(m.median())
    st = route_stations(ROUTES[route]["route_id"])
    km = sum(_km(st[q], st[q + 1]) for q in range(a, b) if q in st and q + 1 in st)
    return km / SEOUL_BUS_KMH * 60


def _describe(path):
    """ODsay 경로 하나를 '4호선(사당→길음) → 버스 163(...)' 같은 한 줄로."""
    steps = []
    for sp in path["subPath"]:
        if sp["trafficType"] == 1:      # 지하철
            steps.append(f"{sp['lane'][0]['name'].replace('수도권 ', '')}({sp['startName']}→{sp['endName']})")
        elif sp["trafficType"] == 2:    # 버스 (같은 구간을 가는 버스가 여러 개면 3개까지)
            nos = "/".join(re.sub(r"\(.*?\)", "", l["busNo"]) for l in sp["lane"][:3])
            steps.append(f"버스 {nos}({sp['startName']}→{sp['endName']})")
        elif sp.get("sectionTime", 0) >= 3:   # 3분 이상 걷는 구간만 표시
            steps.append(f"도보 {sp['sectionTime']}분")
    return " → ".join(steps)


def _parse(data):
    """ODsay 응답 → 후보 경로 목록. 시간을 쪼개 둠 (탄 시간 · 걷기 · 시내버스 · 기다림)."""
    out = []
    for p in data["result"]["path"]:
        legs = [sp for sp in p["subPath"] if sp["trafficType"] in (1, 2)]
        lanes = [l for sp in legs if sp["trafficType"] == 2 for l in sp["lane"]]
        out.append({
            "minutes": p["info"]["totalTime"],
            "transfers": len(legs) - 1,
            "walk": sum(sp.get("sectionTime", 0) for sp in p["subPath"] if sp["trafficType"] == 3),
            "citybus": sum(sp["sectionTime"] for sp in legs if sp["trafficType"] == 2),
            # 타는 곳마다 평균 배차간격의 절반을 기다림 (ODsay totalTime 에는 기다림이 없음)
            "wait": sum(sp.get("intervalTime") or 10 for sp in legs) / 2,
            "buses": [re.sub(r"\(.*?\)", "", l["busNo"]) for l in lanes],
            # 심야버스(N…) · 예약버스는 출퇴근 시간에 탈 수 없으므로 순위에서 뺌
            "special": any(l["busNo"].startswith("N") or "예약" in l["busNo"] for l in lanes),
            "map_obj": p["info"].get("mapObj"),
            "summary": _describe(p),
        })
    return out


def odsay_routes(sx, sy, ex=DEST_X, ey=DEST_Y, fetch=True):
    """ODsay 대중교통 길찾기 → 후보 경로 전부. 같은 출발점은 7일 동안 저장해 둔 결과를 씀.
    fetch=False 이면 저장된 것만 (없으면 None)."""
    key = f"{sx},{sy}->{ex},{ey}"
    cache = {}
    if os.path.exists(ODSAY_CACHE):
        with open(ODSAY_CACHE, encoding="utf-8") as f:
            cache = json.load(f)
    hit = cache.get(key)
    if hit and date.fromisoformat(hit["date"]) > date.today() - timedelta(days=ODSAY_DAYS):
        return hit["routes"]
    if not fetch:
        return None
    res = requests.get("https://api.odsay.com/v1/api/searchPubTransPathT", timeout=15,
                       params={"SX": sx, "SY": sy, "EX": ex, "EY": ey, "apiKey": api_key.ODSAY_KEY})
    data = res.json()
    if "error" in data:
        raise RuntimeError(str(data["error"])[:150])
    routes = _parse(data)
    cache[key] = {"date": date.today().isoformat(), "routes": routes}
    with open(ODSAY_CACHE, "w", encoding="utf-8") as f:
        json.dump(cache, f, ensure_ascii=False)
    return routes


def is_peak(now=None):
    """평일 출퇴근 시간대인지 (공휴일 제외)."""
    now = now or datetime.now()
    try:
        import daytype
        return daytype.day_info(now)["peak"]
    except Exception:
        hm = now.hour * 60 + now.minute
        return now.weekday() < 5 and any(a <= hm < b for a, b in PEAK_HOURS)


def expected_min(r, peak=False):
    """실제로 걸릴 것으로 보는 시간(분): ODsay 시간 + 기다림 (+ 출퇴근이면 시내버스 정체)."""
    extra = (CITY_BUS_PEAK_FACTOR - 1) * r["citybus"] if peak else 0
    return r["minutes"] + r["wait"] + extra


def score(r, peak=False):
    """경로 점수(작을수록 좋음) — 사람이 느끼는 부담 기준:
    걷기 · 기다림은 차 안보다 무겁게, 환승 1회당 벌점, 출퇴근 시간 시내버스는 정체만큼 더."""
    ride = r["minutes"] - r["walk"]
    extra = (CITY_BUS_PEAK_FACTOR - 1) * r["citybus"] if peak else 0
    return (ride + extra + WALK_WEIGHT * r["walk"] + WAIT_WEIGHT * r["wait"]
            + TRANSFER_PENALTY_MIN * r["transfers"])


def rank(routes, peak=False):
    """경로 순위 — 심야 · 예약버스 경로는 제외하고 점수순."""
    return sorted((r for r in routes if not r.get("special")), key=lambda r: score(r, peak))


def tmap_walk_min(ax, ay, bx, by):
    """TMAP 보행자 경로 → 실제 걷는 길 기준 도보 시간(분)."""
    res = requests.post("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", timeout=15,
                        headers={"appKey": api_key.TMAP_KEY},
                        json={"startX": ax, "startY": ay, "endX": bx, "endY": by,
                              "startName": "start", "endName": "end"})
    res.raise_for_status()
    return round(res.json()["features"][0]["properties"]["totalTime"] / 60)


def best_alight(route, now=None, fetch=True):
    """서울 안 하차 후보마다 '버스로 그 정류장까지(실측) + 그 뒤 경로(ODsay)'를 비교해 가장 나은 곳을 고름.
    minutes 는 서울 첫 정차 정류장 기준 예상 시간 (train.py 의 ride 가 그 정류장까지의 시간)."""
    cfg = ROUTES[route]
    st = route_stations(cfg["route_id"])
    cands = alight_candidates(route)
    peak = is_peak(now)
    options, errors = [], []
    for q in cands:
        try:
            routes = odsay_routes(st[q]["x"], st[q]["y"], fetch=fetch)
        except Exception as e:
            errors.append(f"{st[q]['name']}: {e}")
            continue
        if not routes:
            continue
        bus = seoul_bus_min(route, cands[0], q)
        for r in rank(routes, peak):
            options.append(dict(r, seq=q, name=st[q]["name"], cost=bus + score(r, peak),
                                total=round(bus + expected_min(r, peak))))
    if not options:
        raise RuntimeError("후보 경로 없음" + (f" ({errors[0]})" if errors else ""))
    options.sort(key=lambda o: o["cost"])
    best = options[0]
    alts = [o for o in options[1:] if o["seq"] != best["seq"] or o["summary"] != best["summary"]][:2]
    return {"minutes": best["total"], "transfers": best["transfers"], "seq": best["seq"], "from": best["name"],
            "transfer": best["summary"], "map_obj": best.get("map_obj"), "live": True, "peak": peak,
            "partial": bool(errors), "choices": pick_choices(options),
            "alts": [{"minutes": o["total"], "transfers": o["transfers"],
                      "transfer": f"{o['name']} 하차 → {o['summary']}"} for o in alts]}


def pick_choices(options, n=MAX_CHOICES):
    """지도 앱처럼 보여줄 경로 여러 개 — 추천 · 최단시간 · 최소환승 표시를 붙이고 나머지는 추천순으로 채움.
    options 는 점수(cost)순으로 정렬된 상태. transfers 는 광역버스에서 내린 뒤 갈아타는 횟수(광역버스 → 첫 수단 포함)."""
    uniq, seen = [], set()
    for o in options:
        key = (o["seq"], o["summary"])
        if key not in seen:
            seen.add(key)
            uniq.append(o)
    picks = {"추천": uniq[0],
             "최단시간": min(uniq, key=lambda o: (o["total"], o["cost"])),
             "최소환승": min(uniq, key=lambda o: (o["transfers"], o["total"], o["cost"]))}
    chosen = []
    for o in [picks["추천"], picks["최단시간"], picks["최소환승"]] + uniq:
        if o not in chosen:
            chosen.append(o)
        if len(chosen) >= n:
            break
    chosen.sort(key=lambda o: (o is not picks["추천"], o["total"]))   # 추천을 맨 위, 나머지는 빠른 순
    return [{"minutes": o["total"], "transfers": o["transfers"] + 1, "walk": o["walk"], "seq": o["seq"],
             "from": o["name"], "transfer": o["summary"], "map_obj": o.get("map_obj"),
             "tags": [t for t in ("추천", "최단시간", "최소환승") if picks[t] is o]} for o in chosen]


def fallback_leg(route):
    """ODsay 를 쓸 수 없을 때 config 의 가정값 ('서울시청 하차 → …' 형식)으로 만든 경로."""
    cfg = ROUTES[route]
    name, _, rest = cfg["transfer"].partition(" 하차 → ")
    return {"minutes": cfg["to_kookmin_min"], "transfer": rest or cfg["transfer"],
            "from": name if rest else "서울", "live": False}


def get_legs(now=None, fetch=True):
    """노선별 '서울 하차 정류장 → 국민대' 경로 (하차 정류장도 자동 선택, 시각에 따라 출퇴근 기준 적용).
    ODsay 결과는 odsay_cache.json 에 저장되어 있으면 API 를 다시 부르지 않음. 실패하면 config 의 가정값."""
    legs = {}
    for route in ROUTES:
        try:
            legs[route] = best_alight(route, now, fetch)
        except Exception as e:
            if fetch:
                print(f"  ! {route} 환승 경로 조회 실패 — 가정값 사용 ({e})")
            legs[route] = fallback_leg(route)
    return legs


def get_walks():
    """평소 정류장 → 각 후보 정류장 도보 시간(분). 실패하면 config 의 walk_min 을 씁니다."""
    c = _load_cache()
    if "walks" in c:
        return c["walks"]
    near = {}
    if os.path.exists("nearby_stops.json"):
        with open("nearby_stops.json", encoding="utf-8") as f:
            near = {s["station_id"]: s for s in json.load(f)}
    walks, ok = {}, True
    for stop_id, stop in STOPS.items():
        base = abs(stop["walk_min"] - STOPS[MY_STOP]["walk_min"])
        if stop_id == MY_STOP:
            walks[stop_id] = 0
            continue
        try:
            a, b = near[MY_STOP], near[stop_id]
            walks[stop_id] = tmap_walk_min(a["lng"], a["lat"], b["lng"], b["lat"])
        except Exception as e:
            print(f"  ! {stop['name']} 도보 시간 조회 실패 — 추정값 사용 ({e})")
            walks[stop_id], ok = base, False
    if ok:
        c["walks"] = walks
        _save_cache(c)
    return walks


if __name__ == "__main__":
    print(f"[서울 하차 정류장 → {DEST_NAME}]  (후보 정류장마다 ODsay 대중교통 길찾기 · 가장 빠른 곳 선택)")
    for route, leg in get_legs().items():
        src = leg.get("from", "회차 정류장")
        if leg.get("partial"):
            src += " (일부 후보는 ODsay 한도로 아직 비교 못 함)"
        tag = "" if leg["live"] else "  (가정값)"
        n = f" · 환승 {leg['transfers']}회" if "transfers" in leg else ""
        print(f"  {route}번 {src}: 약 {leg['minutes']}분{n}{tag}\n      {leg['transfer']}")
        for ch in leg.get("choices", []):
            tags = "".join(f"[{t}]" for t in ch["tags"]) or "      "
            print(f"      {tags:<10} {ch['minutes']}분 · 환승 {ch['transfers']}회 | {ch['from']} 하차 → {ch['transfer']}")
    print(f"\n[{STOPS[MY_STOP]['name']} → 후보 정류장 도보 시간]  (TMAP 보행자 경로)")
    for stop_id, m in get_walks().items():
        print(f"  {STOPS[stop_id]['name']}: {m}분")
