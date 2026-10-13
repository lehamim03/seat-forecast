# ==========================================================
#  앱 개발용 예시 응답(JSON) 만들기 — docs/api.md 의 형식 그대로
#  실행:  python api_examples.py      → docs/examples/*.json
#  demo_scenarios.py 의 가상 상황(평일 07:40)을 실제 추천 로직으로 계산한 결과라,
#  서버가 없어도 앱 화면을 이 파일로 먼저 만들 수 있습니다.
# ==========================================================
import contextlib
import io
import json
import os
from datetime import datetime

OUT = os.path.join("docs", "examples")
RUSH = datetime(2026, 10, 7, 7, 40)


def save(name, data):
    path = os.path.join(OUT, name)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
    print("저장:", path)


def round_coords(obj, nd=5):
    if isinstance(obj, list):
        return [round_coords(x, nd) for x in obj]
    if isinstance(obj, float):
        return round(obj, nd)
    return obj


def main():
    os.makedirs(OUT, exist_ok=True)
    with contextlib.redirect_stdout(io.StringIO()):   # 시나리오 출력은 숨김
        import demo_scenarios
    results = dict(demo_scenarios.RESULTS)
    full = next(r for t, r in results.items() if t.startswith("상황 3"))       # 8800 만석 → 3007
    accident = next(r for t, r in results.items() if t.startswith("상황 4"))   # 판교IC 사고 → 7001

    keep = ("generated_at", "my_route", "home_stop", "day", "recommendation")
    # ② 홈 — 정류장 × 노선 추천 순위
    save("recommendations.json", {**{k: full[k] for k in keep}, "ranking": full["ranking"]})
    # ③ 실시간 대안 — 기다리기 / 걸어가기 / 다른 버스
    save("alternatives.json", {**{k: full[k] for k in keep}, "alternatives": full["alternatives"]})
    # ⑥ 돌발 알림 — 사고로 경로가 바뀌는 경우 + 푸시 알림 내용
    rec = accident["recommendation"]
    save("incidents.json", {
        "generated_at": accident["generated_at"],
        "incidents": accident["incidents"],
        "recommendation": rec,
        "alternatives": accident["alternatives"],
        "push": {"title": f"{accident['my_route']}번 지연 · {rec['route']}번 추천",
                 "body": rec["message"].split(" — ", 1)[-1],
                 "data": {"screen": "alternatives", "route": rec["route"]}},
    })
    # ⑤ 경로 여러 개 — 노선마다 서울에서 내린 뒤 국민대까지 (추천 · 최단시간 · 최소환승)
    import transit
    legs = transit.get_legs(RUSH, fetch=False)
    save("route_choices.json", {
        "generated_at": RUSH.strftime("%Y-%m-%dT%H:%M:%S+09:00"),
        "routes": [{"route": r, "alight_stop": l.get("from"), "minutes_from_seoul_first_stop": l["minutes"],
                    "choices": [{k: v for k, v in ch.items() if k != "map_obj"} for ch in l.get("choices", [])]}
                   for r, l in legs.items()],
    })
    # ④ 지도 — map_data.py 가 만든 GeoJSON
    if os.path.exists("map_data.geojson"):
        with open("map_data.geojson", encoding="utf-8") as f:
            geo = json.load(f)
        for feat in geo["features"]:
            feat["geometry"]["coordinates"] = round_coords(feat["geometry"]["coordinates"])
        save("map.geojson", geo)
    # ⑦ 마이페이지 · 탑승 피드백 (Firestore 문서 형식)
    save("settings.json", {
        "uid": "firebase-uid-예시",
        "home_stop_id": "203000063",
        "candidate_stop_ids": ["203000063", "203000161", "203000162"],
        "my_route": "8800",
        "destination": {"name": "국민대학교", "x": 126.9971, "y": 37.6108},
        "arrive_by": "09:00",
        "notify": {"departure": True, "incident": True, "minutes_before": 10},
    })
    save("feedback.json", {
        "uid": "firebase-uid-예시",
        "date": "2026-10-07",
        "route": "3007",
        "stop_id": "203000161",
        "boarded": True,
        "predicted_prob": 1.0,
        "memo": "",
    })


if __name__ == "__main__":
    main()
