# ==========================================================
#  실시간 대안 안내 시연 — 출근 시간 가상 상황 3가지
#  실행:  python demo_scenarios.py
#  실제 API 대신 가상의 버스 위치·잔여 좌석을 넣어 live_recommend.py 의 판단을 보여줍니다.
# ==========================================================
import live_recommend as L

L.USE_MODEL = False   # 학습 결과와 상관없이 항상 같은 결과가 나오도록 기본값 사용
L.USE_TRANSIT = False # 환승·도보 시간도 config 값으로 고정

R8800, R3007, R7001, R3008 = "200000205", "200000110", "200000119", "200000274"

SCENARIOS = [
    ("상황 1 — 첫 8800은 만석, 바로 뒤 8800은 여유  →  기다리기", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 6, "remainSeatCnt": 3},
                {"plateNo": "8800-B", "stationSeq": 2, "remainSeatCnt": 30}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 7, "remainSeatCnt": 12}],
        R7001: [{"plateNo": "7001-A", "stationSeq": 9, "remainSeatCnt": 1}],
        R3008: [],
    }),
    ("상황 2 — 집 앞에선 만석 위험, 한 정거장 앞에선 자리 있음  →  걸어가기", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 1, "remainSeatCnt": 15},
                {"plateNo": "8800-X", "stationSeq": 7, "remainSeatCnt": 0}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 9, "remainSeatCnt": 2}],
        R7001: [], R3008: [],
    }),
    ("상황 3 — 8800은 전부 만석, 3007은 여유  →  다른 버스", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 5, "remainSeatCnt": 1},
                {"plateNo": "8800-B", "stationSeq": 2, "remainSeatCnt": 4}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 8, "remainSeatCnt": 25}],
        R7001: [], R3008: [],
    }),
]

for title, buses in SCENARIOS:
    print("=" * 70 + "\n" + title + "\n" + "=" * 70)
    L.get = lambda path, _b=buses, **p: _b[p["routeId"]] if "location" in path else []
    L.main()
    print()
