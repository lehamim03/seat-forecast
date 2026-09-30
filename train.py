# ==========================================================
#  탑승 성공 확률 예측 모델 (Random Forest)
#  데이터가 최소 2~3주(평일 10일 이상) 쌓인 뒤 실행하세요.
#  준비:  pip install pandas scikit-learn joblib
#  실행:  python train.py
# ==========================================================
import sys

import joblib
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import classification_report, roc_auc_score

from config import CSV_FILE, ROUTES

# 내 정류소를 떠난 직후 잔여 좌석이 이 숫자 이상이면 '탑승 성공'.
# 1석이라도 남기고 떠났다 = 줄 선 사람이 전부 탔다는 뜻.
# (더 보수적으로 보고 싶으면 2~3으로 올리세요)
SEAT_MARGIN = 1

# ---------- 1. 불러오기 ----------
df = pd.read_csv(CSV_FILE, encoding="utf-8-sig", dtype={"route_name": str})
df["collected_at"] = pd.to_datetime(df["collected_at"])
df = df[df["remain_seat"] >= 0].sort_values(["plate_no", "collected_at"])  # -1(정보 없음) 제거

# ---------- 2. 버스 '운행 1회' 단위로 묶기 ----------
# 같은 차량이 아침에 두 번 출발할 수도 있으므로,
# 정류소 순번이 뒤로 돌아가거나 1시간 넘게 안 보이면 새 운행으로 본다.
g = df.groupby(["date", "route_name", "plate_no"])
new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
df["trip"] = new_trip.astype(int).groupby([df["date"], df["route_name"], df["plate_no"]]).cumsum()

trips = []
for key, t in df.groupby(["date", "route_name", "plate_no", "trip"]):
    board_seq = ROUTES[key[1]]["board_seq"]   # 노선마다 탑승 정류소 순번이 다름
    before = t[t["station_seq"] <= board_seq]
    after = t[t["station_seq"] > board_seq]
    if after.empty:
        continue  # 내 정류소를 떠나는 모습이 기록되지 않음
    first_after = after.iloc[0]
    # 출발 시각: 내 정류소에서 마지막으로 보인 시각 (없으면 떠난 뒤 처음 보인 시각)
    dep = before["collected_at"].iloc[-1] if not before.empty else first_after["collected_at"]
    trips.append({
        "date": key[0], "route_name": key[1], "weekday": first_after["weekday"],
        "depart_at": dep, "seats_after": first_after["remain_seat"],
    })

trips = pd.DataFrame(trips)
if trips.empty:
    sys.exit("분석할 운행 기록이 없습니다. 수집이 제대로 되었는지 CSV를 확인하세요.")

trips["time_min"] = trips["depart_at"].dt.hour * 60 + trips["depart_at"].dt.minute

# 같은 노선 앞차와의 간격(배차간격) — 간격이 길수록 사람이 많이 몰림
trips = trips.sort_values("depart_at")
trips["headway_min"] = (trips.groupby(["date", "route_name"])["depart_at"]
                        .diff().dt.total_seconds() / 60)
trips["headway_min"] = trips["headway_min"].fillna(trips["headway_min"].median())

# 정답(label)
trips["success"] = (trips["seats_after"] >= SEAT_MARGIN).astype(int)

# ---------- 3. 입력(feature) 만들기 ----------
trips["route_code"] = trips["route_name"].astype("category").cat.codes
route_map = dict(zip(trips["route_name"], trips["route_code"]))
FEATURES = ["route_code", "weekday", "time_min", "headway_min"]

print(f"운행 기록 {len(trips)}건, 수집 일수 {trips['date'].nunique()}일")
print(f"전체 탑승 성공률(좌석 남기고 출발한 비율): {trips['success'].mean():.1%}\n")

dates = sorted(trips["date"].unique())
if len(dates) < 5:
    sys.exit("수집 일수가 너무 적습니다. 최소 평일 10일 이상 모은 뒤 다시 실행하세요.")

# ---------- 4. 날짜 기준으로 학습/시험 나누기 ----------
# 무작위로 섞으면 같은 날 데이터가 양쪽에 들어가 성능이 부풀려진다.
# '과거로 학습 → 미래를 맞히기' 구조로 나눈다.
cut = dates[int(len(dates) * 0.8)]
train, test = trips[trips["date"] < cut], trips[trips["date"] >= cut]

model = RandomForestClassifier(n_estimators=300, min_samples_leaf=5,
                               class_weight="balanced", random_state=42)
model.fit(train[FEATURES], train["success"])

# ---------- 5. 평가 ----------
prob = model.predict_proba(test[FEATURES])[:, 1]
print("[모델 성능 — 학습에 안 쓴 최근 날짜로 시험]")
print(classification_report(test["success"], (prob >= 0.5).astype(int),
                            labels=[0, 1], target_names=["탑승실패", "탑승성공"],
                            zero_division=0))
if test["success"].nunique() == 2:
    print(f"AUC: {roc_auc_score(test['success'], prob):.3f}  (0.5=찍기, 1.0=완벽)")

# 비교 기준(baseline): '노선+10분 단위 시간대'의 과거 평균 성공률
slot_rate = train.assign(slot=train["time_min"] // 10).groupby(["route_name", "slot"])["success"].mean()
base = [slot_rate.get((r, t // 10), train["success"].mean())
        for r, t in zip(test["route_name"], test["time_min"])]
base_acc = ((pd.Series(base, index=test.index) >= 0.5).astype(int) == test["success"]).mean()
print(f"단순 평균 방식 정확도: {base_acc:.1%}  ← 모델이 이것보다 나아야 의미 있음\n")

print("[무엇이 중요했나]")
for f, imp in sorted(zip(FEATURES, model.feature_importances_), key=lambda x: -x[1]):
    print(f"  {f:12s} {imp:.2f}")

# ---------- 6. 전체 데이터로 다시 학습해 저장 ----------
model.fit(trips[FEATURES], trips["success"])
headway = trips.groupby("route_name")["headway_min"].median().to_dict()
joblib.dump({"model": model, "route_map": route_map, "features": FEATURES,
             "headway": headway}, "board_model.pkl")
print("\n모델 저장 완료: board_model.pkl")


# ---------- 7. 활용 예시: 월요일 07:00~08:00 노선별 탑승 확률 ----------
def board_prob(route, weekday, hh, mm):
    x = pd.DataFrame([{"route_code": route_map[route], "weekday": weekday,
                       "time_min": hh * 60 + mm, "headway_min": headway[route]}])
    return model.predict_proba(x[FEATURES])[0, 1]


print("\n[예시] 월요일 출발 시각별 탑승 성공 확률")
for route in route_map:
    line = "  ".join(f"{h:02d}:{m:02d} {board_prob(route, 0, h, m):.0%}"
                     for h, m in [(7, 0), (7, 30), (8, 0)])
    print(f"  {route:>6}: {line}")
