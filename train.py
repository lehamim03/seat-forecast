# ==========================================================
#  탑승 성공 확률 예측 모델 (Random Forest) — 노선 × 후보 정류장
#  데이터가 최소 2~3주(평일 10일 이상) 쌓인 뒤 실행하세요.
#  준비:  pip install pandas scikit-learn joblib
#  실행:  python train.py   →  board_model.pkl 저장 (recommend.py 가 사용)
# ==========================================================
import sys

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, roc_auc_score

from config import CSV_FILE, ROUTES, STOPS

# 정류장을 떠난 직후 잔여 좌석이 이 숫자 이상이면 '탑승 성공'.
# 1석이라도 남기고 떠났다 = 그 정류장에 줄 선 사람이 전부 탔다는 뜻.
# (더 보수적으로 보고 싶으면 2~3으로 올리세요)
SEAT_MARGIN = 1

# ---------- 1. 불러오기 ----------
df = pd.read_csv(CSV_FILE, encoding="utf-8-sig", dtype={"route_name": str})
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
rows = []
for (date, route, plate, trip), t in df.groupby(["date", "route_name", "plate_no", "trip"]):
    cfg = ROUTES[route]
    arrived = t[t["station_seq"] >= cfg["dest_seq"]]   # 서울 도착 지점에 닿은 기록
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

data = pd.DataFrame(rows)
if data.empty:
    sys.exit("분석할 운행 기록이 없습니다. 수집이 제대로 되었는지 CSV를 확인하세요.")

data["time_min"] = data["depart_at"].dt.hour * 60 + data["depart_at"].dt.minute

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
FEATURES = ["route_code", "stop_code", "weekday", "time_min", "headway_min"]

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

model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5,
                               class_weight="balanced", random_state=42)
model.fit(train[FEATURES], train["success"])

# ---------- 6. 평가 ----------
prob = model.predict_proba(test[FEATURES])[:, 1]
print("[모델 성능 — 학습에 안 쓴 최근 날짜로 시험]")
print(classification_report(test["success"], (prob >= 0.5).astype(int),
                            labels=[0, 1], target_names=["탑승실패", "탑승성공"],
                            zero_division=0))
if test["success"].nunique() == 2:
    print(f"AUC: {roc_auc_score(test['success'], prob):.3f}  (0.5=찍기, 1.0=완벽)")

# 비교 기준(baseline): '노선 + 정류장 + 10분 단위 시간대'의 과거 평균 성공률
keys = ["route_name", "stop_id", "slot"]
slot_rate = train.assign(slot=train["time_min"] // 10).groupby(keys)["success"].mean()
base = [slot_rate.get((r, s, t // 10), train["success"].mean())
        for r, s, t in zip(test["route_name"], test["stop_id"], test["time_min"])]
base_acc = ((pd.Series(base, index=test.index) >= 0.5).astype(int) == test["success"]).mean()
print(f"단순 평균 방식 정확도: {base_acc:.1%}  ← 모델이 이것보다 나아야 의미 있음\n")

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

# ---------- 8. 전체 데이터로 다시 학습해 저장 ----------
model.fit(data[FEATURES], data["success"])
bundle = {
    "model": model, "features": FEATURES, "route_map": route_map, "stop_map": stop_map,
    # 추천 계산에 쓰는 실측값: 노선별 배차간격, (노선, 정류장)별 서울까지 이동시간
    "headway": data.groupby("route_name")["headway_min"].median().to_dict(),
    "ride": data.dropna(subset=["ride_min"]).groupby(["route_name", "stop_id"])["ride_min"].median().to_dict(),
    # 실시간 추천용: (노선, 시)별 정류장당 좌석 감소, 노선별 정류장당 이동 시간
    "drop": drop, "min_per_stop": min_per_stop,
}
joblib.dump(bundle, "board_model.pkl")
print("\n모델 저장 완료: board_model.pkl  →  python recommend.py 로 추천을 받아보세요")
