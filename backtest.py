# ==========================================================
#  백테스트 — "추천대로 탔다면 평소보다 실제로 얼마나 빨랐을까?"
#  실행:  python backtest.py              (data 폴더 사용, 최근 평일 8일을 시험)
#         python backtest.py 폴더 시험일수
#
#  과거 날짜로만 학습하고, 시험 날짜의 평일 06:30~09:00 에 5분마다 집을 나선다고 가정합니다.
#  각 방식은 그 시각까지 보인 정보로만 버스를 고르고, 결과는 실제 기록으로 계산합니다.
#   · 실제로 그 정류장을 떠날 때 좌석이 남은 버스만 탈 수 있음 (만석이면 다음 차)
#   · 서울 도착은 그 버스의 실제 기록, 없으면 학습 기간 중앙값
#   · 서울 → 국민대 환승 시간은 ODsay 저장값 (날마다 달라지지 않음)
# ==========================================================
import glob
import os
import sys
from datetime import datetime

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier

import daytype
import transit
from config import DEFAULT_HEADWAY_MIN, DEFAULT_MIN_PER_STOP, MY_ROUTE, MY_STOP, ROUTES, STOPS
from recommend import expected_wait

FOLDER = sys.argv[1] if len(sys.argv) > 1 else "data"
TEST_DAYS = int(sys.argv[2]) if len(sys.argv) > 2 else 8
SEAT_MARGIN = 1
LIVE_MAX_STOPS = 10
LOOKAHEAD = 3
BUFFER_MIN = 1
START, END, STEP = 6 * 60 + 30, 9 * 60, 5   # 집을 나서는 시각 범위 (분)
SEEN_MIN = 3                                # 이만큼 최근에 보인 버스만 '지금 보이는 버스'로 침

# ---------- 1. 데이터 → 운행 × 정류장 표 (train.py 와 같은 방식) ----------
files = sorted(glob.glob(os.path.join(FOLDER, "bus_seats_*.csv")))
if not files:
    sys.exit("수집 데이터가 없습니다.")
df = pd.concat([pd.read_csv(f, encoding="utf-8-sig", dtype={"route_name": str}) for f in files]).drop_duplicates()
df["collected_at"] = pd.to_datetime(df["collected_at"])
df = df[(df["remain_seat"] >= 0) & df["route_name"].isin(ROUTES)].sort_values(["plate_no", "collected_at"])
g = df.groupby(["date", "route_name", "plate_no"])
new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
df["trip"] = new_trip.astype(int).groupby([df["date"], df["route_name"], df["plate_no"]]).cumsum()

SEOUL_FIRST = {r: transit.alight_candidates(r)[0] for r in ROUTES}
rows, live_rows = [], []
for (date, route, plate, trip), t in df.groupby(["date", "route_name", "plate_no", "trip"]):
    arrived = t[t["station_seq"] >= SEOUL_FIRST[route]]
    seoul_at = arrived["collected_at"].iloc[0] if not arrived.empty else pd.NaT
    for stop_id, seq in ROUTES[route]["stops"].items():
        before, after = t[t["station_seq"] <= seq], t[t["station_seq"] > seq]
        if after.empty:
            continue
        first_after = after.iloc[0]
        dep = before["collected_at"].iloc[-1] if not before.empty else first_after["collected_at"]
        ok = int(first_after["remain_seat"] >= SEAT_MARGIN)
        rows.append({"date": date, "route_name": route, "stop_id": stop_id, "weekday": first_after["weekday"],
                     "depart_at": dep, "seoul_at": seoul_at, "success": ok})
        ahead = before[(before["station_seq"] < seq) & (before["station_seq"] >= seq - LIVE_MAX_STOPS)]
        for _, o in ahead.groupby("station_seq").tail(1).iterrows():
            live_rows.append({"date": date, "route_name": route, "stop_id": stop_id, "seen_at": o["collected_at"],
                              "n_stops": seq - o["station_seq"], "seats_now": o["remain_seat"], "success": ok})
data = pd.DataFrame(rows).sort_values("depart_at")
live = pd.DataFrame(live_rows)
kinds = {d: daytype.day_info(pd.Timestamp(d).date())["day_type"] for d in data["date"].unique()}
route_map = {r: i for i, r in enumerate(sorted(ROUTES))}
stop_map = {s: i for i, s in enumerate(sorted(STOPS))}
for f in (data, live):
    f["day_type"] = f["date"].map(kinds)
    f["route_code"] = f["route_name"].map(route_map)
    f["stop_code"] = f["stop_id"].map(stop_map)
data["time_min"] = data["depart_at"].dt.hour * 60 + data["depart_at"].dt.minute
live["time_min"] = live["seen_at"].dt.hour * 60 + live["seen_at"].dt.minute
data["headway_min"] = data.groupby(["date", "route_name", "stop_id"])["depart_at"].diff().dt.total_seconds() / 60
data["headway_min"] = data["headway_min"].fillna(data["headway_min"].median())
data["ride_min"] = (data["seoul_at"] - data["depart_at"]).dt.total_seconds() / 60

# ---------- 2. 시험 날짜 고르기 · 과거로만 학습 ----------
# 평일(공휴일 제외)이고 그날 12시 넘어서까지 수집된 날만 시험 — 09:00 출발이 끝까지 계산되도록
last_seen = df.groupby("date")["collected_at"].max()
full = [d for d in sorted(data["date"].unique())
        if kinds[d] == 0 and last_seen[d] >= pd.Timestamp(d) + pd.Timedelta(hours=12)]
test_dates = full[-TEST_DAYS:]
cut = test_dates[0]
train, train_live = data[data["date"] < cut], live[live["date"] < cut]
FEATURES = ["route_code", "stop_code", "weekday", "day_type", "time_min", "headway_min"]
LIVE_FEATURES = ["route_code", "stop_code", "n_stops", "seats_now", "time_min", "day_type"]
sched_model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5, random_state=42)
sched_model.fit(train[FEATURES], train["success"])
live_model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5, random_state=42)
live_model.fit(train_live[LIVE_FEATURES], train_live["success"])

headway = train.groupby("route_name")["headway_min"].median().to_dict()
ride = train.dropna(subset=["ride_min"]).groupby(["route_name", "stop_id"])["ride_min"].median().to_dict()
# 이동시간은 시간대 차이가 큼 (8800: 하루 전체 45분 · 평일 7시대 61분) — 평일 (노선, 정류장, 시) 중앙값
weekday_rides = train[(train["day_type"] == 0) & (train["ride_min"] > 10)]
ride_hour = (weekday_rides.groupby(["route_name", "stop_id", weekday_rides["depart_at"].dt.hour])["ride_min"]
             .median().to_dict())
key = ["date", "route_name", "plate_no", "trip"]
t = df[df["date"] < cut].copy()
t["d_seq"] = t.groupby(key)["station_seq"].diff()
t["d_min"] = t.groupby(key)["collected_at"].diff().dt.total_seconds() / 60
near_max = {r: max(c["stops"].values()) for r, c in ROUTES.items()}
t = t[(t["d_seq"] > 0) & (t["station_seq"] <= t["route_name"].map(near_max))]
min_per_stop = (t["d_min"] / t["d_seq"]).groupby(t["route_name"]).median().to_dict()

peak_time = datetime.strptime(test_dates[0] + " 07:40", "%Y-%m-%d %H:%M")
legs = {r: v["minutes"] for r, v in transit.get_legs(peak_time, fetch=False).items()}
walks = {s: v["walk_min"] for s, v in STOPS.items()}
OPTIONS = [(r, s) for r, c in ROUTES.items() for s in c["stops"]]


def ride_at(route, stop, leave, by_hour):
    """예상 이동시간(분). by_hour=False 면 지금 추천 코드처럼 하루 전체 중앙값"""
    return ride_hour.get((route, stop, leave.hour), ride[(route, stop)]) if by_hour else ride[(route, stop)]


# ---------- 3. 각 방식이 고르는 조합 · 실제 결과 ----------
def realize(day, route, stop, leave):
    """실제로 탈 수 있었던 첫 버스 → (국민대까지 걸린 분, 만석으로 놓친 대수). 버스가 없으면 None"""
    ready = leave + pd.Timedelta(minutes=walks[stop] + BUFFER_MIN)
    trips = day[(day["route_name"] == route) & (day["stop_id"] == stop) & (day["depart_at"] >= ready)]
    missed = 0
    for _, b in trips.iterrows():
        if not b["success"]:
            missed += 1
            continue
        arrive = b["seoul_at"] if pd.notna(b["seoul_at"]) else b["depart_at"] + pd.Timedelta(minutes=ride[(route, stop)])
        return (arrive - leave).total_seconds() / 60 + legs[route], missed
    return None


def visible(snap, route, seq):
    """지금 보이는, 정류장으로 다가오는 버스 → [(남은 정류장, 좌석)] 가까운 순"""
    near = snap[(snap["route_name"] == route) & (snap["station_seq"] < seq)]
    return sorted((seq - int(s), int(c)) for s, c in zip(near["station_seq"], near["remain_seat"]))[:LOOKAHEAD]


def pick_map(snap, leave, by_hour=False):
    """지도 앱 방식 — 모든 버스를 탈 수 있다고 보고 가장 빠른 조합"""
    totals = {}
    for route, stop in OPTIONS:
        hw, walk = headway.get(route, DEFAULT_HEADWAY_MIN), walks[stop]
        per = min_per_stop.get(route, DEFAULT_MIN_PER_STOP)
        etas = [n * per for n, _ in visible(snap, route, ROUTES[route]["stops"][stop])]
        ok = [e for e in etas if e >= walk + BUFFER_MIN]
        totals[(route, stop)] = (ok[0] if ok else walk + hw / 2) + ride_at(route, stop, leave, by_hour) + legs[route]
    return min(totals, key=totals.get)


def pick_schedule(leave, by_hour=False):
    """시간표 모델 (recommend.py 와 같은 계산) — 그날 혼잡은 모름"""
    hm = leave.hour * 60 + leave.minute
    x = pd.DataFrame([{"route_code": route_map[r], "stop_code": stop_map[s], "weekday": leave.weekday(), "day_type": 0,
                       "time_min": hm + walks[s], "headway_min": headway[r]} for r, s in OPTIONS])[FEATURES]
    p = sched_model.predict_proba(x)[:, 1]
    totals = [walks[s] + expected_wait(headway[r], q) + ride_at(r, s, leave, by_hour) + legs[r] for (r, s), q in zip(OPTIONS, p)]
    return OPTIONS[int(np.argmin(totals))]


def live_totals(snap, leave, by_hour=False):
    """실시간 모델 (live_recommend.py 와 같은 계산) — 조합별 예상 소요시간(분). 다가오는 버스의 지금 좌석을 봄"""
    hm = leave.hour * 60 + leave.minute
    cands = {o: visible(snap, o[0], ROUTES[o[0]]["stops"][o[1]]) for o in OPTIONS}
    x = [{"route_code": route_map[r], "stop_code": stop_map[s], "n_stops": n, "seats_now": c,
          "time_min": hm, "day_type": 0} for (r, s), v in cands.items() for n, c in v]
    p = iter(live_model.predict_proba(pd.DataFrame(x)[LIVE_FEATURES])[:, 1]) if x else iter(())
    totals = {}
    for (r, s), v in cands.items():
        walk, hw = walks[s], headway.get(r, DEFAULT_HEADWAY_MIN)
        coming = [(n * min_per_stop.get(r, DEFAULT_MIN_PER_STOP), next(p)) for n, _ in v]
        reach = [(e, q) for e, q in coming if e >= walk + BUFFER_MIN]
        expected, miss = 0.0, 1.0
        for e, q in reach:
            expected += miss * q * e
            miss *= 1 - q
        last = reach[-1][0] if reach else walk + hw / 2
        later = max(sum(q for _, q in reach) / len(reach), 0.2) if reach else 0.5
        expected += miss * (last + hw * min(1 / later, 4))
        totals[(r, s)] = expected + ride_at(r, s, leave, by_hour) + legs[r]
    return totals


def pick_live(snap, leave, by_hour=False):
    totals = live_totals(snap, leave, by_hour)
    return min(totals, key=totals.get)


# ---------- 4. 시험 날짜마다 5분 간격으로 돌려 보기 ----------
BASE = "평소 (집 앞 8800)"
# 지하철은 실측 기록이 없어 ODsay 시간표 기준 고정값 — 만석이 없으므로 항상 탈 수 있다고 봄
subway = transit.subway_route(peak_time, fetch=False)
results = []
for d in test_dates:
    day, buses = data[data["date"] == d], df[df["date"] == d]
    for m in range(START, END + 1, STEP):
        leave = pd.Timestamp(d) + pd.Timedelta(minutes=m)
        seen = buses[(buses["collected_at"] <= leave) & (buses["collected_at"] > leave - pd.Timedelta(minutes=SEEN_MIN))]
        snap = seen.sort_values("collected_at").groupby(["route_name", "plate_no"]).tail(1)
        picks = {BASE: (MY_ROUTE, MY_STOP), "지도 앱 방식": pick_map(snap, leave),
                 "시간표 추천": pick_schedule(leave), "실시간 추천": pick_live(snap, leave),
                 "시간표 추천+시간대별 이동": pick_schedule(leave, True),
                 "실시간 추천+시간대별 이동": pick_live(snap, leave, True)}
        outcome = {name: realize(day, *pick, leave) for name, pick in picks.items()}
        if any(v is None for v in outcome.values()):
            continue
        every = [v for v in (realize(day, *o, leave) for o in OPTIONS) if v is not None]
        outcome["사후 최선 (상한)"] = min(every)
        picks["사후 최선 (상한)"] = ("-", MY_STOP)
        if subway:
            outcome["지하철만 (시간표 기준)"] = (subway["minutes"], 0)
            picks["지하철만 (시간표 기준)"] = ("지하철", MY_STOP)
            totals = live_totals(snap, leave, True)
            mix = "실시간 추천+지하철"
            if subway["minutes"] < min(totals.values()):
                outcome[mix], picks[mix] = (subway["minutes"], 0), ("지하철", MY_STOP)
            else:
                outcome[mix], picks[mix] = outcome["실시간 추천+시간대별 이동"], picks["실시간 추천+시간대별 이동"]
        for name, (mins, missed) in outcome.items():
            results.append({"date": d, "leave": m, "policy": name, "route": picks[name][0], "stop": picks[name][1],
                            "minutes": mins, "missed": missed})
res = pd.DataFrame(results)

# ---------- 5. 결과 ----------
print(f"학습 {train['date'].nunique()}일 ({cut} 전) · 시험 평일 {len(test_dates)}일 ({test_dates[0]} ~ {test_dates[-1]})")
print(f"출발 시각 {len(res) // res['policy'].nunique()}건 (06:30~09:00, 5분 간격)\n")
base = res[res["policy"] == BASE].set_index(["date", "leave"])["minutes"]
rng = np.random.default_rng(42)
print("방식 | 평균 | 중앙값 | 상위10% | 만석 겪음 | 평소 대비 빠름 | 95% 구간 (날짜 재표집)")
for name, f in res.groupby("policy", sort=False):
    gain = base - f.set_index(["date", "leave"])["minutes"]
    daily = gain.groupby(level="date").mean().to_numpy()
    boot = [rng.choice(daily, len(daily)).mean() for _ in range(5000)]
    lo, hi = np.percentile(boot, [2.5, 97.5])
    print(f"{name} | {f['minutes'].mean():.1f} | {f['minutes'].median():.1f} | {f['minutes'].quantile(.9):.1f} | "
          f"{(f['missed'] > 0).mean():.1%} | {gain.mean():+.1f}분 | {lo:+.1f} ~ {hi:+.1f}")

print(f"\n지하철 경로: 시간표 기준 {subway['minutes']:.0f}분 · 환승 {subway['transfers']}회 · {subway['summary']}" if subway
      else "\n지하철 경로: ODsay 저장값 없음 (비교 생략)")
if subway:
    mix = res[res["policy"] == "실시간 추천+지하철"]
    print(f"실시간 추천+지하철이 지하철을 고른 비율: {(mix['route'] == '지하철').mean():.1%}")

print("\n[출발 시간대별 평균 소요시간(분)]")
res["slot"] = pd.cut(res["leave"], [389, 419, 449, 479, 509, 541], labels=["06:30", "07:00", "07:30", "08:00", "08:30"])
print(res.pivot_table(index="slot", columns="policy", values="minutes", aggfunc="mean", observed=True)
      [list(res["policy"].unique())].round(1).to_string())

for name in ("지도 앱 방식", "시간표 추천", "실시간 추천", "시간표 추천+시간대별 이동", "실시간 추천+시간대별 이동"):
    pick = res[res["policy"] == name]
    print(f"\n[{name}이 고른 조합]")
    print(pick.assign(정류장=pick["stop"].map(lambda s: STOPS[s]["name"])).groupby(["route", "정류장"]).size()
          .sort_values(ascending=False).to_string())
    g2 = base - pick.set_index(["date", "leave"])["minutes"]
    print(f"평소보다 빠름 {(g2 > 1).mean():.0%} · 비슷(±1분) {(g2.abs() <= 1).mean():.0%} · 느림 {(g2 < -1).mean():.0%}")
    print("날짜별 평균 이득(분): " + ", ".join(f"{d[5:]} {v:+.1f}" for d, v in g2.groupby(level="date").mean().items()))
