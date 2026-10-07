# ==========================================================
#  광역버스 잔여좌석 자동 수집기 ('경기도_버스위치정보 조회' API 사용)
#  실행:  python collect.py                    (계속 실행, 멈추려면 Ctrl + C)
#         python collect.py --max-minutes 340  (340분 뒤 스스로 종료 — GitHub Actions 이어달리기용)
#         python collect.py --estimate         (하루 예상 API 호출 수만 계산)
#  첫차 전(05:00)부터 막차가 서울에 닿을 때(01:30)까지, 노선별 운행 시간 안에서만 수집합니다.
#  조회 간격과 하루 호출 한도는 config.py 에서 설정합니다 (현재 1분 간격, 한도 10,000회).
#  시간은 어디서 실행하든 한국 시간(KST) 기준입니다.
# ==========================================================
import csv
import os
import sys
import time
from datetime import datetime, timedelta, timezone

import requests

from config import (SERVICE_KEY, ROUTES, STOPS, INTERVAL_PEAK_SEC, INTERVAL_OFFPEAK_SEC,
                    INTERVAL_WEEKEND_SEC, ROUTE_HOURS, DAILY_CALL_LIMIT,
                    PEAK_HOURS, COLLECT_START, COLLECT_END, CSV_FILE)

URL = "https://apis.data.go.kr/6410000/buslocationservice/v2/getBusLocationListv2"
KST = timezone(timedelta(hours=9))   # GitHub 서버(UTC)에서 돌려도 한국 시간으로 판단


def now_kst():
    return datetime.now(KST).replace(tzinfo=None)


def _within(now, start, end):
    """now 가 start~end 사이인지 (자정을 넘어가는 구간 처리)."""
    hm = now.hour * 60 + now.minute
    a, b = start[0] * 60 + start[1], end[0] * 60 + end[1]
    return a <= hm or hm < b if b < a else a <= hm < b


def in_service(now):
    """첫차 전 ~ 막차 도착 시간대인지."""
    return _within(now, COLLECT_START, COLLECT_END)


def route_active(route, now):
    """노선별 운행 시간(첫차 10분 전 ~ 막차가 서울에 닿을 무렵) 안인지."""
    start, end = ROUTE_HOURS.get(route, (COLLECT_START, COLLECT_END))
    return _within(now, start, end)


def _rest_day(now):
    """토·일 또는 공휴일(대체공휴일 포함). 자정~03:00 은 전날 기준."""
    day = (now - timedelta(hours=3)).date()
    if day.weekday() >= 5:
        return True
    try:
        import daytype
        return daytype.day_info(day)["day_type"] == 2
    except Exception:
        return False


def interval(now):
    """평일 출퇴근 2분, 평일 그 외 30분, 주말·공휴일 6분 (config 값)."""
    if _rest_day(now):
        return INTERVAL_WEEKEND_SEC
    hm = now.hour * 60 + now.minute
    peak = any(a <= hm < b for a, b in PEAK_HOURS)
    return INTERVAL_PEAK_SEC if peak else INTERVAL_OFFPEAK_SEC


def estimate(day):
    """그날(05:00 ~ 다음 날 01:30) 예상 API 호출 수."""
    t = datetime(day.year, day.month, day.day, COLLECT_START[0], COLLECT_START[1])
    calls = 0
    while in_service(t):
        calls += sum(route_active(r, t) for r in ROUTES)
        t += timedelta(seconds=interval(t))
    return calls

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


def to_rows(route_name, route_id, dest_seq, items, now):
    """서울 방향(출발점 ~ 서울 도착 지점)을 달리는 버스를 CSV 한 줄씩으로 변환.
    서울 도착까지 기록해야 정류장 → 서울 이동시간도 데이터로 잴 수 있다."""
    rows = []
    for it in items:
        seq = int(it.get("stationSeq", 999))
        if seq > dest_seq:   # 서울에서 돌아오는 방향은 제외
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
        if not route_active(name, now):   # 첫차 전 · 막차 후에는 호출하지 않음 (하루 한도 절약)
            continue
        rows += to_rows(name, r["route_id"], r["dest_seq"], fetch_buses(r["route_id"]), now)
    if rows:
        save(rows)

    # 화면에는 후보 정류장에 다가오는 버스만 "다음 후보 정류장까지 몇 정거장 전"으로 표시
    def approaching(r):
        ahead = sorted((seq, sid) for sid, seq in ROUTES[r["route_name"]]["stops"].items()
                       if seq >= r["station_seq"])
        if not ahead:
            return None   # 후보 정류장을 모두 지나감
        seq, sid = ahead[0]
        n = seq - r["station_seq"]
        return f"{r['route_name']} {STOPS[sid]['name']} {n}정거장전(좌석{r['remain_seat']})" if n else                f"{r['route_name']} {STOPS[sid]['name']} 도착(좌석{r['remain_seat']})"

    near = [a for a in map(approaching, sorted(rows, key=lambda r: (r["route_name"], -r["station_seq"]))) if a]
    print(f"[{now:%H:%M:%S}] 기록 {len(rows)}대 | " + (", ".join(near) or "후보 정류장에 다가오는 버스 없음"))


def main():
    if "--estimate" in sys.argv:
        today = now_kst().date()
        for d in [today + timedelta(days=i) for i in range(7)]:
            n = estimate(d)
            print(f"{d} ({'월화수목금토일'[d.weekday()]}) 예상 호출 {n}회"
                  + ("  ⚠ 한도 초과" if n > DAILY_CALL_LIMIT else f"  (여유 {DAILY_CALL_LIMIT - n}회)"))
        return
    today_calls = estimate((now_kst() - timedelta(hours=3)).date())
    print(f"오늘 예상 호출 {today_calls}회 / 하루 한도 {DAILY_CALL_LIMIT}회"
          + ("  ⚠ 한도를 넘습니다 — config.py 의 간격을 늘리세요" if today_calls > DAILY_CALL_LIMIT else ""))
    limit = None
    if "--max-minutes" in sys.argv:
        limit = int(sys.argv[sys.argv.index("--max-minutes") + 1])
    began = time.time()
    print(f"수집 시작: 노선 {list(ROUTES)}, {COLLECT_START[0]:02d}:{COLLECT_START[1]:02d}~"
          f"{COLLECT_END[0]:02d}:{COLLECT_END[1]:02d}(한국 시간), "
          f"평일 출퇴근 {INTERVAL_PEAK_SEC // 60}분 · 평일 그 외 {INTERVAL_OFFPEAK_SEC // 60}분 · "
          f"주말·공휴일 {INTERVAL_WEEKEND_SEC // 60}분 간격")
    print(f"{limit}분 뒤 종료합니다\n" if limit else "멈추려면 Ctrl + C\n")
    while True:
        now = now_kst()
        if limit and time.time() - began >= limit * 60:
            print(f"[{now:%H:%M:%S}] {limit}분 수집 완료 — 종료 (다음 실행이 이어받음)")
            return
        if in_service(now):
            try:
                collect_once(now)
            except Exception as e:
                # 인터넷 끊김 등 — 멈추지 말고 다음 차례에 다시 시도
                print(f"[{now:%H:%M:%S}] 오류: {e}")
            wait = interval(now)
            if limit:   # 끝낼 시간이 가까우면 그만큼만 기다림
                wait = max(1, min(wait, limit * 60 - (time.time() - began)))
            time.sleep(wait)
        else:
            print(f"[{now:%H:%M:%S}] 운행 시간 아님 (01:30~05:00) — 대기 중", end="\r")
            time.sleep(60)


if __name__ == "__main__":
    main()
