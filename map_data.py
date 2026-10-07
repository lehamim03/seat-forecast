# ==========================================================
#  지도 화면용 데이터 만들기 (GeoJSON) — 앱의 지도(카카오맵·네이버 지도 SDK)에 그릴 것들
#  실행:  python map_data.py              → map_data.geojson + map_preview.html (브라우저로 열어 확인)
#         python map_data.py --no-live    (버스 위치·돌발정보 없이 고정 데이터만)
#
#  담는 것 (feature 의 properties.kind 로 구분)
#   me        내 위치 (지금은 평소 정류장 위치 — 앱에서는 GPS 위치로 대체)
#   stop      후보 정류장 (삼성1차아파트 · 자유총연맹 · 경기아트센터)
#   walk      내 위치 → 후보 정류장 도보 경로 (TMAP 보행자 경로)
#   route     노선 경로 (정류장 · 경유지 좌표를 이은 선, 오늘 고른 서울 하차 정류장까지)
#   bus       지금 다가오는 버스 위치 + 잔여 좌석 (버스위치정보)
#   incident  노선 위 돌발상황 (국가교통정보센터)
#   transfer  서울 하차 정류장 → 국민대 환승 경로 모양 (ODsay)
#  나중에 FastAPI 예측 서버가 앱에 이 형식으로 넘겨주면, 앱은 그리기만 하면 됩니다.
# ==========================================================
import json
import sys

import requests

import api_key
import transit
from config import SERVICE_KEY, ROUTES, STOPS, MY_STOP, DEST_X, DEST_Y

ROUTE_COLORS = {"8800": "#C8102E", "3007": "#1E2126", "7001": "#1E9E5A", "3008": "#6B7280"}


def feature(kind, geom_type, coords, **props):
    return {"type": "Feature", "geometry": {"type": geom_type, "coordinates": coords},
            "properties": {"kind": kind, **props}}


def stop_xy():
    """후보 정류장 좌표 (노선 정류장 목록에서)."""
    xy = {}
    for cfg in ROUTES.values():
        st = transit.route_stations(cfg["route_id"])
        for stop_id, seq in cfg["stops"].items():
            if seq in st:
                xy[stop_id] = (st[seq]["x"], st[seq]["y"])
    return xy


def walk_path(a, b):
    """TMAP 보행자 경로 → 좌표 목록과 시간(분)."""
    res = requests.post("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", timeout=15,
                        headers={"appKey": api_key.TMAP_KEY},
                        json={"startX": a[0], "startY": a[1], "endX": b[0], "endY": b[1],
                              "startName": "start", "endName": "end"}).json()
    coords = []
    for f in res["features"]:
        if f["geometry"]["type"] == "LineString":
            for c in f["geometry"]["coordinates"]:
                if not coords or coords[-1] != c:
                    coords.append(c)
    return coords, round(res["features"][0]["properties"]["totalTime"] / 60)


def transfer_shape(route):
    """ODsay: 오늘 고른 하차 정류장 → 국민대 추천 경로의 지도용 모양 (loadLane)."""
    cfg = ROUTES[route]
    leg = transit.get_legs()[route]
    x, y, _ = transit.station_xy(cfg["route_id"], transit.alight_seq(route))
    data = requests.get("https://api.odsay.com/v1/api/searchPubTransPathT", timeout=15,
                        params={"SX": x, "SY": y, "EX": DEST_X, "EY": DEST_Y, "apiKey": api_key.ODSAY_KEY}).json()
    paths = data["result"]["path"]
    ranked = transit.rank([dict(r, _p=p) for r, p in zip(transit.odsay_routes(x, y, DEST_X, DEST_Y), paths)])
    # get_legs 가 고른 경로와 같은 것을 우선 (없으면 1순위)
    ranked.sort(key=lambda r: r["summary"] != leg["transfer"])
    best = ranked[0]
    lane = requests.get("https://api.odsay.com/v1/api/loadLane", timeout=15, params={
        "mapObject": f"0:0@{best['_p']['info']['mapObj']}", "apiKey": api_key.ODSAY_KEY}).json()
    lines = []
    for ln in lane["result"]["lane"]:
        for sec in ln["section"]:
            lines.append([[float(g["x"]), float(g["y"])] for g in sec["graphPos"]])
    return lines, best["summary"]


def build(live=True):
    feats = []
    xy = stop_xy()
    me = xy[MY_STOP]
    feats.append(feature("me", "Point", list(me), name="내 위치 (앱에서는 GPS)"))

    # 후보 정류장 + 도보 경로
    for stop_id, p in xy.items():
        feats.append(feature("stop", "Point", list(p), name=STOPS[stop_id]["name"],
                             mobile_no=STOPS[stop_id]["mobile_no"], stop_id=stop_id))
        if stop_id != MY_STOP:
            try:
                coords, minutes = walk_path(me, p)
                feats.append(feature("walk", "LineString", coords, to=STOPS[stop_id]["name"], minutes=minutes))
            except Exception as e:
                print(f"  ! 도보 경로 실패 ({STOPS[stop_id]['name']}): {e}")

    # 노선 경로 (출발점 ~ 서울 도착 지점)
    for route, cfg in ROUTES.items():
        st = transit.route_stations(cfg["route_id"])
        end = transit.alight_seq(route)
        line = [[st[s]["x"], st[s]["y"]] for s in sorted(st) if s <= end]
        feats.append(feature("route", "LineString", line, route=route, dest=st[end]["name"], color=ROUTE_COLORS[route]))

    if live:
        # 지금 다가오는 버스 (후보 정류장보다 앞에 있는 차량)
        for route, cfg in ROUTES.items():
            st = transit.route_stations(cfg["route_id"])
            res = requests.get("https://apis.data.go.kr/6410000/buslocationservice/v2/getBusLocationListv2",
                               params={"serviceKey": SERVICE_KEY, "routeId": cfg["route_id"], "format": "json"},
                               timeout=10).json()["response"]
            items = (res.get("msgBody") or {}).get("busLocationList") or []
            for b in items if isinstance(items, list) else [items]:
                seq = int(b["stationSeq"])
                if seq <= transit.alight_seq(route) and seq in st:
                    feats.append(feature("bus", "Point", [st[seq]["x"], st[seq]["y"]], route=route,
                                         plate=b.get("plateNo"), seq=seq, station=st[seq]["name"],
                                         remain_seat=b.get("remainSeatCnt"), color=ROUTE_COLORS[route]))
        # 노선 위 돌발상황
        try:
            import incidents
            events = incidents.fetch_events()
            seen = set()
            for route in ROUTES:
                for s, ev in incidents.events_on_route(route, events):
                    key = (ev.get("coordX"), ev.get("coordY"))
                    if key in seen or not incidents.event_delay(ev):
                        continue
                    seen.add(key)
                    feats.append(feature("incident", "Point", [float(ev["coordX"]), float(ev["coordY"])],
                                         type=ev.get("eventType"), road=ev.get("roadName"),
                                         message=(ev.get("message") or "").strip() or ev.get("eventDetailType")))
        except Exception as e:
            print(f"  ! 돌발정보 실패: {e}")
        # 서울 도착 후 환승 경로 (평소 노선 8800 기준)
        try:
            lines, summary = transfer_shape("8800")
            for ln in lines:
                feats.append(feature("transfer", "LineString", ln, route="8800", summary=summary))
        except Exception as e:
            print(f"  ! 환승 경로 모양 실패 (ODsay IP 등록 확인): {e}")

    return {"type": "FeatureCollection", "features": feats}


PREVIEW = """<!doctype html><html><head><meta charset="utf-8"><title>좌석예보 지도 미리보기</title>
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css">
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>html,body,#m{height:100%;margin:0}</style></head><body><div id="m"></div><script>
const data = __DATA__;
const map = L.map('m');
L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {attribution: '© OpenStreetMap'}).addTo(map);
const layer = L.geoJSON(data, {
  style: f => ({ route: {color: f.properties.color, weight: 4, opacity: .7},
                 walk: {color: '#2563EB', weight: 4, dashArray: '6 6'},
                 transfer: {color: '#7C3AED', weight: 4} }[f.properties.kind] || {}),
  pointToLayer: (f, ll) => {
    const k = f.properties.kind;
    const c = {me: '#2563EB', stop: '#111827', bus: f.properties.color, incident: '#D99A00'}[k];
    return L.circleMarker(ll, {radius: k === 'me' ? 9 : 7, color: '#fff', weight: 2, fillColor: c, fillOpacity: 1});
  },
  onEachFeature: (f, l) => l.bindPopup(Object.entries(f.properties).map(([k, v]) => k + ': ' + v).join('<br>'))
}).addTo(map);
const near = data.features.filter(f => ['me', 'stop', 'walk'].includes(f.properties.kind));
map.fitBounds(L.geoJSON({type: 'FeatureCollection', features: near}).getBounds().pad(0.6));
</script></body></html>"""


if __name__ == "__main__":
    data = build(live="--no-live" not in sys.argv)
    with open("map_data.geojson", "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False)
    with open("map_preview.html", "w", encoding="utf-8") as f:
        f.write(PREVIEW.replace("__DATA__", json.dumps(data, ensure_ascii=False)))
    kinds = {}
    for ft in data["features"]:
        kinds[ft["properties"]["kind"]] = kinds.get(ft["properties"]["kind"], 0) + 1
    print("저장: map_data.geojson, map_preview.html (브라우저로 열어 확인)")
    print("  ", ", ".join(f"{k} {v}개" for k, v in kinds.items()))
