# ==========================================================
#  다른 노선 · 다른 정류장에도 통할까? — 일반화 시험 (처음 보는 노선 · 정류장으로 시험)
#  실행:  python generalize.py
#
#  우리 데이터는 집(수원 매탄동) → 국민대 한 경로뿐이라, 다른 곳에서 직접 시험할 수 없다.
#  대신 노선(또는 정류장) 하나를 학습에서 완전히 빼고, 그 노선을 '처음 보는 노선'처럼 맞혀 본다.
#    · 전용 모델        : 지금 서비스 모델 (노선 · 정류장 번호 포함, 그 노선 데이터로도 학습) — 비교용 상한
#    · 공통 모델        : 노선 · 정류장 번호 없이 실시간 API 에서 바로 얻는 값만 (지금 좌석 · 남은 정류장 · 시각 · 요일 · 정류장 위치)
#    · 공통 + 짧은 수집 : 새 노선을 평일 5일만 모았다고 보고, 그 노선의 '정류장당 좌석 감소'를 더함
#    · 기존 공식        : 데이터 없이 쓰는 기본 공식 (정류장당 2석 감소 가정)
#  시험은 모두 학습에 안 쓴 최근 날짜(뒤쪽 20%)로 — train.py 와 같은 날짜 나누기.
# ==========================================================
import glob
import sys

import numpy as np
import pandas as pd
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import brier_score_loss, roc_auc_score

import daytype
from config import DEFAULT_DROP_PER_STOP, PEAK_HOURS, ROUTES, STOPS
from live_recommend import board_prob

SEAT_MARGIN = 1        # train.py 와 같음
LIVE_MAX_STOPS = 10
SHORT_DAYS = 5         # '짧은 수집': 새 노선을 평일 며칠 모았다고 볼지
SHORT_STEPS = (3, 5, 10)   # 새 노선 · 정류장을 평일 며칠 모아 학습에 넣었을 때를 비교
MIN_FAILS = 10         # 실패가 이보다 적은 노선은 AUC 를 믿기 어려워 표시만

GENERIC = ["n_stops", "seats_now", "time_min", "day_type", "stop_seq"]
GENERIC_SHORT = GENERIC + ["seats_proj"]
SPECIFIC = ["route_code", "stop_code", "n_stops", "seats_now", "time_min", "day_type"]


def load():
    files = sorted(glob.glob("data/bus_seats_*.csv"))
    if not files:
        sys.exit("data/ 에 수집 파일이 없습니다 (git pull).")
    df = pd.concat([pd.read_csv(f, encoding="utf-8-sig", dtype={"route_name": str}) for f in files]).drop_duplicates()
    df["collected_at"] = pd.to_datetime(df["collected_at"])
    df = df[(df["remain_seat"] >= 0) & df["route_name"].isin(ROUTES)].sort_values(["plate_no", "collected_at"])
    g = df.groupby(["date", "route_name", "plate_no"])
    new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
    df["trip"] = new_trip.astype(int).groupby([df["date"], df["route_name"], df["plate_no"]]).cumsum()
    print(f"데이터 파일 {len(files)}개 · {len(df):,}행")
    return df


def live_table(df):
    """train.py 의 실시간 모델 학습표와 같음: 다가오는 버스의 지금 좌석 → 그 정류장에서 탈 수 있었나"""
    rows = []
    for (date, route, plate, trip), t in df.groupby(["date", "route_name", "plate_no", "trip"]):
        for stop_id, seq in ROUTES[route]["stops"].items():
            before, after = t[t["station_seq"] <= seq], t[t["station_seq"] > seq]
            if after.empty:
                continue
            ok = int(after.iloc[0]["remain_seat"] >= SEAT_MARGIN)
            ahead = before[(before["station_seq"] < seq) & (before["station_seq"] >= seq - LIVE_MAX_STOPS)]
            for _, o in ahead.groupby("station_seq").tail(1).iterrows():
                rows.append({"date": date, "route_name": route, "stop_id": stop_id, "stop_seq": seq,
                             "seen_at": o["collected_at"], "n_stops": seq - o["station_seq"],
                             "seats_now": o["remain_seat"], "success": ok})
    live = pd.DataFrame(rows)
    live["time_min"] = live["seen_at"].dt.hour * 60 + live["seen_at"].dt.minute
    kinds = {d: daytype.day_info(pd.Timestamp(d).date())["day_type"] for d in live["date"].unique()}
    live["day_type"] = live["date"].map(kinds)
    live["route_code"] = live["route_name"].map({r: i for i, r in enumerate(sorted(ROUTES))})
    live["stop_code"] = live["stop_id"].map({s: i for i, s in enumerate(sorted(STOPS))})
    live["hour"] = live["seen_at"].dt.hour
    return live, kinds


def drop_table(df, dates):
    """노선 · 시간대별 정류장당 좌석 감소(중앙값) — 주어진 날짜의 기록만으로"""
    t = df[df["date"].isin(dates)].copy()
    key = ["date", "route_name", "plate_no", "trip"]
    t["d_seq"] = t.groupby(key)["station_seq"].diff()
    t["d_seat"] = t.groupby(key)["remain_seat"].diff()
    near_max = {r: max(c["stops"].values()) for r, c in ROUTES.items()}
    t = t[(t["d_seq"] > 0) & (t["station_seq"] <= t["route_name"].map(near_max))]
    return (-t["d_seat"] / t["d_seq"]).clip(lower=0).groupby([t["route_name"], t["collected_at"].dt.hour]).median()


def add_proj(frame, drop):
    d = np.array([drop.get((r, h), DEFAULT_DROP_PER_STOP) for r, h in zip(frame["route_name"], frame["hour"])])
    return frame.assign(seats_proj=frame["seats_now"] - d * frame["n_stops"])


def is_peak(frame):
    in_peak = sum((frame["time_min"] >= a) & (frame["time_min"] < b) for a, b in PEAK_HOURS) > 0
    return (in_peak & (frame["day_type"] == 0)).to_numpy()


def score(y, p):
    y, p = np.asarray(y), np.asarray(p)
    fails = int((y == 0).sum())
    auc = roc_auc_score(y, p) if len(set(y)) == 2 else np.nan
    return {"건수": len(y), "실패": fails, "AUC": auc, "Brier": brier_score_loss(y, p, labels=[0, 1]),
            "예측": p.mean(), "실제": y.mean()}


def rf():
    return RandomForestClassifier(n_estimators=300, min_samples_leaf=5, random_state=42, n_jobs=-1)


def holdout(live, df, cut, weekdays, col, value):
    """col == value 인 노선(정류장)을 학습에서 빼고 시험. 반환: 방식별 점수 (평일 출퇴근 시간대)"""
    train, test = live[live["date"] < cut], live[live["date"] >= cut]
    test_h = test[test[col] == value]
    peak = is_peak(test_h)
    if peak.sum() == 0:
        return None
    seen = train[train[col] != value]                     # 빼낸 노선 · 정류장은 전혀 안 봄
    short_days = [d for d in weekdays if d < cut][:SHORT_DAYS]
    drop_seen = drop_table(df, [d for d in weekdays if d < cut])   # 학습 기간 평일 (빼낸 노선 값은 아래에서 교체)
    drop_short = drop_table(df, short_days)             # 새 노선은 평일 5일치만으로 감소 속도 추정
    drop_test = drop_seen.copy()
    if col == "route_name":
        drop_test = drop_test[drop_test.index.get_level_values(0) != value]
        drop_test = pd.concat([drop_test, drop_short[drop_short.index.get_level_values(0) == value]])

    out = {}
    m = rf().fit(train[SPECIFIC], train["success"])     # 전용 모델 (그 노선도 학습)
    out["전용 모델 (상한)"] = m.predict_proba(test_h[SPECIFIC])[:, 1]
    m = rf().fit(seen[GENERIC], seen["success"])
    out["공통 모델"] = m.predict_proba(test_h[GENERIC])[:, 1]
    seen_p = add_proj(seen, drop_seen)
    m = rf().fit(seen_p[GENERIC_SHORT], seen_p["success"])
    out["공통 + 좌석 감소만 5일"] = m.predict_proba(add_proj(test_h, drop_test)[GENERIC_SHORT])[:, 1]
    # 새 노선 · 정류장을 평일 k일만 모아 학습에 넣었을 때 — 실제로 새 경로를 시작하는 상황
    for k in SHORT_STEPS:
        first_k = [d for d in weekdays if d < cut][:k]
        part = pd.concat([seen, train[(train[col] == value) & train["date"].isin(first_k)]])
        m = rf().fit(part[SPECIFIC], part["success"])
        out[f"새 곳 평일 {k}일 학습"] = m.predict_proba(test_h[SPECIFIC])[:, 1]
    out["기존 공식 (데이터 없음)"] = np.array([board_prob(s - DEFAULT_DROP_PER_STOP * n)
                                         for s, n in zip(test_h["seats_now"], test_h["n_stops"])])
    return {k: score(test_h["success"][peak], p[peak]) for k, p in out.items()}


def show(title, results):
    print(f"\n[{title}] — 평일 출퇴근 시간대, 학습에 안 쓴 최근 날짜")
    print(f"  {'빼낸 것':<16}{'방식':<22}{'건수':>6}{'실패':>5}{'AUC':>7}{'Brier':>8}{'예측':>7}{'실제':>7}")
    summary = {}
    for name, res in results:
        if res is None:
            continue
        for k, s in res.items():
            auc = f"{s['AUC']:.3f}" if s["실패"] >= MIN_FAILS and not np.isnan(s["AUC"]) else "  -  "
            print(f"  {name:<16}{k:<22}{s['건수']:>6}{s['실패']:>5}{auc:>7}{s['Brier']:>8.4f}"
                  f"{s['예측']:>7.1%}{s['실제']:>7.1%}")
            if s["실패"] >= MIN_FAILS:
                summary.setdefault(k, []).append(s)
        print()
    print("  평균 (실패 10건 이상인 경우만):")
    for k, ss in summary.items():
        n = sum(s["건수"] for s in ss)
        print(f"    {k:<22} AUC {np.mean([s['AUC'] for s in ss]):.3f}  "
              f"Brier {sum(s['Brier'] * s['건수'] for s in ss) / n:.4f}  ({len(ss)}개)")


def main():
    df = load()
    live, kinds = live_table(df)
    dates = sorted(live["date"].unique())
    cut = dates[int(len(dates) * 0.8)]
    weekdays = [d for d in dates if kinds[d] == 0]
    print(f"학습 {dates[0]} ~ {cut} 전 · 시험 {cut} ~ {dates[-1]} · 실시간 기록 {len(live):,}건")

    show("처음 보는 노선", [(f"{r}번", holdout(live, df, cut, weekdays, "route_name", r)) for r in sorted(ROUTES)])
    show("처음 보는 정류장", [(STOPS[s]["name"][:12], holdout(live, df, cut, weekdays, "stop_id", s)) for s in STOPS])


if __name__ == "__main__":
    main()
