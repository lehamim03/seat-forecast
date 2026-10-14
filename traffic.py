# ==========================================================
#  실시간 도로 속도(국가교통정보센터 교통소통정보)로 돌발 감지
#  실행:  python traffic.py --build   (처음 한 번: 노선이 지나는 지점 ↔ 도로 구간 번호 표 만들기)
#         python traffic.py           (지금 노선별 도로 속도 · 평소 대비 지연 출력)
#
#  앞차가 사고 지점에 닿기 전에도, 도로 속도가 평소보다 크게 떨어지면 미리 알 수 있습니다.
#  - 지점: 노선의 고속도로 · 경유 지점(신갈JC, 판교IC, 학의JC …) — 집 앞 정류장 ~ 서울 하차 정류장
#  - 경부고속도로(8800 · 3007 · 3008)는 서울 방향(상행) 구간만, 7001 의 과천·의왕 도로는 도로명으로 거름
#  - 평소 속도: 수집해 둔 data/traffic_*.csv 의 같은 요일 종류 · 같은 시간대 중앙값
#    (기록이 모이기 전에는 시속 20km 아래로 떨어질 때만 '정체'로 봄)
#  응답에 좌표가 없어서, 지점마다 좁은 범위로 한 번 조회해 구간 번호 표(traffic_links.json)를 만들어 두고
#  이후에는 도로당 한 번(넓은 범위)만 조회합니다.
# ==========================================================
import csv
import glob
import json
import math
import os
import sys
from datetime import datetime, timedelta

import requests

import api_key
import transit
from config import ROUTES, MY_STOP

URL = "https://openapi.its.go.kr:9443/trafficInfo"
LINKS_FILE = "traffic_links.json"
CSV_FILE = "traffic.csv"            # collect.py 가 기록, save_data.py 가 data/traffic_운행일.csv 로 옮김
COLUMNS = ["collected_at", "link_id", "speed"]
NEAR_DEG = 0.008                    # 지점 주변 조회 범위 (약 0.8km)
EX_ROUTE_NO = {"경부": "0010"}      # 고속도로 노선번호 (config 의 roads → ITS routeNo)
SLOW_RATIO = 0.6                    # 평소 속도의 이 비율 아래로 떨어지고
SLOW_GAP = 20                       # 평소보다 이 km/h 이상 느리면 '정체'
NO_HISTORY_KMH = 20                 # 평소 기록이 없을 때: 이 속도 아래면 '정체' (평소에도 막히는 구간 오판 방지)
FREE_KMH = {"ex": 80, "all": 50}    # 평소 기록이 없을 때 쓰는 평소 속도
MAX_DELAY = 20                      # 도로 속도로 더하는 지연 상한(분)
_CACHE = {}                         # 같은 실행 안에서 5분 동안 조회 결과 재사용


def _key():
    return getattr(api_key, "ITS_KEY", "")


def _km(a, b):
    return math.hypot((a["x"] - b["x"]) * 88.4, (a["y"] - b["y"]) * 111.0)


def _query(kind, route_no, minx, maxx, miny, maxy):
    res = requests.get(URL, timeout=30, params={
        "apiKey": _key(), "type": kind, "routeNo": route_no, "drcType": "all", "getType": "json",
        "minX": minx, "maxX": maxx, "minY": miny, "maxY": maxy})
    body = res.json().get("body")
    return (body or {}).get("items") or [] if isinstance(body, dict) else []


def _group(route):
    """노선이 지나는 도로의 조회 방식: 경부고속도로면 (ex, 0010, 상행만), 아니면 (all, all, 도로명)."""
    roads = ROUTES[route].get("roads", [])
    for r in roads:
        if r in EX_ROUTE_NO:
            return "ex", EX_ROUTE_NO[r], None
    return "all", "all", roads


def waypoints(route):
    """집 앞 정류장 ~ 서울 첫 정차 정류장 사이의 경유 지점(고속도로 IC · JC · TG 등) 순번."""
    st = transit.route_stations(ROUTES[route]["route_id"])
    home = ROUTES[route]["stops"].get(MY_STOP, min(ROUTES[route]["stops"].values()))
    end = transit.alight_candidates(route)[0]
    return [q for q in sorted(st) if home < q < end and any(w in st[q]["name"] for w in transit.SKIP_WORDS)]


def build_links():
    """지점마다 좁은 범위로 조회해 {구간 번호: 노선 · 지점} 표를 만든다 (처음 한 번)."""
    links = {}
    for route in ROUTES:
        kind, route_no, names = _group(route)
        st = transit.route_stations(ROUTES[route]["route_id"])
        for q in waypoints(route):
            p = st[q]
            items = _query(kind, route_no, p["x"] - NEAR_DEG, p["x"] + NEAR_DEG, p["y"] - NEAR_DEG, p["y"] + NEAR_DEG)
            for it in items:
                if names is None and it.get("roadDrcType") != "상행":     # 경부: 서울 방향만
                    continue
                if names is not None and not any(n in (it.get("roadName") or "") for n in names):
                    continue
                links.setdefault(it["linkId"], {"road": it.get("roadName"), "points": []})
                links[it["linkId"]]["points"].append({"route": route, "seq": q, "name": st[q]["name"]})
            print(f"  {route} {st[q]['name']}: 구간 {sum(1 for v in links.values() for x in v['points'] if x['route'] == route and x['seq'] == q)}개")
    with open(LINKS_FILE, "w", encoding="utf-8") as f:
        json.dump(links, f, ensure_ascii=False, indent=1)
    print(f"저장: {LINKS_FILE} (구간 {len(links)}개)")
    return links


def load_links():
    if not os.path.exists(LINKS_FILE):
        return {}
    with open(LINKS_FILE, encoding="utf-8") as f:
        return json.load(f)


def fetch_speeds(now=None):
    """지금 구간별 속도 {구간 번호: km/h} — 도로(조회 방식)마다 넓은 범위로 한 번씩."""
    now = now or datetime.now()
    if "speeds" in _CACHE and now - _CACHE["at"] < timedelta(minutes=5):
        return _CACHE["speeds"]
    links = load_links()
    if not (_key() and links):
        return {}
    speeds = {}
    groups = {}
    for route in ROUTES:
        st = transit.route_stations(ROUTES[route]["route_id"])
        pts = [st[q] for q in waypoints(route)]
        if pts:
            g = groups.setdefault(_group(route)[:2], [])
            g.extend(pts)
    for (kind, route_no), pts in groups.items():
        xs, ys = [p["x"] for p in pts], [p["y"] for p in pts]
        try:
            for it in _query(kind, route_no, min(xs) - NEAR_DEG, max(xs) + NEAR_DEG, min(ys) - NEAR_DEG, max(ys) + NEAR_DEG):
                if it.get("linkId") in links:
                    speeds[it["linkId"]] = float(it["speed"])
        except Exception as e:
            print(f"  ! 도로 속도 조회 실패 ({kind} {route_no}): {e}")
    _CACHE.update(speeds=speeds, at=now)
    return speeds


def record(now):
    """collect.py 용: 지금 구간 속도를 traffic.csv 에 한 줄씩 추가 (평소 속도 기록)."""
    speeds = fetch_speeds(now)
    if not speeds:
        return 0
    new = not os.path.exists(CSV_FILE)
    with open(CSV_FILE, "a", newline="", encoding="utf-8-sig") as f:
        w = csv.writer(f)
        if new:
            w.writerow(COLUMNS)
        stamp = now.strftime("%Y-%m-%d %H:%M:%S")
        for link, v in speeds.items():
            w.writerow([stamp, link, v])
    return len(speeds)


_HIST = {}


def usual_speed(link, now):
    """같은 요일 종류 · 같은 시간대(±1시간)의 평소 속도 중앙값 (기록이 3개 미만이면 None)."""
    import daytype
    if not _HIST:
        import pandas as pd
        files = glob.glob(os.path.join("data", "traffic_*.csv"))
        if files:
            df = pd.concat([pd.read_csv(f, dtype={"link_id": str}) for f in files])
            df["collected_at"] = pd.to_datetime(df["collected_at"])
            kinds = {d: daytype.day_info(d)["day_type"] for d in df["collected_at"].dt.date.unique()}
            df["kind"] = df["collected_at"].dt.date.map(kinds)
            df["hour"] = df["collected_at"].dt.hour
            _HIST["df"] = df
        else:
            _HIST["df"] = None
    df = _HIST["df"]
    if df is None:
        return None
    kind = daytype.day_info(now.date())["day_type"]
    m = df[(df["link_id"] == link) & (df["kind"] == kind) & ((df["hour"] - now.hour).abs() <= 1)]["speed"]
    return float(m.median()) if len(m) >= 3 else None


def detect(now=None, speeds=None):
    """노선별 도로 정체 → {노선: {"delay": 분, "notes": [...]}} (집 앞 ~ 서울 첫 정차 사이 지점만)."""
    now = now or datetime.now()
    links = load_links()
    speeds = fetch_speeds(now) if speeds is None else speeds
    result = {r: {"delay": 0.0, "notes": []} for r in ROUTES}
    if not (links and speeds):
        return result
    for route in ROUTES:
        kind = _group(route)[0]
        st = transit.route_stations(ROUTES[route]["route_id"])
        pts = waypoints(route)
        total = 0.0
        for i, q in enumerate(pts):
            ids = [l for l, v in links.items() if l in speeds and any(p["route"] == route and p["seq"] == q for p in v["points"])]
            if not ids:
                continue
            cur = sorted(speeds[l] for l in ids)[len(ids) // 2]
            usual = [u for u in (usual_speed(l, now) for l in ids) if u]
            base = sorted(usual)[len(usual) // 2] if usual else None
            slow = (cur < base * SLOW_RATIO and base - cur >= SLOW_GAP) if base else cur < NO_HISTORY_KMH
            if not slow:
                continue
            base = base or FREE_KMH[kind]
            nxt = st[pts[i + 1]] if i + 1 < len(pts) else st[transit.alight_candidates(route)[0]]
            km = max(_km(st[q], nxt), 0.5)
            total += km / max(cur, 5) * 60 - km / base * 60
            road = links[ids[0]]["road"]
            result[route]["notes"].append(
                f"[도로 속도] {road} {st[q]['name'].replace('(경유)', '').replace('(미정차)', '')} 부근 시속 {cur:.0f}km (평소 {base:.0f}km)")
        result[route]["delay"] = float(min(round(total, 1), MAX_DELAY))
    return result


if __name__ == "__main__":
    if "--build" in sys.argv:
        build_links()
    else:
        sp = fetch_speeds()
        print(f"구간 {len(sp)}개 속도 받음")
        for r, v in detect(speeds=sp).items():
            print(f"{r}번: " + (f"+{v['delay']:.0f}분" if v["delay"] else "정체 없음"))
            for n in v["notes"]:
                print("   ", n)
