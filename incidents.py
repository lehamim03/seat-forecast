# ==========================================================
#  돌발상황 감지 — 앞서 가는 같은 노선 버스들의 최근 움직임으로 판단
#  실행:  python incidents.py      (지금 노선별 지연·정지·정류장 건너뜀 감지 결과 출력)
#
#  사고 · 출퇴근 정체 · 시위 등 원인이 무엇이든, 버스가 실제로 느려지거나 멈추거나
#  정류장을 건너뛰면 수집 데이터(bus_seats.csv, collect.py 가 1분마다 기록)에 드러납니다.
#   - 정체   : 최근 구간을 평소보다 훨씬 오래 걸려 지나감
#   - 정지   : 한 정류장 구간에 평소보다 훨씬 오래 머물러 있음
#   - 건너뜀 : 1~2분 사이에 정류장 순번이 3칸 이상 뛰면서 후보 정류장을 지나침 (우회 의심)
#  평소 이동 시간은 train.py 실측값(seg_min)이 있으면 그 값을, 없으면
#  정류장 사이 거리 ÷ 평균 속도(BASE_KMH)로 추정합니다.
# ==========================================================
import math
import os
from datetime import datetime, timedelta

import pandas as pd

import transit
from config import CSV_FILE, ROUTES, STOPS, MY_STOP

RECENT_MIN = 20        # 최근 몇 분의 기록을 볼지
BASE_KMH = 30          # 실측값이 없을 때 쓰는 버스 평균 속도 (정차 포함)
SLOW_RATIO = 1.8       # 평소보다 이 배 이상 오래 걸리면 '정체'
STUCK_MIN = 8          # 한 구간에 평소 시간 + 이 분 이상 머물면 '정지'
SKIP_JUMP = 3          # 2분 안에 순번이 이만큼 이상 뛰면 '건너뜀'


def _dist_km(a, b):
    dx = (a["x"] - b["x"]) * 88.4
    dy = (a["y"] - b["y"]) * 111.0
    return math.hypot(dx, dy)


def baseline_min(route, seq_from, seq_to, bundle, stations):
    """seq_from → seq_to 구간의 평소 이동 시간(분)."""
    seg = bundle.get("seg_min", {}) if bundle else {}
    total = 0.0
    for s in range(seq_from, seq_to):
        if (route, s) in seg:
            total += seg[(route, s)]
        elif s in stations and s + 1 in stations:
            total += max(_dist_km(stations[s], stations[s + 1]) / BASE_KMH * 60, 0.7)
        else:
            total += 2.0
    return total


def load_recent(now):
    """수집기가 기록한 최근 RECENT_MIN 분의 차량 위치."""
    if not os.path.exists(CSV_FILE):
        return pd.DataFrame()
    df = pd.read_csv(CSV_FILE, encoding="utf-8-sig", dtype={"route_name": str})
    df["collected_at"] = pd.to_datetime(df["collected_at"])
    return df[df["collected_at"] >= now - timedelta(minutes=RECENT_MIN)]


def detect(now=None, bundle=None, recent=None):
    """노선별 돌발 판단 → {노선: {"delay": 추가 지연(분), "notes": [...], "skip_stops": {정류장ID}}}"""
    now = now or datetime.now()
    recent = load_recent(now) if recent is None else recent
    result = {r: {"delay": 0.0, "notes": [], "skip_stops": set(), "probes": 0} for r in ROUTES}
    # 수집기가 멈춰 기록이 3분 넘게 끊겼으면 판단하지 않음 (오래된 기록으로 '정지'라고 오판 방지)
    if recent.empty or now - recent["collected_at"].max() > timedelta(minutes=3):
        return result
    now = recent["collected_at"].max()

    for route, cfg in ROUTES.items():
        stations = transit.route_stations(cfg["route_id"])
        my_seq = cfg["stops"].get(MY_STOP, min(cfg["stops"].values()))
        extras = []
        for plate, t in recent[recent["route_name"] == route].groupby("plate_no"):
            t = t.sort_values("collected_at")
            seqs, times = t["station_seq"].tolist(), t["collected_at"].tolist()

            # 건너뜀: 짧은 시간에 순번이 크게 뛰면서 후보 정류장을 지나침
            for (s0, t0), (s1, t1) in zip(zip(seqs, times), zip(seqs[1:], times[1:])):
                if s1 - s0 >= SKIP_JUMP and (t1 - t0) <= timedelta(minutes=2):
                    for stop_id, seq in cfg["stops"].items():
                        if s0 < seq < s1:
                            result[route]["skip_stops"].add(stop_id)
                            result[route]["notes"].append(
                                f"{plate}이(가) {STOPS[stop_id]['name']}을(를) 건너뜀 (우회 의심)")

            # 내 정류장보다 앞서(서울 쪽) 가는 버스만 '탐침'으로 사용
            if seqs[-1] <= my_seq or seqs[-1] > cfg["dest_seq"]:
                continue
            result[route]["probes"] += 1
            last_seq = seqs[-1]
            since = next(tm for s, tm in zip(seqs, times) if s == last_seq)   # 지금 구간에 들어온 시각
            stay = (now - since).total_seconds() / 60
            normal = baseline_min(route, last_seq, last_seq + 1, bundle, stations)
            where = stations.get(last_seq, {}).get("name", f"{last_seq}번째 정류장")

            if stay > normal + STUCK_MIN:                      # 정지
                extras.append(stay - normal)
                result[route]["notes"].append(f"앞차가 {where} 부근에서 {stay:.0f}분째 정지 (평소 {normal:.0f}분)")
            elif seqs[0] < last_seq:                           # 정체: 최근 구간 통과 속도
                took = (since - times[0]).total_seconds() / 60
                usual = baseline_min(route, seqs[0], last_seq, bundle, stations)
                if usual >= 3 and took > usual * SLOW_RATIO:
                    extras.append(took - usual)
                    start = stations.get(seqs[0], {}).get("name", "")
                    result[route]["notes"].append(
                        f"앞차가 {start}→{where} 구간을 {took:.0f}분에 통과 (평소 {usual:.0f}분)")
        if extras:
            result[route]["delay"] = float(pd.Series(extras).median())
    return result


if __name__ == "__main__":
    res = detect()
    if all(r["probes"] == 0 for r in res.values()):
        print("최근 기록이 없습니다. collect.py 를 켜 두면 1분마다 기록되어 돌발 감지가 작동합니다.")
    for route, r in res.items():
        status = f"+{r['delay']:.0f}분 지연 예상" if r["delay"] else "특이사항 없음"
        print(f"{route}번: {status}  (앞차 {r['probes']}대 확인)")
        for n in r["notes"]:
            print(f"   ⚠ {n}")
