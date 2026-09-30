# ==========================================================
#  노선 ID(routeId)와 정류소 순번 찾기 도우미
#  실행:  python find_ids.py
#  ('경기도_버스노선 조회' API를 사용합니다)
# ==========================================================
import requests
from config import SERVICE_KEY

BASE = "https://apis.data.go.kr/6410000/busrouteservice/v2"


def call(path, **params):
    params.update(serviceKey=SERVICE_KEY, format="json")
    res = requests.get(f"{BASE}/{path}", params=params, timeout=10)
    try:
        return res.json()["response"]["msgBody"]
    except Exception:
        print("응답을 해석하지 못했습니다. 서버가 보낸 원문:")
        print(res.text[:500])
        return None


def as_list(x):
    # 결과가 1개면 리스트가 아니라 딕셔너리로 오는 경우가 있어서 통일
    if x is None:
        return []
    return x if isinstance(x, list) else [x]


keyword = input("노선 번호 검색어 (예: 8800): ").strip()
body = call("getBusRouteListv2", keyword=keyword)
routes = as_list(body.get("busRouteList")) if body else []
for i, r in enumerate(routes):
    print(f"  [{i}] 노선={r.get('routeName')}  routeId={r.get('routeId')}  "
          f"{r.get('startStationName')} → {r.get('endStationName')}")

if routes:
    pick = input("\n정류소 목록을 볼 번호 [0]: ").strip() or "0"
    route = routes[int(pick)]
    body = call("getBusRouteStationListv2", routeId=route["routeId"])
    print(f"\n[{route['routeName']} 정류소 순서]  '순번'을 config.py ROUTES 의 board_seq 에 넣으세요")
    for s in as_list(body.get("busRouteStationList") if body else None):
        print(f"  순번 {s.get('stationSeq'):>3}  {s.get('stationName')}  "
              f"(정류소번호 {s.get('mobileNo', '')})")
