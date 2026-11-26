# ==========================================================
#  어디서나 모드 — 우리 집 경로(config.py)가 아닌 경기도 아무 정류장에서도 서울행 버스 탑승 확률 계산
#  실행:  python anywhere.py 37.3850 127.1233        (위도 경도 → 주변 정류장 · 서울행 버스 · 탑승 확률)
#
#  · 주변 정류장: 경기데이터드림 '버스정류소 현황' 전체(약 3만 5천 곳)를 한 번 받아 gg_stations.json 에 저장
#  · 다가오는 버스: 정류장 도착정보 API — 차량마다 도착 예정(분) · 남은 정류장 수 · 지금 잔여 좌석
#  · 탑승 확률: 학습 데이터가 없는 노선이므로 노선 번호 없이 학습한 '공통 모델'(train.py 의 generic_model),
#    없으면 기존 공식 — generalize.py 로 처음 보는 노선 · 정류장에서 검증한 방식
# ==========================================================
import json
import math
import os
import sys
from datetime import datetime

import requests

from api_key import GG_KEY, SERVICE_KEY

STATIONS_FILE = "gg_stations.json"
RADIUS_M = 500                    # 이 거리(직선) 안의 정류장
WALK_M_PER_MIN = 75               # 분당 75m, 실제 길은 직선의 1.3배
ROUTE_TYPES = {"11", "12", "14"}  # 직행좌석형 · 좌석형 · 광역급행(M버스) — 좌석 수가 나오는 버스
SEOUL_WORDS = ("서울", "강남", "사당", "잠실", "양재", "광화문", "종로", "을지로", "시청", "신논현", "역삼", "선릉",
               "삼성", "교대", "서초", "여의도", "영등포", "신촌", "홍대", "합정", "공덕", "청량리", "동대문", "구로",
               "신도림", "건대", "강변", "천호", "수서", "고속터미널", "용산", "숭례문", "남대문", "명동", "광장", "회기")
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    "(KHTML, like Gecko) Chrome/128.0 Safari/537.36"}
BASE = "https://apis.data.go.kr/6410000"


def stations():
    """경기도 버스정류소 전체 [{id, name, mobile_no, sigun, lat, lng}] — 처음 한 번 받아 저장 (약 35쪽)"""
    if os.path.exists(STATIONS_FILE):
        with open(STATIONS_FILE, encoding="utf-8") as f:
            return json.load(f)
    out, page = [], 1
    while True:
        data = requests.get("https://openapi.gg.go.kr/BusStation", headers=UA, timeout=30, params={
            "KEY": GG_KEY, "Type": "json", "pIndex": page, "pSize": 1000}).json()
        if "BusStation" not in data:
            break
        for s in data["BusStation"][1]["row"]:
            if s.get("WGS84_LAT") and s.get("WGS84_LOGT"):
                out.append({"id": str(s["STATION_ID"]), "name": s["STATION_NM_INFO"],
                            "mobile_no": str(s.get("STATION_MANAGE_NO") or ""), "sigun": s.get("SIGUN_NM"),
                            "lat": float(s["WGS84_LAT"]), "lng": float(s["WGS84_LOGT"])})
        page += 1
    if not out:
        raise RuntimeError("경기도 정류장 목록을 받지 못했습니다 (GG_KEY 확인)")
    with open(STATIONS_FILE, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False)
    return out


def dist_m(lat1, lng1, lat2, lng2):
    return math.hypot((lng1 - lng2) * 88_400, (lat1 - lat2) * 111_000)


def nearby(lat, lng, radius=RADIUS_M):
    """(거리 m, 정류장) 가까운 순"""
    cands = ((dist_m(lat, lng, s["lat"], s["lng"]), s) for s in stations()
             if abs(s["lat"] - lat) < 0.01 and abs(s["lng"] - lng) < 0.012)   # 대략 1km 상자 안만 계산
    return sorted(((d, s) for d, s in cands if d <= radius), key=lambda x: x[0])


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


def seoul_bound(a):
    return str(a.get("routeTypeCd")) in ROUTE_TYPES and any(w in str(a.get("routeDestName")) for w in SEOUL_WORDS)


def arrivals(station_id):
    """정류장에 다가오는 서울행 좌석 · 광역버스 — 노선마다 다음 차 2대 {order, plate, eta_min, n_stops, seats_now}"""
    out = []
    for a in get("busarrivalservice/v2/getBusArrivalListv2", stationId=station_id):
        if not seoul_bound(a):
            continue
        buses = []
        for n in ("1", "2"):
            eta = num(a.get(f"predictTime{n}"))
            if eta is None:
                continue
            seats = num(a.get(f"remainSeatCnt{n}"), -1)
            buses.append({"order": int(n), "plate": a.get(f"plateNo{n}"), "eta_min": eta,
                          "n_stops": int(num(a.get(f"locationNo{n}"), 0)),
                          "seats_now": None if seats < 0 else seats})
        out.append({"route": str(a.get("routeName")), "route_id": str(a.get("routeId")),
                    "dest": a.get("routeDestName"), "type": str(a.get("routeTypeCd")),
                    "stop_seq": int(num(a.get("staOrder"), 0)), "buses": buses})
    return out


def main():
    lat, lng = float(sys.argv[1]), float(sys.argv[2])
    near = nearby(lat, lng)
    print(f"반경 {RADIUS_M}m 정류장 {len(near)}곳")
    for d, s in near[:8]:
        arr = arrivals(s["id"])
        if not arr:
            continue
        walk = round(d * 1.3 / WALK_M_PER_MIN)
        print(f"\n{s['name']} ({s['mobile_no']}, {s['id']}) · 도보 {walk}분")
        for r in arr:
            info = " / ".join(f"{b['eta_min']:.0f}분 후 · {b['n_stops']}정거장 전 · "
                              + ("좌석 정보 없음" if b["seats_now"] is None else f"{b['seats_now']:.0f}석")
                              for b in r["buses"]) or "도착 예정 없음"
            print(f"  {r['route']:>8}번 → {r['dest']}  ({r['route_id']}, {r['stop_seq']}번째)  {info}")


if __name__ == "__main__":
    main()
