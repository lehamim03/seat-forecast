# ==========================================================
#  날짜 종류 판단 — 평일 / 토요일 / 일요일·공휴일, 출퇴근 시간대 여부
#  실행:  python daytype.py            (오늘)
#         python daytype.py 2026-10-05 (특정 날짜)
#
#  공휴일은 공공데이터포털 '한국천문연구원_특일 정보'(대체공휴일 포함)로 받고,
#  API를 쓸 수 없으면 아래 2026년 목록을 씁니다. 결과는 daytype_cache.json 에 연도별로 저장.
#  평일 출퇴근 시간대에는 승객이 많아 좌석이 빨리 줄고, 주말·공휴일엔 한산하지만
#  배차간격이 길어집니다 — 그 정도를 config.DAY_FACTORS 로 반영합니다.
# ==========================================================
import json
import os
import sys
from datetime import date, datetime

import requests

from config import SERVICE_KEY, DAY_FACTORS, PEAK_HOURS

CACHE = "daytype_cache.json"

# API를 못 쓸 때 쓰는 2026년 공휴일 (대체공휴일 포함 — 정부 발표로 바뀔 수 있음)
FALLBACK = {
    "2026-01-01": "신정", "2026-02-16": "설날 연휴", "2026-02-17": "설날", "2026-02-18": "설날 연휴",
    "2026-03-01": "삼일절", "2026-03-02": "대체공휴일(삼일절)", "2026-05-05": "어린이날",
    "2026-05-24": "부처님오신날", "2026-05-25": "대체공휴일(부처님오신날)", "2026-06-03": "전국동시지방선거",
    "2026-06-06": "현충일", "2026-08-15": "광복절", "2026-08-17": "대체공휴일(광복절)",
    "2026-09-24": "추석 연휴", "2026-09-25": "추석", "2026-09-26": "추석 연휴",
    "2026-10-03": "개천절", "2026-10-05": "대체공휴일(개천절)", "2026-10-09": "한글날", "2026-12-25": "성탄절",
}


def holidays(year):
    """{'YYYY-MM-DD': 이름} — API 결과를 연도별로 저장해 두고 씀."""
    cache = {}
    if os.path.exists(CACHE):
        with open(CACHE, encoding="utf-8") as f:
            cache = json.load(f)
    if str(year) in cache:
        return cache[str(year)]
    days = {}
    try:
        for month in range(1, 13):
            res = requests.get("https://apis.data.go.kr/B090041/openapi/service/SpcdeInfoService/getRestDeInfo",
                               timeout=10, params={"serviceKey": SERVICE_KEY, "solYear": year,
                                                   "solMonth": f"{month:02d}", "_type": "json", "numOfRows": 50})
            items = res.json()["response"]["body"]["items"]
            items = (items or {}).get("item", []) if isinstance(items, dict) else []
            for it in items if isinstance(items, list) else [items]:
                if it.get("isHoliday") == "Y":
                    d = str(it["locdate"])
                    days[f"{d[:4]}-{d[4:6]}-{d[6:]}"] = it["dateName"]
        cache[str(year)] = days
        with open(CACHE, "w", encoding="utf-8") as f:
            json.dump(cache, f, ensure_ascii=False, indent=1)
        return days
    except Exception:
        return {k: v for k, v in FALLBACK.items() if k.startswith(str(year))}


def day_info(when):
    """날짜(·시각)의 종류.
    day_type: 0 = 평일, 1 = 토요일, 2 = 일요일·공휴일 / peak: 평일 출퇴근 시간대 여부"""
    d = when.date() if isinstance(when, datetime) else when
    name = holidays(d.year).get(d.isoformat())
    if name:
        kind, day_type = "공휴일", 2
    elif d.weekday() == 6:
        kind, day_type = "일요일", 2
    elif d.weekday() == 5:
        kind, day_type = "토요일", 1
    else:
        kind, day_type = "평일", 0
    peak = False
    if isinstance(when, datetime) and day_type == 0:
        hm = when.hour * 60 + when.minute
        peak = any(a <= hm < b for a, b in PEAK_HOURS)
    return {"date": d, "kind": kind, "name": name, "day_type": day_type, "peak": peak}


def factors(when):
    """평소(평일 출퇴근) 대비 좌석 감소 · 배차간격 배율 + 설명 문구."""
    info = day_info(when)
    key = "평일 출퇴근" if info["peak"] else ("평일" if info["day_type"] == 0 else info["kind"])
    key = "일요일·공휴일" if info["day_type"] == 2 else key
    f = dict(DAY_FACTORS[key])
    label = info["name"] or info["kind"]
    if info["peak"]:
        label += " 출퇴근 시간대"
    f.update(label=label, key=key, **info)
    return f


if __name__ == "__main__":
    when = datetime.strptime(sys.argv[1], "%Y-%m-%d") if len(sys.argv) > 1 else datetime.now()
    f = factors(when)
    day = "월화수목금토일"[f["date"].weekday()]
    print(f"{f['date']}({day}) — {f['label']}  [{f['key']}]")
    print(f"  좌석 줄어드는 속도 ×{f['drop']}, 배차간격 ×{f['headway']}")
