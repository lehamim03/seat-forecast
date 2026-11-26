# ==========================================================
#  가상 분산 시험용 수집 — 우리 경로가 아닌 경기도 다른 지역 서울행 버스의 출근 시간 좌석 기록
#  실행:  python collect_test.py                 (지금부터 END 까지, 평일만)
#         python collect_test.py --wait          (START 전이면 기다렸다가 시작 — GitHub Actions 용)
#         python collect_test.py --once          (한 번만 조회 · 연결 확인)
#  저장:  data_test/test_seats_YYYY-MM-DD.csv  (data/ 와 같은 열 — generalize.py · field_test.py 가 사용)
#
#  왜: 우리 데이터는 집 → 국민대 한 경로뿐이라, 학습에 전혀 없던 지역 · 노선에서도
#      탑승 확률이 맞는지 보려면 그 노선들의 실제 기록이 필요하다. 앱이 보여 줄 확률은
#      (지금 좌석 · 남은 정류장 · 시각)으로 정해지므로, 기록만 있으면 나중에 똑같이 다시 계산해 채점할 수 있다.
#  하루 호출: 노선 10개 × 1분 간격 × 220분 ≈ 2,200회 (기존 수집 약 4,600회와 합쳐 한도 10,000회 안)
# ==========================================================
import csv
import os
import sys
import time
from datetime import datetime, timedelta, timezone

import requests

try:
    from api_key import SERVICE_KEY
except ImportError:
    SERVICE_KEY = os.environ["SERVICE_KEY"]

# 노선 이름 → (노선 ID, 지역) — anywhere.py 로 2026-11-25 낮에 찾은 서울행 좌석 · 광역버스
TEST_ROUTES = {
    "M4102": ("234001159", "분당 서현·정자 → 숭례문"),
    "8100": ("234000878", "분당 정자 → 을지로"),
    "1000": ("219000013", "고양 일산 → 숭례문"),
    "9600": ("234001244", "고양 화정 → 강남"),
    "1200": ("222000078", "남양주 도농 → 잠실"),
    "1330-4": ("239000111", "남양주·구리 → 청량리"),
    "9301": ("234000309", "하남 미사 → 서울역"),
    "M6117": ("232000071", "김포 장기 → 서울역"),
    "M4130": ("234001577", "화성 동탄 → 서울역"),
    "6900": ("234000027", "용인 수지 → 잠실"),
}
START, END = (6, 0), (9, 40)     # 평일 출근 시간 (한국 시간)
INTERVAL_SEC = 60
OUT_DIR = "data_test"
COLUMNS = ["collected_at", "date", "weekday", "time", "route_name", "route_id",
           "plate_no", "station_seq", "station_id", "state", "remain_seat", "crowded"]
BASE = "https://apis.data.go.kr/6410000"
KST = timezone(timedelta(hours=9))


def now_kst():
    return datetime.now(KST).replace(tzinfo=None)


def call(path, key, **params):
    params.update(serviceKey=SERVICE_KEY, format="json")
    try:
        data = requests.get(f"{BASE}/{path}", params=params, timeout=10).json()["response"]
    except Exception:
        return []
    items = (data.get("msgBody") or {}).get(key) or []
    return items if isinstance(items, list) else [items]


_TURN = {}


def turn_seq(route_id):
    """회차 정류장 순번 — 이 뒤는 서울에서 돌아오는 방향이라 기록하지 않음"""
    if route_id not in _TURN:
        st = call("busrouteservice/v2/getBusRouteStationListv2", "busRouteStationList", routeId=route_id)
        turns = [int(s["stationSeq"]) for s in st if s.get("turnYn") == "Y"]
        _TURN[route_id] = min(turns) if turns else 999
    return _TURN[route_id]


def collect_once(now):
    rows = []
    for name, (rid, _) in TEST_ROUTES.items():
        turn = turn_seq(rid)
        for it in call("buslocationservice/v2/getBusLocationListv2", "busLocationList", routeId=rid):
            seq = int(it.get("stationSeq", 999))
            if seq > turn:
                continue
            rows.append({"collected_at": now.strftime("%Y-%m-%d %H:%M:%S"), "date": now.strftime("%Y-%m-%d"),
                         "weekday": now.weekday(), "time": now.strftime("%H:%M"), "route_name": name,
                         "route_id": rid, "plate_no": it.get("plateNo"), "station_seq": seq,
                         "station_id": it.get("stationId"), "state": it.get("stateCd"),
                         "remain_seat": it.get("remainSeatCnt"), "crowded": it.get("crowded")})
    if rows:
        os.makedirs(OUT_DIR, exist_ok=True)
        path = os.path.join(OUT_DIR, f"test_seats_{now:%Y-%m-%d}.csv")
        new = not os.path.exists(path)
        with open(path, "a", newline="", encoding="utf-8-sig") as f:
            w = csv.DictWriter(f, fieldnames=COLUMNS)
            if new:
                w.writeheader()
            w.writerows(rows)
    by_route = {}
    for r in rows:
        by_route[r["route_name"]] = by_route.get(r["route_name"], 0) + 1
    print(f"[{now:%H:%M:%S}] {len(rows)}대 · " + ", ".join(f"{k} {v}" for k, v in by_route.items()), flush=True)
    return rows


def hm(t):
    return t.hour * 60 + t.minute


def main():
    if "--once" in sys.argv:
        sys.exit(0 if collect_once(now_kst()) else 1)
    now = now_kst()
    if now.weekday() >= 5:
        print("주말이라 수집하지 않습니다.")
        return
    if "--wait" in sys.argv:
        while hm(now_kst()) < START[0] * 60 + START[1]:
            time.sleep(20)
    print(f"수집: 노선 {len(TEST_ROUTES)}개, {END[0]:02d}:{END[1]:02d} 까지 {INTERVAL_SEC}초 간격", flush=True)
    while hm(now_kst()) < END[0] * 60 + END[1]:
        began = time.time()
        collect_once(now_kst())
        time.sleep(max(0, INTERVAL_SEC - (time.time() - began)))


if __name__ == "__main__":
    main()
