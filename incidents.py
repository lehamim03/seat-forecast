# ==========================================================
#  돌발상황 감지 — 앞서 가는 같은 노선 버스들의 최근 움직임으로 판단
#  실행:  python incidents.py      (지금 노선별 지연·정지·정류장 건너뜀 감지 결과 출력)
#
#  탐침은 노선을 가리지 않습니다: 8800 · 3007 · 3008 은 경부고속도로를, 네 노선 모두 수원 시내 구간을
#  함께 달리므로, 앞서 가는 버스가 멈춘 곳이 내 노선 경로 위(NEAR_KM 안)면 다른 노선 버스라도 반영합니다.
#  국가교통정보센터 도로 속도(traffic.py)로 버스가 닿기 전의 정체도 미리 반영합니다.
#
#  사고 · 출퇴근 정체 · 시위 등 원인이 무엇이든, 버스가 실제로 느려지거나 멈추거나
#  정류장을 건너뛰면 수집 데이터(bus_seats.csv, collect.py 가 1분마다 기록)에 드러납니다.
#   - 정체   : 최근 구간을 평소보다 훨씬 오래 걸려 지나감
#   - 정지   : 한 정류장 구간에 평소보다 훨씬 오래 머물러 있음
#   - 건너뜀 : 1~2분 사이에 정류장 순번이 3칸 이상 뛰면서 후보 정류장을 지나침 (우회 의심)
#  평소 이동 시간은 train.py 실측값(seg_min)이 있으면 그 값을, 없으면
#  정류장 사이 거리 ÷ 평균 속도(BASE_KMH)로 추정합니다.
#
#  여기에 국가교통정보센터(ITS) 돌발상황정보(사고 · 공사 · 통제)를 더해,
#  노선이 지나는 길(정류장·경유지를 이은 선) 400m 안의 돌발상황을 원인과 함께 알려줍니다.
#  버스가 막히기 전에도 미리 알 수 있고, 앞차 기록이 없는 시간대도 보완합니다.
# ==========================================================
import math
import os
from datetime import datetime, timedelta

import pandas as pd
import requests

import api_key
import transit
import daytype
from config import CSV_FILE, ROUTES, STOPS, MY_STOP, DEFAULT_DROP_PER_STOP

RECENT_MIN = 20        # 최근 몇 분의 기록을 볼지
BASE_KMH = 30          # 실측값이 없을 때 쓰는 버스 평균 속도 (정차 포함)
SLOW_RATIO = 1.8       # 평소보다 이 배 이상 오래 걸리면 '정체'
STUCK_MIN = 8          # 한 구간에 평소 시간 + 이 분 이상 머물면 '정지'
SKIP_JUMP = 3          # 2분 안에 순번이 이만큼 이상 뛰면 '건너뜀'
SURGE_RATIO = 2.0      # 다가오는 버스의 좌석이 평소의 이 배 이상 빨리 줄면 '승객 쏠림'
SURGE_EXTRA = 5        # … 그리고 평소보다 이 석 이상 더 줄었을 때 (행사 · 혼잡 의심)

USE_ITS = True         # 국가교통정보센터 돌발상황정보 사용 여부
USE_TRAFFIC = True     # 국가교통정보센터 도로 속도(traffic.py) 사용 여부
NEAR_KM = 0.4          # 노선 경로에서 이 거리 안의 돌발상황만 반영
MAX_ITS_DELAY = 15     # 공식 돌발정보로 더하는 지연의 상한(분)
# 돌발 종류별 예상 지연(분) — 앞차 기록으로 실제 지연이 확인되면 그 값이 우선
# (차로 일부만 막는 공사는 지연으로 치지 않음)
EVENT_DELAY = {"사고": 12, "통제": 15, "정체": 4}
# 서울로 가는 우리와 반대 방향 표시
OPPOSITE = ("부산방향", "수원방향", "하행", "부산 방향", "수원 방향")


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


def fetch_events():
    """ITS 돌발상황 — 모든 노선 경로를 덮는 범위 안의 사고·공사·기타 돌발."""
    key = getattr(api_key, "ITS_KEY", "")
    if not (USE_ITS and key):
        return []
    xs, ys = [], []
    for cfg in ROUTES.values():
        for v in transit.route_stations(cfg["route_id"]).values():
            xs.append(v["x"]); ys.append(v["y"])
    try:
        res = requests.get("https://openapi.its.go.kr:9443/eventInfo", timeout=15, params={
            "apiKey": key, "type": "all", "eventType": "all", "getType": "json",
            "minX": min(xs), "maxX": max(xs), "minY": min(ys), "maxY": max(ys)})
        return res.json()["body"]["items"] or []
    except Exception:
        return []


def _seg_dist_km(p, a, b):
    """점 p 에서 선분 a-b 까지 거리(km)."""
    ax, ay = (a["x"] - p["x"]) * 88.4, (a["y"] - p["y"]) * 111.0
    bx, by = (b["x"] - p["x"]) * 88.4, (b["y"] - p["y"]) * 111.0
    dx, dy = bx - ax, by - ay
    t = 0.0 if dx == dy == 0 else max(0.0, min(1.0, -(ax * dx + ay * dy) / (dx * dx + dy * dy)))
    return math.hypot(ax + t * dx, ay + t * dy)


def event_delay(ev):
    """돌발상황 한 건의 예상 지연(분)."""
    text = f"{ev.get('eventType', '')} {ev.get('eventDetailType', '')} {ev.get('message', '')}"
    if "사고" in ev.get("eventType", "") or "사고" in ev.get("eventDetailType", ""):
        return EVENT_DELAY["사고"]
    if "전면" in (ev.get("lanesBlocked") or "") or "전면통제" in text:
        return EVENT_DELAY["통제"]
    if "정체" in text:
        return EVENT_DELAY["정체"]
    return 0


def on_my_road(route, ev):
    """이 노선이 실제로 지나는 도로의 돌발인지 (고속도로는 도로명으로, 서울 방향만)."""
    text = f"{ev.get('roadName', '')} {ev.get('message', '')}"
    if any(w in text for w in OPPOSITE):
        return False
    if ev.get("type") == "고속도로":   # 교차하는 다른 고속도로의 돌발 제외
        return any(k in (ev.get("roadName") or "") for k in ROUTES[route].get("roads", []))
    return True


def events_on_route(route, events):
    """노선이 내 정류장 → 서울 하차 정류장 사이에 지나는 길 위의 돌발상황."""
    cfg = ROUTES[route]
    st = transit.route_stations(cfg["route_id"])
    my_seq = cfg["stops"].get(MY_STOP, min(cfg["stops"].values()))
    found = []
    for ev in events:
        if not on_my_road(route, ev):
            continue
        try:
            p = {"x": float(ev["coordX"]), "y": float(ev["coordY"])}
        except (KeyError, TypeError, ValueError):
            continue
        for s in range(my_seq, transit.alight_seq(route)):
            if s in st and s + 1 in st and _seg_dist_km(p, st[s], st[s + 1]) <= NEAR_KM:
                # 위치 안내는 구간 양 끝 중 더 가까운 정류장(경유지) 이름으로
                near = s + 1 if _dist_km(p, st[s + 1]) < _dist_km(p, st[s]) else s
                found.append((near, ev))
                break
    return found


def load_recent(now):
    """수집기가 기록한 최근 RECENT_MIN 분의 차량 위치."""
    if not os.path.exists(CSV_FILE):
        return pd.DataFrame()
    df = pd.read_csv(CSV_FILE, encoding="utf-8-sig", dtype={"route_name": str})
    df["collected_at"] = pd.to_datetime(df["collected_at"])
    return df[df["collected_at"] >= now - timedelta(minutes=RECENT_MIN)]


def detect(now=None, bundle=None, recent=None, events=None):
    """노선별 돌발 판단 → {노선: {"delay": 추가 지연(분), "notes": [...], "skip_stops": {정류장ID}}}"""
    now = now or datetime.now()
    recent = load_recent(now) if recent is None else recent
    result = {r: {"delay": 0.0, "notes": [], "skip_stops": set(), "probes": 0, "drop_factor": 1.0}
              for r in ROUTES}

    # 1) 공식 돌발상황(ITS): 노선 경로 위의 사고·통제·차로 차단 → 원인 안내 + 예상 지연
    events = fetch_events() if events is None else events
    for route in ROUTES:
        st = transit.route_stations(ROUTES[route]["route_id"])
        its_delay, seen = 0, set()
        for s, ev in events_on_route(route, events):
            d = event_delay(ev)
            key = (ev.get("roadName"), ev.get("eventDetailType"), s)
            if not d or key in seen:   # 지연 없는 공사, 같은 곳 중복은 제외
                continue
            seen.add(key)
            its_delay += d
            near = st.get(s, {}).get("name", "").replace("(경유)", "")
            blocked = f", {ev['lanesBlocked']}" if ev.get("lanesBlocked") else ""
            result[route]["notes"].append(
                f"[{ev.get('eventType', '돌발')}] {ev.get('roadName', '')} {near} 부근 — "
                f"{(ev.get('message') or '').strip() or ev.get('eventDetailType', '')}{blocked}")
        result[route]["delay"] = float(min(its_delay, MAX_ITS_DELAY))

    # 1-2) 도로 속도: 버스가 닿기 전에도 평소보다 크게 느려진 구간 → 예상 지연
    if USE_TRAFFIC:
        try:
            import traffic
            for route, v in traffic.detect(now.to_pydatetime() if hasattr(now, "to_pydatetime") else now).items():
                if v["delay"]:
                    result[route]["notes"] += v["notes"]
                    result[route]["delay"] = max(result[route]["delay"], v["delay"])
        except Exception as e:
            print(f"  ! 도로 속도 확인 실패: {e}")

    # 2) 앞차 기록: 실제로 느려졌는지 · 멈췄는지 · 건너뛰었는지
    # 수집기가 멈춰 기록이 3분 넘게 끊겼으면 판단하지 않음 (오래된 기록으로 '정지'라고 오판 방지)
    if recent.empty or now - recent["collected_at"].max() > timedelta(minutes=3):
        return result
    now = recent["collected_at"].max()

    findings = []   # 앞서 가는 버스의 정지 · 정체 (위치 포함) — 아래에서 경로가 겹치는 모든 노선에 나눠 줌
    probes = []     # 탐침 버스 위치
    for route, cfg in ROUTES.items():
        stations = transit.route_stations(cfg["route_id"])
        my_seq = cfg["stops"].get(MY_STOP, min(cfg["stops"].values()))
        surges = []
        # 오늘 · 지금 시간대의 평소 정류장당 좌석 감소 (평일 출퇴근 > 평일 > 주말 > 공휴일)
        base_drop = (bundle or {}).get("drop", {}).get((route, now.hour), DEFAULT_DROP_PER_STOP)             * daytype.factors(now.to_pydatetime() if hasattr(now, "to_pydatetime") else now)["drop"]
        for plate, t in recent[recent["route_name"] == route].groupby("plate_no"):
            t = t.sort_values("collected_at")
            seqs, times = t["station_seq"].tolist(), t["collected_at"].tolist()

            # 승객 쏠림: 내 정류장으로 다가오는 버스의 좌석이 평소보다 훨씬 빨리 줄어듦 (행사 · 혼잡 의심)
            if "remain_seat" in t and seqs[-1] <= max(cfg["stops"].values()) and seqs[-1] - seqs[0] >= 2:
                seats = t["remain_seat"].tolist()
                if seats[0] >= 0 and seats[-1] >= 0:
                    dropped, usual = seats[0] - seats[-1], base_drop * (seqs[-1] - seqs[0])
                    if dropped - usual >= SURGE_EXTRA and dropped >= SURGE_RATIO * max(usual, 1):
                        surges.append(dropped / max(usual, 1))
                        a, b = stations.get(seqs[0], {}).get("name", ""), stations.get(seqs[-1], {}).get("name", "")
                        result[route]["notes"].append(
                            f"다가오는 차가 {a}→{b}에서 {dropped:.0f}석 줄어듦 (평소 {usual:.0f}석) — 승객 쏠림 의심")

            # 건너뜀: 짧은 시간에 순번이 크게 뛰면서 후보 정류장을 지나침
            for (s0, t0), (s1, t1) in zip(zip(seqs, times), zip(seqs[1:], times[1:])):
                if s1 - s0 >= SKIP_JUMP and (t1 - t0) <= timedelta(minutes=2):
                    for stop_id, seq in cfg["stops"].items():
                        if s0 < seq < s1:
                            result[route]["skip_stops"].add(stop_id)
                            result[route]["notes"].append(
                                f"{plate}이(가) {STOPS[stop_id]['name']}을(를) 건너뜀 (우회 의심)")

            # 집 앞 정류장을 지나 서울 쪽으로 가는 버스만 '탐침'으로 사용
            if seqs[-1] <= my_seq or seqs[-1] > transit.alight_seq(route):
                continue
            last_seq = seqs[-1]
            if last_seq not in stations:
                continue
            point = stations[last_seq]
            probes.append(point)
            since = next(tm for s, tm in zip(seqs, times) if s == last_seq)   # 지금 구간에 들어온 시각
            stay = (now - since).total_seconds() / 60
            normal = baseline_min(route, last_seq, last_seq + 1, bundle, stations)
            where = stations[last_seq]["name"]

            if stay > normal + STUCK_MIN:                      # 정지
                findings.append({"route": route, "point": point, "extra": stay - normal,
                                 "note": f"{where} 부근에서 {stay:.0f}분째 정지 (평소 {normal:.0f}분)"})
            elif seqs[0] < last_seq:                           # 정체: 최근 구간 통과 속도
                took = (since - times[0]).total_seconds() / 60
                usual = baseline_min(route, seqs[0], last_seq, bundle, stations)
                if usual >= 3 and took > usual * SLOW_RATIO:
                    start = stations.get(seqs[0], {}).get("name", "")
                    findings.append({"route": route, "point": point, "extra": took - usual,
                                     "note": f"{start}→{where} 구간을 {took:.0f}분에 통과 (평소 {usual:.0f}분)"})
        if surges:   # 뒤따라오는 버스도 좌석이 이만큼 빨리 줄 것으로 봄 (최대 3배)
            result[route]["drop_factor"] = float(min(pd.Series(surges).median(), 3.0))

    # 탐침 결과를 노선마다 나눠 줌: 멈춘 곳이 그 노선의 '집 앞 ~ 서울 하차' 경로 위에 있으면 반영
    for route, cfg in ROUTES.items():
        stations = transit.route_stations(cfg["route_id"])
        my_seq = cfg["stops"].get(MY_STOP, min(cfg["stops"].values()))
        segs = [(stations[s], stations[s + 1]) for s in range(my_seq, transit.alight_seq(route))
                if s in stations and s + 1 in stations]
        on_path = lambda p: any(_seg_dist_km(p, a, b) <= NEAR_KM for a, b in segs)
        result[route]["probes"] = sum(1 for p in probes if on_path(p))
        extras = []
        for f in findings:
            if f["route"] == route or on_path(f["point"]):
                extras.append(f["extra"])
                who = "앞차가" if f["route"] == route else f"앞서 가는 {f['route']}번이"
                result[route]["notes"].append(f"{who} {f['note']}")
        if extras:   # 앞차로 실제 지연이 확인되면 그 값을 우선 (공식 정보 · 도로 속도 추정치보다 정확)
            result[route]["delay"] = float(pd.Series(extras).median())
    return result


if __name__ == "__main__":
    res = detect()
    if all(r["probes"] == 0 for r in res.values()):
        print("최근 기록이 없습니다. collect.py 를 켜 두면 1분마다 기록되어 돌발 감지가 작동합니다.")
    for route, r in res.items():
        status = f"+{r['delay']:.0f}분 지연 예상" if r["delay"] else "특이사항 없음"
        if r["drop_factor"] > 1:
            status += f", 좌석이 평소의 {r['drop_factor']:.1f}배 속도로 줄어듦"
        print(f"{route}번: {status}  (앞차 {r['probes']}대 확인)")
        for n in r["notes"]:
            print(f"   ⚠ {n}")
