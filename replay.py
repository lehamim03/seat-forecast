# ==========================================================
#  지난 출근길 재생 — 수집한 기록(data/)으로 "그날 그 시각"의 추천을 다시 만들고, 실제로 어떻게 됐는지 붙임
#  실행:  python replay.py 2026-11-12T07:40            (평소 정류장 · 노선은 config 값)
#  서버:  /api/v1/recommendations?replay=2026-11-12T07:40  (alternatives · incidents 도 같음)
#
#  · 버스 위치 · 잔여 좌석: 그 시각 직전 3분 안에 기록된 차량별 마지막 위치 (실시간 API 대신)
#  · 정확한 도착 예정(분)은 기록이 없어 정류장 수 × 정류장당 시간으로 추정 (live_recommend 의 기본 동작)
#  · 돌발 감지: 그 시각 전 20분 기록 (공식 돌발정보는 지난 것을 받을 수 없어 뺌)
#  · 실제 결과(actual): 추천 선택지대로 그 정류장에 갔다면 실제로 탄 버스 · 만석으로 보낸 대수 · 서울 도착 기록 기준 국민대 도착
#    — backtest.py 의 realize 와 같은 방식
# ==========================================================
import os
import sys
from datetime import datetime, timedelta

import pandas as pd

from config import ROUTES, STOPS

SNAP_MIN = 3        # 이 시간 안의 마지막 기록을 '그 순간' 위치로
RECENT_MIN = 20     # 돌발 감지용 최근 기록
SEAT_MARGIN = 1
BUFFER_MIN = 1
_days = {}


class NoData(Exception):
    pass


def load_day(date):
    """그 운행일 기록 (자정 이후 기록은 같은 운행일 파일에 있음) + 운행 · 정류장별 출발 결과표"""
    if date in _days:
        return _days[date]
    path = os.path.join("data", f"bus_seats_{date}.csv")
    if not os.path.exists(path):
        raise NoData(f"{date} 기록이 없습니다")
    df = pd.read_csv(path, encoding="utf-8-sig", dtype={"route_name": str, "route_id": str})
    df["collected_at"] = pd.to_datetime(df["collected_at"])
    df = df[df["route_name"].isin(ROUTES)].sort_values(["plate_no", "collected_at"])
    ok = df[df["remain_seat"] >= 0]
    g = ok.groupby(["route_name", "plate_no"])
    new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
    ok = ok.assign(trip=new_trip.astype(int).groupby([ok["route_name"], ok["plate_no"]]).cumsum())
    import transit
    seoul_first = {r: transit.alight_candidates(r)[0] for r in ROUTES}
    deps = []
    for (route, plate, trip), t in ok.groupby(["route_name", "plate_no", "trip"]):
        arrived = t[t["station_seq"] >= seoul_first[route]]
        seoul_at = arrived["collected_at"].iloc[0] if not arrived.empty else pd.NaT
        for stop_id, seq in ROUTES[route]["stops"].items():
            before, after = t[t["station_seq"] <= seq], t[t["station_seq"] > seq]
            if after.empty:
                continue
            dep = before["collected_at"].iloc[-1] if not before.empty else after["collected_at"].iloc[0]
            deps.append({"route": route, "stop_id": stop_id, "plate": plate, "depart_at": dep, "seoul_at": seoul_at,
                         "seats_after": int(after["remain_seat"].iloc[0]),
                         "success": int(after["remain_seat"].iloc[0] >= SEAT_MARGIN)})
    _days[date] = (df, pd.DataFrame(deps).sort_values("depart_at") if deps else pd.DataFrame())
    return _days[date]


def snapshot(df, when):
    """그 순간 노선별 차량 목록 — 실시간 위치 API(getBusLocationListv2) 응답과 같은 모양"""
    s = df[(df["collected_at"] > when - timedelta(minutes=SNAP_MIN)) & (df["collected_at"] <= when)]
    s = s.groupby("plate_no").tail(1)
    out = {c["route_id"]: [] for c in ROUTES.values()}
    for _, r in s.iterrows():
        out.setdefault(str(r["route_id"]), []).append({
            "plateNo": r["plate_no"], "stationSeq": int(r["station_seq"]), "stationId": str(r["station_id"]),
            "remainSeatCnt": int(r["remain_seat"])})
    return out


def recent(df, when):
    r = df[(df["collected_at"] >= when - timedelta(minutes=RECENT_MIN)) & (df["collected_at"] <= when)]
    return r[["collected_at", "route_name", "plate_no", "station_seq"]].copy()


def actual(deps, route, stop_id, when, walk, leg_min, ride_default):
    """그 정류장에 갔다면 실제로 어땠나 → {boarded_at, full_skipped, arrive_at} (기록이 없으면 None)"""
    if deps.empty:
        return None
    ready = pd.Timestamp(when) + pd.Timedelta(minutes=walk + BUFFER_MIN)
    trips = deps[(deps["route"] == route) & (deps["stop_id"] == stop_id) & (deps["depart_at"] >= ready)]
    skipped = 0
    for _, b in trips.iterrows():
        if not b["success"]:
            skipped += 1
            continue
        seoul = b["seoul_at"] if pd.notna(b["seoul_at"]) else b["depart_at"] + pd.Timedelta(minutes=ride_default)
        arrive = seoul + pd.Timedelta(minutes=leg_min)
        return {"boarded_at": b["depart_at"].strftime("%H:%M"), "full_skipped": skipped,
                "arrive_at": arrive.strftime("%H:%M"),
                "total_min": round((arrive - pd.Timestamp(when)).total_seconds() / 60)}
    return {"boarded_at": None, "full_skipped": skipped, "arrive_at": None, "total_min": None}


def parse(value):
    try:
        when = datetime.fromisoformat(value.replace("Z", ""))
    except ValueError:
        raise NoData("replay 는 2026-11-12T07:40 형식입니다")
    return when.replace(second=0, microsecond=0, tzinfo=None)


def run(when, home_stop, route, compute):
    """compute(): live_recommend.main 을 부르는 함수 (호출하는 쪽이 평소 정류장 · 노선 전역값을 맞춰 둠).
    실시간 API 대신 그날 기록을 넣고 결과에 실제 결과를 붙인다."""
    import joblib
    import live_recommend as L
    import transit
    date = (when - timedelta(hours=3)).date().isoformat()   # 자정~03:00 은 전날 운행일
    df, deps = load_day(date)
    snap = snapshot(df, when)
    if not any(snap.values()):
        raise NoData(f"{when:%Y-%m-%d %H:%M} 직전 {SNAP_MIN}분 안에 기록이 없습니다 (수집 공백일 수 있음)")
    saved = (L.get, L.INCIDENT_RECENT, L.INCIDENT_EVENTS, L.NOW_OVERRIDE, L.LOCAL_EXTRA)
    try:
        L.get = lambda path, **p: snap.get(str(p.get("routeId")), []) if "location" in path else []
        L.INCIDENT_RECENT, L.INCIDENT_EVENTS, L.NOW_OVERRIDE, L.LOCAL_EXTRA = recent(df, when), [], when, None
        res = compute()
    finally:
        L.get, L.INCIDENT_RECENT, L.INCIDENT_EVENTS, L.NOW_OVERRIDE, L.LOCAL_EXTRA = saved
    legs = transit.get_legs(when, fetch=False)
    b = joblib.load("board_model.pkl") if os.path.exists("board_model.pkl") else {}

    def attach(o):
        if not o:
            return o
        ride = b.get("ride", {}).get((o["route"], o["stop"]["id"]), 60)
        o["actual"] = actual(deps, o["route"], o["stop"]["id"], when, o["walk_min"], legs[o["route"]]["minutes"], ride)
        return o
    for o in res["ranking"]:
        attach(o)
    for k in res["alternatives"]:
        attach(res["alternatives"][k])
    top = res["ranking"][0] if res["ranking"] else None
    a = top and top.get("actual")
    msg = None
    if a and a["arrive_at"]:
        msg = (f"그날 1순위({top['route']}번 · {top['stop']['name']})대로 했다면 {a['boarded_at']}에 타서 "
               f"{a['arrive_at']} 국민대 도착" + (f", 만석으로 {a['full_skipped']}대 보냄" if a["full_skipped"] else ""))
    usual = next((o for o in res["ranking"] if o["route"] == route and o["stop"]["id"] == home_stop), None)
    ua = usual and usual.get("actual")
    if msg and ua and ua["arrive_at"] and usual is not top:
        msg += f" · 평소대로({route}번 · {usual['stop']['name']})면 {ua['arrive_at']}" + (
            f", 만석 {ua['full_skipped']}대" if ua["full_skipped"] else "")
    res["replay"] = {"at": when.strftime("%Y-%m-%dT%H:%M:00+09:00"), "date": date, "message": msg,
                     "note": "수집 기록으로 다시 계산 — 도착 예정 분은 정류장 수로 추정, 공식 돌발정보 없음"}
    return res


def days():
    """재생할 수 있는 운행일 (data/ 파일)"""
    import glob
    return sorted(os.path.basename(f)[10:20] for f in glob.glob(os.path.join("data", "bus_seats_*.csv")))


if __name__ == "__main__":
    import contextlib
    import io
    import live_recommend as L
    when = parse(sys.argv[1])
    with contextlib.redirect_stdout(io.StringIO()):
        r = run(when, "203000063", "8800", lambda: L.main("8800"))
    print(r["replay"]["message"])
    for o in r["ranking"][:6]:
        a = o.get("actual") or {}
        print(f"  예측 {o['arrive_at']} {o['kind']:5} {o['route']:>5} @{o['stop']['name']:<16} 확률 {o['board_prob']:.0%}"
              f"  → 실제 {a.get('boarded_at')} 탑승 · 만석 {a.get('full_skipped')}대 · {a.get('arrive_at')} 도착")
