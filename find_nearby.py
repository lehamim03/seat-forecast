# ==========================================================
#  주변 정류장 · 서울행 광역버스 찾기
#  실행:  python find_nearby.py
#  - 경기데이터드림 '버스정류소 현황'(BusStation)으로 정류장 좌표를 받고
#  - 공공데이터포털 '버스도착정보'로 각 정류장에 서는 노선을 확인합니다.
#  결과는 nearby_stops.json 에도 저장됩니다.
# ==========================================================
import json
import math

import requests

from api_key import SERVICE_KEY, GG_KEY

CENTER_STATION_ID = "203000063"   # 기준 정류장: 삼성1차아파트
SIGUN_NM = "수원시"               # 기준 정류장이 있는 시·군
RADIUS_M = 700                    # 이 거리(직선) 안의 정류장만 조사
WALK_M_PER_MIN = 75               # 걷는 속도 (분당 75m)
ROUTE_TYPES = {"11", "12", "14"}  # 직행좌석형 · 좌석형 · 광역급행(M버스)
SEOUL_WORDS = ("서울", "강남", "사당", "잠실", "삼성서초", "양재", "광화문")

# 경기데이터드림 서버는 브라우저가 아닌 요청을 차단해서 User-Agent를 붙입니다
UA = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    "(KHTML, like Gecko) Chrome/128.0 Safari/537.36"}


def fetch_stops():
    """시·군의 모든 정류장(이름, ID, 좌표)을 받아온다."""
    stops, page = [], 1
    while True:
        res = requests.get("https://openapi.gg.go.kr/BusStation", headers=UA, timeout=20, params={
            "KEY": GG_KEY, "Type": "json", "pIndex": page, "pSize": 1000, "SIGUN_NM": SIGUN_NM})
        data = res.json()
        if "BusStation" not in data:   # 더 이상 데이터 없음 (INFO-200) 또는 오류
            if page == 1:
                print("정류장 조회 실패:", data)
            return stops
        stops += data["BusStation"][1]["row"]
        page += 1


def distance_m(a, b):
    dx = (float(a["WGS84_LOGT"]) - float(b["WGS84_LOGT"])) * 88_400   # 위도 37도 부근 경도 1도 ≈ 88.4km
    dy = (float(a["WGS84_LAT"]) - float(b["WGS84_LAT"])) * 111_000
    return math.hypot(dx, dy)


def routes_at(station_id):
    """정류장에 서는 노선 목록 (버스도착정보 API)."""
    res = requests.get("https://apis.data.go.kr/6410000/busarrivalservice/v2/getBusArrivalListv2",
                       params={"serviceKey": SERVICE_KEY, "stationId": station_id, "format": "json"}, timeout=10)
    try:
        items = res.json()["response"]["msgBody"]["busArrivalList"]
    except Exception:
        return []
    return items if isinstance(items, list) else [items]


def main():
    stops = fetch_stops()
    center = next((s for s in stops if s["STATION_ID"] == CENTER_STATION_ID), None)
    if center is None:
        print("기준 정류장을 찾지 못했습니다. CENTER_STATION_ID 와 SIGUN_NM 을 확인하세요.")
        return
    near = sorted((distance_m(center, s), s) for s in stops
                  if s["WGS84_LAT"] and distance_m(center, s) <= RADIUS_M)
    print(f"기준: {center['STATION_NM_INFO']} — 반경 {RADIUS_M}m 안 정류장 {len(near)}곳 조사\n")

    result = []
    for d, s in near:
        seoul = [r for r in routes_at(s["STATION_ID"])
                 if str(r.get("routeTypeCd")) in ROUTE_TYPES
                 and any(w in str(r.get("routeDestName")) for w in SEOUL_WORDS)]
        if not seoul:
            continue
        walk = round(d * 1.3 / WALK_M_PER_MIN)   # 실제 걷는 길은 직선보다 약 1.3배
        result.append({
            "station_id": s["STATION_ID"], "name": s["STATION_NM_INFO"], "mobile_no": s["STATION_MANAGE_NO"],
            "lat": s["WGS84_LAT"], "lng": s["WGS84_LOGT"], "dist_m": int(d), "walk_min": walk,
            "routes": [{"name": str(r["routeName"]), "route_id": str(r["routeId"]),
                        "board_seq": r.get("staOrder"), "dest": r.get("routeDestName")} for r in seoul],
        })
        print(f"도보 {walk:2d}분  {s['STATION_NM_INFO']}({s['STATION_MANAGE_NO']})  id={s['STATION_ID']}")
        for r in seoul:
            print(f"          {str(r['routeName']):>10}  {r.get('staOrder')}번째 정류장  → {r.get('routeDestName')}")

    with open("nearby_stops.json", "w", encoding="utf-8") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    print("\n저장 완료: nearby_stops.json")


if __name__ == "__main__":
    main()
