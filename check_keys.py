# ==========================================================
#  지금 내 공인 IP 확인 + 외부 API 키가 작동하는지 점검
#  실행:  python check_keys.py
#  ODsay(Server 플랫폼)는 등록한 공인 IP에서만 작동합니다.
#  와이파이를 옮기면 IP가 바뀌니, 인증 오류가 나면 먼저 이걸 실행하세요.
# ==========================================================
import requests

import api_key

ip = requests.get("https://api.ipify.org", timeout=10).text
print(f"지금 내 공인 IP: {ip}   ← ODsay 'Server' 플랫폼에 이 값을 등록하세요")
print("  (192.168.x.x 같은 내부 IP가 아닙니다)\n")

checks = []

# 공공데이터포털 — 버스 노선 조회
r = requests.get("https://apis.data.go.kr/6410000/busrouteservice/v2/getBusRouteListv2",
                 params={"serviceKey": api_key.SERVICE_KEY, "keyword": "8800", "format": "json"}, timeout=10)
checks.append(("공공데이터포털", '"resultCode":0' in r.text.replace(" ", ""), r.text[:120]))

# ODsay — 서울역 → 국민대 대중교통 길찾기
key = getattr(api_key, "ODSAY_KEY", "")
if key:
    r = requests.get("https://api.odsay.com/v1/api/searchPubTransPathT", timeout=15,
                     params={"SX": 126.9723, "SY": 37.5550, "EX": 126.9971, "EY": 37.6108, "apiKey": key})
    checks.append(("ODsay", '"result"' in r.text and '"error"' not in r.text, r.text[:120]))

# TMAP — 보행자 경로 (짧은 구간)
key = getattr(api_key, "TMAP_KEY", "")
if key:
    r = requests.post("https://apis.openapi.sk.com/tmap/routes/pedestrian?version=1", timeout=15,
                      headers={"appKey": key},
                      json={"startX": 127.0410, "startY": 37.2660, "endX": 127.0440, "endY": 37.2680,
                            "startName": "start", "endName": "end"})
    checks.append(("TMAP", r.status_code == 200, r.text[:120]))

for name, ok, detail in checks:
    print(f"{'✅' if ok else '❌'} {name}" + ("" if ok else f"\n    응답: {detail}"))
