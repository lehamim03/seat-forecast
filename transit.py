# ==========================================================
#  환승 경로(ODsay) · 도보 시간(TMAP) 조회
#  실행:  python transit.py     (서울 도착 지점 → 국민대 경로와 정류장 간 도보 시간 출력)
#  다른 코드에서는 get_legs(), get_walks() 를 불러 씁니다.
#  결과는 transit_cache.json 에 하루 동안 저장해서 API 호출을 아낍니다.
# ==========================================================
import json
import os
import re
from datetime import date

import requests

import api_key
from config import (SERVICE_KEY, ROUTES, STOPS, MY_STOP, DEST_NAME, DEST_X, DEST_Y,
                    TRANSFER_PENALTY_MIN)

CACHE = "transit_cache.json"
# 경로 고르는 기준이 바뀌면 저장된 결과를 다시 조회하도록 설정값을 함께 기록
SIG = f"penalty={TRANSFER_PENALTY_MIN};" + ";".join(f"{r}={c.get('prefer_bus')}" for r, c in ROUTES.items())


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


def station_xy(route_id, seq):
    """노선의 seq번째 정류장 좌표 (공공데이터포털 노선 API)."""
    res = requests.get("https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteStationListv2",
                       params={"serviceKey": SERVICE_KEY, "routeId": route_id, "format": "json"}, timeout=10)
    for s in res.json()["response"]["msgBody"]["busRouteStationList"]:
        if int(s["stationSeq"]) == seq:
            return float(s["x"]), float(s["y"]), s["stationName"]
    raise ValueError("정류장을 찾지 못함")


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


def odsay_routes(sx, sy, ex, ey):
    """ODsay 대중교통 길찾기 → 후보 경로 전부 (소요시간, 환승 횟수, 탄 버스, 한 줄 요약)."""
    res = requests.get("https://api.odsay.com/v1/api/searchPubTransPathT", timeout=15,
                       params={"SX": sx, "SY": sy, "EX": ex, "EY": ey, "apiKey": api_key.ODSAY_KEY})
    data = res.json()
    if "error" in data:
        raise RuntimeError(str(data["error"])[:150])
    out = []
    for p in data["result"]["path"]:
        i = p["info"]
        out.append({
            "minutes": i["totalTime"],
            "transfers": i["busTransitCount"] + i["subwayTransitCount"] - 1,
            "buses": [re.sub(r"\(.*?\)", "", l["busNo"]) for sp in p["subPath"]
                      if sp["trafficType"] == 2 for l in sp["lane"]],
            "summary": _describe(p),
        })
    return out


def rank(routes, prefer_bus=None):
    """경로 순위: 소요시간 + 환승 1회당 벌점. prefer_bus 가 있으면 그 버스를 타는 경로를 우선."""
    score = lambda r: r["minutes"] + TRANSFER_PENALTY_MIN * r["transfers"]
    preferred = [r for r in routes if prefer_bus and prefer_bus in r["buses"]]
    others = [r for r in routes if r not in preferred]
    return sorted(preferred, key=score) + sorted(others, key=score)


def tmap_walk_min(ax, ay, bx, by):
    """TMAP 보행자 경로 → 실제 걷는 길 기준 도보 시간(분)."""
    res = requests.post("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", timeout=15,
                        headers={"appKey": api_key.TMAP_KEY},
                        json={"startX": ax, "startY": ay, "endX": bx, "endY": by,
                              "startName": "start", "endName": "end"})
    res.raise_for_status()
    return round(res.json()["features"][0]["properties"]["totalTime"] / 60)


def get_legs():
    """노선별 '서울 도착 지점 → 국민대' 환승 경로. 실패하면 config 의 가정값을 씁니다."""
    c = _load_cache()
    if "legs" not in c:
        legs = {}
        for route, cfg in ROUTES.items():
            try:
                x, y, name = station_xy(cfg["route_id"], cfg["dest_seq"])
                ranked = rank(odsay_routes(x, y, DEST_X, DEST_Y), cfg.get("prefer_bus"))
                best = ranked[0]
                alts = [{"minutes": r["minutes"], "transfers": r["transfers"], "transfer": r["summary"]}
                        for r in ranked[1:3]]
                legs[route] = {"minutes": best["minutes"], "transfers": best["transfers"],
                               "transfer": best["summary"], "from": name, "live": True, "alts": alts}
            except Exception as e:
                print(f"  ! {route} 환승 경로 조회 실패 — 가정값 사용 ({e})")
                legs[route] = {"minutes": cfg["to_kookmin_min"], "transfer": cfg["transfer"], "live": False}
        if all(l["live"] for l in legs.values()):
            c["legs"] = legs
            _save_cache(c)
        return legs
    return c["legs"]


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
    print(f"[서울 도착 지점 → {DEST_NAME}]  (ODsay 대중교통 길찾기)")
    for route, leg in get_legs().items():
        src = leg.get("from", ROUTES[route]["dest"])
        tag = "" if leg["live"] else "  (가정값)"
        n = f" · 환승 {leg['transfers']}회" if "transfers" in leg else ""
        print(f"  {route}번 {src}: 약 {leg['minutes']}분{n}{tag}\n      {leg['transfer']}")
        for a in leg.get("alts", []):
            print(f"      (다른 경로 {a['minutes']}분 · 환승 {a['transfers']}회) {a['transfer']}")
    print(f"\n[{STOPS[MY_STOP]['name']} → 후보 정류장 도보 시간]  (TMAP 보행자 경로)")
    for stop_id, m in get_walks().items():
        print(f"  {STOPS[stop_id]['name']}: {m}분")
