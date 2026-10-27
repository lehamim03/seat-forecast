# ==========================================================
#  탑승 성공 확률 예측 모델 (Random Forest) — 노선 × 후보 정류장
#  데이터가 최소 2~3주(평일 10일 이상) 쌓인 뒤 실행하세요.
#  준비:  pip install pandas scikit-learn joblib
#  실행:  python train.py   →  board_model.pkl 저장 (recommend.py 가 사용)
# ==========================================================
import glob
import os
import sys

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import brier_score_loss, f1_score, precision_score, recall_score, roc_auc_score

import daytype
import transit
from config import CSV_FILE, PEAK_HOURS, ROUTES, STOPS
from live_recommend import board_prob

# 정류장을 떠난 직후 잔여 좌석이 이 숫자 이상이면 '탑승 성공'.
# 1석이라도 남기고 떠났다 = 그 정류장에 줄 선 사람이 전부 탔다는 뜻.
# (더 보수적으로 보고 싶으면 2~3으로 올리세요)
SEAT_MARGIN = 1

# 실시간 모델: 후보 정류장 몇 정거장 앞까지의 버스 기록을 쓸지 (live_recommend 는 다가오는 버스 3대까지 봄)
LIVE_MAX_STOPS = 10

# ---------- 1. 불러오기 ----------
# 노트북에서 모은 파일 + GitHub Actions 가 날마다 저장한 data/bus_seats_YYYY-MM-DD.csv
files = ([CSV_FILE] if os.path.exists(CSV_FILE) else []) + sorted(glob.glob("data/bus_seats_*.csv"))
if not files:
    sys.exit("수집 데이터가 없습니다. collect.py 를 실행하거나 GitHub 의 data 폴더를 받아오세요 (git pull).")
df = pd.concat([pd.read_csv(f, encoding="utf-8-sig", dtype={"route_name": str}) for f in files]).drop_duplicates()
print(f"데이터 파일 {len(files)}개 사용")
df["collected_at"] = pd.to_datetime(df["collected_at"])
df = df[(df["remain_seat"] >= 0) & df["route_name"].isin(ROUTES)]   # -1(정보 없음) 제거
df = df.sort_values(["plate_no", "collected_at"])

# ---------- 2. 버스 '운행 1회' 단위로 묶기 ----------
# 같은 차량이 아침에 두 번 출발할 수도 있으므로,
# 정류장 순번이 뒤로 돌아가거나 1시간 넘게 안 보이면 새 운행으로 본다.
g = df.groupby(["date", "route_name", "plate_no"])
new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
df["trip"] = new_trip.astype(int).groupby([df["date"], df["route_name"], df["plate_no"]]).cumsum()

# ---------- 3. 운행 1회 × 후보 정류장마다 한 줄 만들기 ----------
# 버스 이동시간(ride)은 '서울 첫 정차 정류장'까지 — 거기서 하차 정류장까지는 transit.get_legs 가 더함
SEOUL_FIRST = {r: transit.alight_candidates(r)[0] for r in ROUTES}
rows, live_rows = [], []
for (date, route, plate, trip), t in df.groupby(["date", "route_name", "plate_no", "trip"]):
    cfg = ROUTES[route]
    arrived = t[t["station_seq"] >= SEOUL_FIRST[route]]   # 서울 첫 정차 정류장에 닿은 기록
    for stop_id, seq in cfg["stops"].items():
        before, after = t[t["station_seq"] <= seq], t[t["station_seq"] > seq]
        if after.empty:
            continue  # 이 정류장을 떠나는 모습이 기록되지 않음
        first_after = after.iloc[0]
        # 출발 시각: 정류장에서 마지막으로 보인 시각 (없으면 떠난 뒤 처음 보인 시각)
        dep = before["collected_at"].iloc[-1] if not before.empty else first_after["collected_at"]
        ride = (arrived["collected_at"].iloc[0] - dep).total_seconds() / 60 if not arrived.empty else None
        rows.append({
            "date": date, "route_name": route, "stop_id": stop_id, "stop_seq": seq,
            "weekday": first_after["weekday"], "depart_at": dep,
            "seats_after": first_after["remain_seat"], "ride_min": ride,
        })
        # 실시간 모델용: 정류장에 닿기 전 정거장마다 마지막으로 보인 잔여 좌석 → 이 정류장에서 탈 수 있었나
        ahead = before[(before["station_seq"] < seq) & (before["station_seq"] >= seq - LIVE_MAX_STOPS)]
        for _, o in ahead.groupby("station_seq").tail(1).iterrows():
            live_rows.append({
                "date": date, "route_name": route, "stop_id": stop_id, "seen_at": o["collected_at"],
                "n_stops": seq - o["station_seq"], "seats_now": o["remain_seat"],
                "success": int(first_after["remain_seat"] >= SEAT_MARGIN),
            })

data = pd.DataFrame(rows)
if data.empty:
    sys.exit("분석할 운행 기록이 없습니다. 수집이 제대로 되었는지 CSV를 확인하세요.")

data["time_min"] = data["depart_at"].dt.hour * 60 + data["depart_at"].dt.minute
# 날짜 종류: 0 = 평일, 1 = 토요일, 2 = 일요일·공휴일 (대체공휴일 포함)
kinds = {d: daytype.day_info(pd.Timestamp(d).date())["day_type"] for d in data["date"].unique()}
data["day_type"] = data["date"].map(kinds)

# 같은 정류장 · 같은 노선 앞차와의 간격(배차간격) — 간격이 길수록 사람이 많이 몰림
data = data.sort_values("depart_at")
data["headway_min"] = (data.groupby(["date", "route_name", "stop_id"])["depart_at"]
                       .diff().dt.total_seconds() / 60)
data["headway_min"] = data["headway_min"].fillna(data["headway_min"].median())

# 정답(label)
data["success"] = (data["seats_after"] >= SEAT_MARGIN).astype(int)

# ---------- 4. 입력(feature) 만들기 ----------
route_map = {r: i for i, r in enumerate(sorted(ROUTES))}
stop_map = {s: i for i, s in enumerate(sorted(STOPS))}
data["route_code"] = data["route_name"].map(route_map)
data["stop_code"] = data["stop_id"].map(stop_map)
FEATURES = ["route_code", "stop_code", "weekday", "day_type", "time_min", "headway_min"]

print(f"기록 {len(data)}건 (운행 × 정류장), 수집 일수 {data['date'].nunique()}일")
print(f"전체 탑승 성공률(좌석 남기고 출발한 비율): {data['success'].mean():.1%}\n")
print("[정류장별 탑승 성공률]")
print(data.assign(정류장=data["stop_id"].map(lambda s: STOPS[s]["name"]))
          .pivot_table(index="route_name", columns="정류장", values="success", aggfunc="mean")
          .apply(lambda col: col.map(lambda v: f"{v:.0%}" if pd.notna(v) else "-")), "\n")

dates = sorted(data["date"].unique())
if len(dates) < 5:
    sys.exit("수집 일수가 너무 적습니다. 최소 평일 10일 이상 모은 뒤 다시 실행하세요.")

# ---------- 5. 날짜 기준으로 학습/시험 나누기 ----------
# 무작위로 섞으면 같은 날 데이터가 양쪽에 들어가 성능이 부풀려진다.
# '과거로 학습 → 미래를 맞히기' 구조로 나눈다.
cut = dates[int(len(dates) * 0.8)]
train, test = data[data["date"] < cut], data[data["date"] >= cut]

# class_weight="balanced" 는 쓰지 않음 — 확률이 실제보다 낮게 나와 기대 대기시간이 부풀려짐
model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5, random_state=42)
model.fit(train[FEATURES], train["success"])

# ---------- 6. 평가 ----------
# 추천은 확률 p를 그대로 기대 대기시간 공식에 넣으므로, '50% 기준으로 맞혔나'보다
# 확률이 실제 비율과 맞는지(Brier · 구간별 비교)가 중요하다.
def is_peak(frame):
    """평일 출퇴근 시간대 — 탑승 실패가 거의 여기서만 생김"""
    in_peak = sum((frame["time_min"] >= a) & (frame["time_min"] < b) for a, b in PEAK_HOURS) > 0
    return in_peak & (frame["day_type"] == 0)


def report(name, y, p):
    y, p = np.asarray(y), np.asarray(p)
    auc = f"{roc_auc_score(y, p):.3f}" if len(set(y)) == 2 else "  - "
    print(f"  {name:<14} AUC {auc}  Brier {brier_score_loss(y, p, labels=[0, 1]):.4f}  "
          f"평균 예측 {p.mean():.1%} (실제 {y.mean():.1%}, {len(y)}건 중 실패 {int((y == 0).sum())}건)")


def calibration(y, p, edges=(0, 0.5, 0.8, 0.9, 0.95, 0.98, 1.0001)):
    """예측 확률 구간마다 실제로 몇 %가 탔나 — 두 숫자가 비슷해야 확률을 믿을 수 있음"""
    t = pd.DataFrame({"y": np.asarray(y), "p": np.asarray(p)})
    t["구간"] = pd.cut(t["p"], edges, right=False)
    g = t.groupby("구간", observed=True).agg(건수=("y", "size"), 예측=("p", "mean"), 실제=("y", "mean"))
    for k, r in g.iterrows():
        print(f"    {k.left:.0%}~{min(k.right, 1):.0%}  {int(r['건수']):>5}건  예측 {r['예측']:.1%}  실제 {r['실제']:.1%}")


def pick_threshold(y, p):
    """'만석 위험' 경고 기준: 탑승 실패를 경고로 잡는 F1 이 가장 큰 확률 (확률 자체는 건드리지 않음)"""
    fail = 1 - np.asarray(y)
    ths = np.round(np.arange(0.05, 0.96, 0.05), 2)
    return max(ths, key=lambda th: f1_score(fail, (np.asarray(p) < th).astype(int), zero_division=0))


prob = model.predict_proba(test[FEATURES])[:, 1]
# 비교 기준(baseline): '노선 + 정류장 + 10분 단위 시간대'의 과거 평균 성공률
keys = ["route_name", "stop_id", "slot"]
slot_rate = train.assign(slot=train["time_min"] // 10).groupby(keys)["success"].mean()
base = np.array([slot_rate.get((r, s, t // 10), train["success"].mean())
                 for r, s, t in zip(test["route_name"], test["stop_id"], test["time_min"])])
peak = is_peak(test).to_numpy()
print(f"[모델 성능 — 학습에 안 쓴 최근 날짜({cut} 부터)로 시험 · Brier 는 낮을수록 좋음]")
print(" 전체")
report("모델", test["success"], prob)
report("단순 평균 방식", test["success"], base)
print(" 평일 출퇴근 시간대만")
report("모델", test["success"][peak], prob[peak])
report("단순 평균 방식", test["success"][peak], base[peak])
print(" 예측 확률 구간별 실제 탑승 비율 (모델)")
calibration(test["success"], prob)
print()

print("[무엇이 중요했나]")
for f, imp in sorted(zip(FEATURES, model.feature_importances_), key=lambda x: -x[1]):
    print(f"  {f:12s} {imp:.2f}")

# ---------- 7. 실시간 추천용 실측값: 정류장 하나마다 줄어드는 좌석 · 걸리는 시간 ----------
# 같은 운행에서 연달아 찍힌 두 기록을 비교한다 (후보 정류장 부근 구간만)
t = df.copy()
key = ["date", "route_name", "plate_no", "trip"]
t["d_seq"] = t.groupby(key)["station_seq"].diff()
t["d_seat"] = t.groupby(key)["remain_seat"].diff()
t["d_min"] = t.groupby(key)["collected_at"].diff().dt.total_seconds() / 60
near_max = {r: max(c["stops"].values()) for r, c in ROUTES.items()}
t = t[(t["d_seq"] > 0) & (t["station_seq"] <= t["route_name"].map(near_max))]
t["hour"] = t["collected_at"].dt.hour
drop = (-t["d_seat"] / t["d_seq"]).clip(lower=0).groupby([t["route_name"], t["hour"]]).median().to_dict()
min_per_stop = (t["d_min"] / t["d_seq"]).groupby(t["route_name"]).median().to_dict()

# 돌발 감지용: 서울 도착까지 모든 구간의 '평소' 정류장당 이동 시간 (노선, 순번)
s = df.copy()
s["d_seq"] = s.groupby(key)["station_seq"].diff()
s["d_min"] = s.groupby(key)["collected_at"].diff().dt.total_seconds() / 60
s = s[(s["d_seq"] > 0) & (s["d_seq"] <= 3)]
s["from_seq"] = s["station_seq"] - s["d_seq"]
seg_min = (s["d_min"] / s["d_seq"]).groupby([s["route_name"], s["from_seq"].astype(int)]).median().to_dict()

# ---------- 7-1. 실시간 탑승 확률 모델 (live_recommend 용) ----------
# 시간표 모델은 '그날 실제로 얼마나 붐비는지'를 모른다.
# 다가오는 버스의 지금 잔여 좌석 · 남은 정류장 수를 넣어 그 정류장에서 탈 수 있는지 예측한다.
live = pd.DataFrame(live_rows)
live["time_min"] = live["seen_at"].dt.hour * 60 + live["seen_at"].dt.minute
live["day_type"] = live["date"].map(kinds)
live["route_code"] = live["route_name"].map(route_map)
live["stop_code"] = live["stop_id"].map(stop_map)
LIVE_FEATURES = ["route_code", "stop_code", "n_stops", "seats_now", "time_min", "day_type"]
live_train, live_test = live[live["date"] < cut], live[live["date"] >= cut]
live_model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5, random_state=42)
live_model.fit(live_train[LIVE_FEATURES], live_train["success"])
live_prob = live_model.predict_proba(live_test[LIVE_FEATURES])[:, 1]
# 비교 기준: 지금까지 쓰던 공식 — (지금 좌석 − 정류장당 감소 × 남은 정류장) 을 board_prob 에 넣음
formula = np.array([board_prob(sn - drop.get((r, ts.hour), 0) * n)
                    for r, ts, sn, n in zip(live_test["route_name"], live_test["seen_at"],
                                            live_test["seats_now"], live_test["n_stops"])])
live_peak = is_peak(live_test).to_numpy()
print(f"\n[실시간 모델 성능 — 다가오는 버스 {LIVE_MAX_STOPS}정거장 앞까지의 기록으로 시험]")
print(" 전체")
report("실시간 모델", live_test["success"], live_prob)
report("기존 공식", live_test["success"], formula)
print(" 평일 출퇴근 시간대만")
report("실시간 모델", live_test["success"][live_peak], live_prob[live_peak])
report("기존 공식", live_test["success"][live_peak], formula[live_peak])
print(" 예측 확률 구간별 실제 탑승 비율 (실시간 모델)")
calibration(live_test["success"], live_prob)

# '만석 위험' 경고 기준 — 시험 기간 결과로 고름 (실패 사례가 적어 데이터가 쌓이면 바뀔 수 있음)
risk_threshold = pick_threshold(live_test["success"], live_prob)
fail = 1 - live_test["success"].to_numpy()
print(f"\n[만석 위험 경고 기준] 탑승 확률 {risk_threshold:.0%} 미만이면 경고")
for th in sorted({0.5, risk_threshold}):
    warn = (live_prob < th).astype(int)
    print(f"  {th:.0%} 기준: 실제 실패 {int(fail.sum())}건 중 {int((warn & fail).sum())}건 잡음 "
          f"(재현율 {recall_score(fail, warn, zero_division=0):.0%}) · "
          f"경고 {int(warn.sum())}건 중 실제 실패 {precision_score(fail, warn, zero_division=0):.0%}")

# ---------- 8. 전체 데이터로 다시 학습해 저장 ----------
model.fit(data[FEATURES], data["success"])
live_model.fit(live[LIVE_FEATURES], live["success"])
bundle = {
    "model": model, "features": FEATURES, "route_map": route_map, "stop_map": stop_map,
    # 추천 계산에 쓰는 실측값: 노선별 배차간격, (노선, 정류장)별 서울까지 이동시간
    "headway": data.groupby("route_name")["headway_min"].median().to_dict(),
    "ride": data.dropna(subset=["ride_min"]).groupby(["route_name", "stop_id"])["ride_min"].median().to_dict(),
    # 실시간 추천용: (노선, 시)별 정류장당 좌석 감소, 노선별 정류장당 이동 시간
    "drop": drop, "min_per_stop": min_per_stop,
    # 돌발 감지용: (노선, 순번)별 평소 정류장당 이동 시간
    "seg_min": seg_min,
    # 실시간 탑승 확률 모델과 '만석 위험' 경고 기준 (live_recommend 가 사용)
    "live_model": live_model, "live_features": LIVE_FEATURES, "risk_threshold": risk_threshold,
}
joblib.dump(bundle, "board_model.pkl")
print("\n모델 저장 완료: board_model.pkl  →  python recommend.py 로 추천을 받아보세요")
