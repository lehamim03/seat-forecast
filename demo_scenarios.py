# ==========================================================
#  실시간 대안 안내 시연 — 출근 시간 가상 상황 4가지
#  실행:  python demo_scenarios.py
#  실제 API 대신 가상의 버스 위치·잔여 좌석(과 돌발 상황 기록)을 넣어
#  live_recommend.py 의 판단을 보여줍니다.
# ==========================================================
from datetime import datetime, timedelta

import pandas as pd

import live_recommend as L
import transit
from config import ROUTES

L.USE_MODEL = False   # 학습 결과와 상관없이 항상 같은 결과가 나오도록 기본값 사용
L.USE_TRANSIT = False # 환승·도보 시간도 config 값으로 고정

R8800, R3007, R7001, R3008 = "200000205", "200000110", "200000119", "200000274"
NO_INCIDENT = pd.DataFrame(columns=["collected_at", "route_name", "plate_no", "station_seq"])


def seq_of(route, keyword):
    """노선에서 이름에 keyword 가 들어간 정류장(경유지)의 순번."""
    st = transit.route_stations(ROUTES[route]["route_id"])
    return next(s for s, v in sorted(st.items()) if keyword in v["name"])


def stalled(route, plate, keyword, minutes):
    """keyword 지점에 minutes 분째 멈춰 있는 버스의 최근 1분 간격 기록."""
    now, seq = datetime.now(), seq_of(route, keyword)
    return [{"collected_at": now - timedelta(minutes=m), "route_name": route, "plate_no": plate,
             "station_seq": seq if m <= minutes else seq - 1} for m in range(0, 20 + 1)] + \
           [{"collected_at": now - timedelta(minutes=minutes), "route_name": route, "plate_no": plate,
             "station_seq": seq}]


def moving(route, plate, keyword, n_stops):
    """keyword 지점부터 평소 속도로 n_stops 정류장을 지나온 버스의 기록."""
    now, seq = datetime.now(), seq_of(route, keyword)
    return [{"collected_at": now - timedelta(minutes=2 * (n_stops - i)), "route_name": route,
             "plate_no": plate, "station_seq": seq + i} for i in range(n_stops + 1)]


SCENARIOS = [
    ("상황 1 — 첫 8800은 만석, 바로 뒤 8800은 여유  →  기다리기", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 6, "remainSeatCnt": 3},
                {"plateNo": "8800-B", "stationSeq": 2, "remainSeatCnt": 30}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 7, "remainSeatCnt": 12}],
        R7001: [{"plateNo": "7001-A", "stationSeq": 9, "remainSeatCnt": 1}],
        R3008: [],
    }, None),
    ("상황 2 — 집 앞에선 만석 위험, 한 정거장 앞에선 자리 있음  →  걸어가기", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 1, "remainSeatCnt": 15},
                {"plateNo": "8800-X", "stationSeq": 7, "remainSeatCnt": 0}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 9, "remainSeatCnt": 2}],
        R7001: [], R3008: [],
    }, None),
    ("상황 3 — 8800은 전부 만석, 3007은 여유  →  다른 버스", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 5, "remainSeatCnt": 1},
                {"plateNo": "8800-B", "stationSeq": 2, "remainSeatCnt": 4}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 8, "remainSeatCnt": 25}],
        R7001: [], R3008: [],
    }, None),
    ("상황 4 — 경부고속도로 판교IC 부근 사고: 8800·3007·3008 앞차 정지  →  다른 버스(7001, 과천 경유)", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 6, "remainSeatCnt": 25}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 8, "remainSeatCnt": 20}],
        R7001: [{"plateNo": "7001-A", "stationSeq": 9, "remainSeatCnt": 20}],
        R3008: [{"plateNo": "3008-A", "stationSeq": 5, "remainSeatCnt": 20}],
    }, lambda: pd.DataFrame(
        stalled("8800", "8800-P", "판교IC", 35) + stalled("3007", "3007-P", "판교IC", 35)
        + stalled("3008", "3008-P", "판교IC", 35) + moving("7001", "7001-P", "의왕톨게이트", 4))),
    ("상황 5 — 집회로 8800이 우회해 삼성1차아파트·경기아트센터를 건너뜀  →  다른 버스", {
        R8800: [{"plateNo": "8800-A", "stationSeq": 5, "remainSeatCnt": 30}],
        R3007: [{"plateNo": "3007-A", "stationSeq": 8, "remainSeatCnt": 10}],
        R7001: [{"plateNo": "7001-A", "stationSeq": 9, "remainSeatCnt": 3}],
        R3008: [{"plateNo": "3008-A", "stationSeq": 5, "remainSeatCnt": 22}],
    }, lambda: pd.DataFrame([   # 앞서 간 8800이 1분 만에 6번째 → 10번째 정류장으로 건너뜀
        {"collected_at": datetime.now() - timedelta(minutes=3), "route_name": "8800", "plate_no": "8800-P", "station_seq": 6},
        {"collected_at": datetime.now() - timedelta(minutes=2), "route_name": "8800", "plate_no": "8800-P", "station_seq": 10},
        {"collected_at": datetime.now() - timedelta(minutes=1), "route_name": "8800", "plate_no": "8800-P", "station_seq": 11},
    ])),
]

for title, buses, incident in SCENARIOS:
    print("=" * 70 + "\n" + title + "\n" + "=" * 70)
    L.get = lambda path, _b=buses, **p: _b[p["routeId"]] if "location" in path else []
    L.INCIDENT_RECENT = incident() if incident else NO_INCIDENT
    L.main()
    print()
