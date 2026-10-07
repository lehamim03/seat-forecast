# ==========================================================
#  지역행사 반영 — 축제 · 공연 · 경기 때문에 생기는 승객 쏠림과 정체
#  실행:  python events.py     (지금 영향을 주는 행사와 노선별 영향 출력)
#
#  행사 정보: ① 한국관광공사 TourAPI 축제·행사(경기·서울, 하루 한 번 조회해 저장)
#            ② config.LOCAL_EVENTS 에 직접 넣은 행사 (경기·공연처럼 축제 정보에 없는 것)
#  영향 판단: 행사장 EVENT_NEAR_KM 안의 정류장이
#   - 내 정류장보다 앞(출발점 쪽)이면 → 거기서 승객이 몰려 내 정류장 도착 때 좌석이 줄어듦
#   - 내 정류장 뒤(서울 쪽)면        → 행사장 주변 정체로 이동시간이 늘어남
# ==========================================================
import json
import math
import os
from datetime import date, datetime, timedelta

import requests

import transit
from config import (SERVICE_KEY, ROUTES, MY_STOP, EVENT_NEAR_KM, EVENT_EXTRA_SEATS,
                    EVENT_DELAY_MIN, LOCAL_EVENTS)

CACHE = "events_cache.json"
USE_TOURAPI = True
FESTIVAL_HOURS = ("10:00", "21:00")   # 축제 정보엔 시간이 없어 이 시간대에 열린다고 봄
AREAS = {"11": "서울", "41": "경기"}   # 법정동 시도 코드 (TourAPI KorService2)
MAX_EVENT_DAYS = 7     # 이보다 오래 이어지는 행사(상시 공연·의식 등)는 평소 교통에 이미 포함된 것으로 보고 제외
MAX_EVENT_DELAY = 10   # 노선당 행사 정체 지연 상한(분)
MAX_EVENT_SEATS = 20   # 정류장당 행사 추가 승객 상한


def _km(ax, ay, bx, by):
    return math.hypot((ax - bx) * 88.4, (ay - by) * 111.0)


def festivals(day):
    """TourAPI 축제·행사 중 day 에 열리는 것 (경기·서울)."""
    if not USE_TOURAPI:
        return []
    cache = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            cache = json.load(f)
    if cache.get("date") == day.isoformat() and cache.get("max_days") == MAX_EVENT_DAYS:
        return cache["items"]
    items = []
    try:
        for area in AREAS:
            res = requests.get("https://apis.data.go.kr/B551011/KorService2/searchFestival2", timeout=15, params={
                "serviceKey": SERVICE_KEY, "MobileOS": "ETC", "MobileApp": "seatforecast", "_type": "json",
                "eventStartDate": (day - timedelta(days=90)).strftime("%Y%m%d"),
                "lDongRegnCd": area, "numOfRows": 500})
            body = res.json()["response"]["body"]["items"]
            rows = body.get("item", []) if isinstance(body, dict) else []
            for it in rows if isinstance(rows, list) else [rows]:
                start, end = it.get("eventstartdate", ""), it.get("eventenddate", "")
                if not (start <= day.strftime("%Y%m%d") <= end and it.get("mapx")):
                    continue
                days = (datetime.strptime(end, "%Y%m%d") - datetime.strptime(start, "%Y%m%d")).days + 1
                if days > MAX_EVENT_DAYS:   # 몇 주~몇 달씩 매일 열리는 상시 행사 제외
                    continue
                items.append({"name": it["title"], "date": day.isoformat(), "start": FESTIVAL_HOURS[0],
                              "end": FESTIVAL_HOURS[1], "x": float(it["mapx"]), "y": float(it["mapy"]),
                              "size": "중", "days": days, "source": "관광공사"})
    except Exception:
        return []   # 활용신청 전이거나 조회 실패 — 직접 넣은 행사만 사용
    with open(CACHE, "w", encoding="utf-8") as f:
        json.dump({"date": day.isoformat(), "max_days": MAX_EVENT_DAYS, "items": items}, f, ensure_ascii=False, indent=1)
    return items


def active_events(now, extra=None):
    """지금(행사 앞뒤 1시간 포함) 영향을 주는 행사 목록."""
    out = []
    for ev in festivals(now.date()) + list(LOCAL_EVENTS) + list(extra or []):
        if ev["date"] != now.date().isoformat():
            continue
        start = datetime.strptime(f"{ev['date']} {ev['start']}", "%Y-%m-%d %H:%M") - timedelta(hours=1)
        end = datetime.strptime(f"{ev['date']} {ev['end']}", "%Y-%m-%d %H:%M") + timedelta(hours=1)
        if start <= now <= end:
            out.append(ev)
    return out


def impact(now=None, extra=None):
    """노선별 행사 영향 → {노선: {"extra": {순번: 추가 승객}, "delay": 분, "notes": [...]}}"""
    now = now or datetime.now()
    result = {r: {"extra": {}, "delay": 0.0, "notes": []} for r in ROUTES}
    jammed = set()   # (노선, 정류장) — 같은 곳 주변 행사가 여러 개여도 정체는 한 번만
    for ev in active_events(now, extra):
        size = ev.get("size", "중")
        for route, cfg in ROUTES.items():
            st = transit.route_stations(cfg["route_id"])
            near = [(s, v) for s, v in sorted(st.items())
                    if s <= transit.alight_seq(route) and _km(ev["x"], ev["y"], v["x"], v["y"]) <= EVENT_NEAR_KM]
            if not near:
                continue
            my_seq = cfg["stops"].get(MY_STOP, max(cfg["stops"].values()))
            board = [(s, v) for s, v in near if s < max(cfg["stops"].values())]   # 후보 정류장보다 앞
            ride = [(s, v) for s, v in near if s > my_seq]                         # 서울 쪽 이동 구간
            if board:
                s, v = board[0]
                result[route]["extra"][s] = min(result[route]["extra"].get(s, 0) + EVENT_EXTRA_SEATS[size],
                                                MAX_EVENT_SEATS)
                result[route]["notes"].append(
                    f"{ev['name']} — {v['name'].replace('(경유)', '')}에서 승객 약 {EVENT_EXTRA_SEATS[size]}명 추가 예상")
            if ride and (route, ride[0][0]) not in jammed:
                jammed.add((route, ride[0][0]))
                result[route]["delay"] = min(result[route]["delay"] + EVENT_DELAY_MIN[size], MAX_EVENT_DELAY)
                result[route]["notes"].append(
                    f"{ev['name']} — {ride[0][1]['name'].replace('(경유)', '')} 주변 정체 (+{EVENT_DELAY_MIN[size]}분)")
    return result


if __name__ == "__main__":
    now = datetime.now()
    evs = active_events(now)
    print(f"[{now:%m/%d %H:%M}] 지금 영향을 주는 행사 {len(evs)}건"
          + ("" if USE_TOURAPI and os.path.exists(CACHE) else "  (관광공사 축제 정보: 활용신청 전이면 직접 넣은 행사만)"))
    for ev in evs:
        print(f"  - {ev['name']} ({ev['start']}~{ev['end']}, 규모 {ev.get('size', '중')})")
    for route, r in impact(now).items():
        if r["notes"]:
            print(f"{route}번:")
            for n in r["notes"]:
                print(f"   ⚠ {n}")
