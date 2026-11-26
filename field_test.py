# ==========================================================
#  현장 · 가상 분산 시험 채점 — 학습에 전혀 없던 경기도 다른 지역 노선에서 탑승 확률이 맞았나
#  실행:  python field_test.py                  (data_test/ 가상 분산 + feedback/ 현장 탑승 버튼)
#
#  ① 가상 분산 (collect_test.py 가 평일 아침 기록한 10개 노선)
#     앱이 그 순간 보여 줬을 확률을 기록(지금 좌석 · 남은 정류장 · 시각)으로 똑같이 다시 계산하고,
#     버스가 그 정류장을 떠난 뒤 좌석이 남았는지(= 줄 선 사람이 다 탔는지)와 비교한다. 사람이 없어도 채점된다.
#     대상 정류장: 노선의 경기도 구간(서울 진입 전) 모든 정류장.
#  ② 현장 (팀원이 앱에서 누른 "탔어요 / 못 탔어요" — POST /api/v1/feedback → feedback/feedback.jsonl)
#     앱이 보여 준 확률(predicted_prob)과 실제 탑승 여부.
# ==========================================================
import glob
import json
import os

import joblib
import numpy as np
import pandas as pd
from sklearn.metrics import brier_score_loss, roc_auc_score

import daytype
from config import DEFAULT_DROP_PER_STOP, PEAK_HOURS
from live_recommend import board_prob

SEAT_MARGIN = 1
LIVE_MAX_STOPS = 10
EDGES = (0, 0.5, 0.8, 0.9, 0.95, 1.0001)


def gyeonggi_stops(route_id):
    """노선의 경기도 구간 정류장 순번 (서울 첫 정류장 전까지, 회차 전)"""
    import transit
    st = transit.route_stations(route_id)
    out = []
    for seq in sorted(st):
        if "서울" in st[seq]["region"] or st[seq]["turn"]:
            break
        if not any(w in st[seq]["name"] for w in ("(경유)", "(미정차)")):
            out.append(seq)
    return set(out)


def table(df):
    """기록 → (다가오는 버스의 지금 좌석, 남은 정류장, 시각, 정류장 순번) → 그 정류장에서 탈 수 있었나"""
    df = df[df["remain_seat"] >= 0].sort_values(["plate_no", "collected_at"])
    g = df.groupby(["date", "route_name", "plate_no"])
    new_trip = (g["station_seq"].diff() < 0) | (g["collected_at"].diff() > pd.Timedelta("60min"))
    df = df.assign(trip=new_trip.astype(int).groupby([df["date"], df["route_name"], df["plate_no"]]).cumsum())
    stops = {rid: gyeonggi_stops(str(rid)) for rid in df["route_id"].astype(str).unique()}
    rows = []
    for (date, route, plate, trip), t in df.groupby(["date", "route_name", "plate_no", "trip"]):
        seqs, seats, times = t["station_seq"].to_numpy(), t["remain_seat"].to_numpy(), t["collected_at"].to_numpy()
        for s in sorted(stops[str(t["route_id"].iloc[0])]):
            after = np.nonzero(seqs > s)[0]
            if not len(after):
                continue
            ok = int(seats[after[0]] >= SEAT_MARGIN)
            last = {}
            for q, n, tm in zip(seqs, seats, times):
                if s - LIVE_MAX_STOPS <= q < s:
                    last[q] = (n, tm)          # 정거장마다 마지막으로 보인 좌석
            for q, (n, tm) in last.items():
                rows.append({"date": date, "route_name": route, "stop_seq": s, "n_stops": s - q,
                             "seats_now": n, "seen_at": pd.Timestamp(tm), "success": ok})
    out = pd.DataFrame(rows)
    if out.empty:
        return out
    out["time_min"] = out["seen_at"].dt.hour * 60 + out["seen_at"].dt.minute
    kinds = {d: daytype.day_info(pd.Timestamp(d).date())["day_type"] for d in out["date"].unique()}
    out["day_type"] = out["date"].map(kinds)
    return out


def score(name, y, p):
    y, p = np.asarray(y), np.asarray(p)
    auc = f"{roc_auc_score(y, p):.3f}" if len(set(y)) == 2 else "  -  "
    print(f"  {name:<22} AUC {auc}  Brier {brier_score_loss(y, p, labels=[0, 1]):.4f}  "
          f"평균 예측 {p.mean():.1%} / 실제 {y.mean():.1%}  ({len(y):,}건, 못 탐 {int((y == 0).sum()):,}건)")


def calibration(y, p):
    t = pd.DataFrame({"y": np.asarray(y), "p": np.asarray(p)})
    t["구간"] = pd.cut(t["p"], EDGES, right=False)
    for k, r in t.groupby("구간", observed=True).agg(n=("y", "size"), p=("p", "mean"), y=("y", "mean")).iterrows():
        print(f"    예측 {k.left:.0%}~{min(k.right, 1):.0%}  {int(r['n']):>6}건  예측 평균 {r['p']:.1%}  실제 탄 비율 {r['y']:.1%}")


def virtual():
    files = sorted(glob.glob("data_test/test_seats_*.csv"))
    print(f"\n[① 가상 분산 — 학습에 없던 경기도 노선, 평일 출근 시간] 파일 {len(files)}개")
    if not files:
        print("  data_test/ 에 기록이 아직 없습니다 (collect-test-routes 워크플로가 평일 아침 수집 · git pull)")
        return
    df = pd.concat([pd.read_csv(f, encoding="utf-8-sig", dtype={"route_name": str, "route_id": str}) for f in files])
    df["collected_at"] = pd.to_datetime(df["collected_at"])
    t = table(df)
    if t.empty:
        print("  채점할 운행 기록이 없습니다")
        return
    in_peak = sum((t["time_min"] >= a) & (t["time_min"] < b) for a, b in PEAK_HOURS) > 0
    t = t[in_peak & (t["day_type"] == 0)]
    b = joblib.load("board_model.pkl") if os.path.exists("board_model.pkl") else {}
    methods = {}
    if "generic_model" in b:
        methods["공통 모델 (앱이 쓰는 방식)"] = b["generic_model"].predict_proba(t[b["generic_features"]])[:, 1]
    methods["기존 공식 (데이터 없음)"] = np.array([board_prob(s - DEFAULT_DROP_PER_STOP * n)
                                         for s, n in zip(t["seats_now"], t["n_stops"])])
    methods["평소 탑승률만 (93%)"] = np.full(len(t), 0.93)
    print(f"  날짜 {sorted(t['date'].unique())} · 노선 {t['route_name'].nunique()}개 · 정류장 "
          f"{t.groupby('route_name')['stop_seq'].nunique().sum()}곳")
    for k, p in methods.items():
        score(k, t["success"], p)
    main = next(iter(methods))
    print(f"  예측 확률 구간별 실제 탄 비율 — {main}")
    calibration(t["success"], methods[main])
    print("  노선별 (공통 모델)")
    for r, g in t.groupby("route_name"):
        p = methods[main][(t["route_name"] == r).to_numpy()]
        y = g["success"].to_numpy()
        auc = f"{roc_auc_score(y, p):.3f}" if len(set(y)) == 2 else "  -  "
        print(f"    {r:>8}  {len(g):>6}건  못 탐 {int((y == 0).sum()):>5}  AUC {auc}  예측 {p.mean():.1%} / 실제 {y.mean():.1%}")


def field():
    path = os.path.join("feedback", "feedback.jsonl")
    print("\n[② 현장 — 앱에서 누른 '탔어요 / 못 탔어요']")
    if not os.path.exists(path):
        print("  feedback/feedback.jsonl 이 없습니다 (현장 시험 후 서버 컴퓨터에서 실행)")
        return
    rows = [json.loads(line) for line in open(path, encoding="utf-8") if line.strip()]
    rows = [r for r in rows if r.get("predicted_prob") is not None and "연동 점검" not in (r.get("memo") or "")]
    if not rows:
        print("  확률이 함께 저장된 기록이 없습니다")
        return
    f = pd.DataFrame(rows)
    score("앱이 보여 준 확률", f["boarded"].astype(int), f["predicted_prob"])
    for _, r in f.sort_values("received_at").iterrows():
        print(f"    {r['date']} {r['route']:>8} @{r['stop_id']}  예측 {r['predicted_prob']:.0%}  "
              f"{'탔어요' if r['boarded'] else '못 탔어요'}  {r.get('memo') or ''}")


if __name__ == "__main__":
    virtual()
    field()
