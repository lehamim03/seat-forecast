# ==========================================================
#  광역버스 잔여좌석 자동 수집기 ('경기도_버스위치정보 조회' API 사용)
#  실행:  python collect.py      (멈추려면 Ctrl + C)
#  설정은 config.py 에서 바꾸세요.
# ==========================================================
import csv
import os
import time
from datetime import datetime

import requests

from config import (SERVICE_KEY, ROUTES, AFTER_SEQ, INTERVAL_SEC,
                    START_HOUR, END_HOUR, CSV_FILE)

URL = "https://apis.data.go.kr/6410000/buslocationservice/v2/getBusLocationListv2"

COLUMNS = ["collected_at", "date", "weekday", "time", "route_name", "route_id",
           "plate_no", "station_seq", "station_id", "state", "remain_seat",
           "crowded"]


def fetch_buses(route_id):
    """노선 위를 달리는 모든 버스의 현재 위치와 잔여좌석을 받아온다."""
    params = {"serviceKey": SERVICE_KEY, "routeId": route_id, "format": "json"}
    res = requests.get(URL, params=params, timeout=10)
    try:
        data = res.json()["response"]
    except Exception:
        # 키 오류 등은 다른 형식으로 오는 경우가 많다
        print("  ! 응답 해석 실패. 서버 원문:", res.text[:300])
        return []

    code = str(data.get("msgHeader", {}).get("resultCode"))
    if code == "4":   # 결과 없음 = 지금 운행 중인 버스가 없음
        return []
    if code != "0":
        print("  ! API 결과 코드:", code, data.get("msgHeader", {}).get("resultMessage"))
        return []

    items = (data.get("msgBody") or {}).get("busLocationList") or []
    return items if isinstance(items, list) else [items]


def to_rows(route_name, route_id, board_seq, items, now):
    """출발점 ~ 탑승 정류소 조금 뒤까지 있는 버스만 CSV 한 줄씩으로 변환."""
    rows = []
    for it in items:
        seq = int(it.get("stationSeq", 999))
        if seq > board_seq + AFTER_SEQ:
            continue
        rows.append({
            "collected_at": now.strftime("%Y-%m-%d %H:%M:%S"),
            "date": now.strftime("%Y-%m-%d"),
            "weekday": now.weekday(),              # 0=월 ... 6=일
            "time": now.strftime("%H:%M"),
            "route_name": route_name,
            "route_id": route_id,
            "plate_no": it.get("plateNo"),
            "station_seq": seq,                    # 노선의 몇 번째 정류소에 있는지
            "station_id": it.get("stationId"),
            "state": it.get("stateCd"),            # 0=교차로통과 1=정류소도착 2=정류소출발
            "remain_seat": it.get("remainSeatCnt"),  # 잔여 좌석 (-1 = 정보없음)
            "crowded": it.get("crowded"),          # 혼잡도 (1=여유 ~ 4=매우혼잡)
        })
    return rows


def save(rows):
    new_file = not os.path.exists(CSV_FILE)
    # utf-8-sig : 엑셀에서 열어도 한글이 안 깨짐
    with open(CSV_FILE, "a", newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=COLUMNS)
        if new_file:
            w.writeheader()
        w.writerows(rows)


def collect_once(now):
    rows = []
    for name, r in ROUTES.items():
        rows += to_rows(name, r["route_id"], r["board_seq"], fetch_buses(r["route_id"]), now)
    if rows:
        save(rows)
    # 화면에는 탑승 정류소 기준 몇 정거장 전인지로 표시
    def where(r):
        n = ROUTES[r["route_name"]]["board_seq"] - r["station_seq"]
        return f"{n}정거장전" if n > 0 else ("도착" if n == 0 else "지나감")

    near = sorted(rows, key=lambda r: (r["route_name"], r["station_seq"]))
    summary = ", ".join(f"{r['route_name']} {where(r)}(좌석{r['remain_seat']})"
                        for r in near) or "대상 버스 없음"
    print(f"[{now:%H:%M:%S}] {summary}")


def main():
    print(f"수집 시작: 노선 {list(ROUTES)}, {START_HOUR}시~{END_HOUR}시, {INTERVAL_SEC}초 간격")
    print("멈추려면 Ctrl + C\n")
    while True:
        now = datetime.now()
        if START_HOUR <= now.hour < END_HOUR:
            try:
                collect_once(now)
            except Exception as e:
                # 인터넷 끊김 등 — 멈추지 말고 다음 차례에 다시 시도
                print(f"[{now:%H:%M:%S}] 오류: {e}")
        else:
            print(f"[{now:%H:%M:%S}] 수집 시간이 아님 — 대기 중", end="\r")
        time.sleep(INTERVAL_SEC)


if __name__ == "__main__":
    main()
