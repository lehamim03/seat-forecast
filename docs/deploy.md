# 서버 배포

ODsay 는 **등록한 공인 IP 에서만** 작동합니다. 학교 네트워크는 공인 IP 가 `113.198.219.x` 안에서 계속 바뀌어(.112 → .114 → .115) 며칠마다 다시 막힙니다.
그래서 IP 가 바뀌지 않는 곳에 서버를 두고 그 IP 를 한 번만 등록하는 것이 배포 계획입니다.

| 방법 | 언제 | IP | 휴대폰(LTE) 접속 |
|---|---|---|---|
| **A. 무료 클라우드 VM (Oracle Cloud Always Free 등)** | 발표 · 이후 계속 | 고정 공인 IP | `http://<VM IP>:8000/` |
| **B. 이 PC + Cloudflare Tunnel** | 급할 때 · 현장 시험 아침 | 이 PC 의 공인 IP (바뀌면 ODsay 재등록) | `https://….trycloudflare.com` |

## A. 클라우드 VM (우분투 22.04 · 무료 등급 1GB 이상)

0. 메모리: 서버 실행에 약 600MB (모델 125MB 포함), 서버에서 train.py 까지 돌리면 1~2GB.
   - Oracle 은 **Ampere A1 (2 OCPU · 12GB)** 권장, 자리가 없으면 VM.Standard.E2.1.Micro(1GB) + 아래 스왑 + 모델은 복사
   - 홈 지역은 가입 때 **South Korea Central (Seoul)** — 나중에 못 바꿈
   - 1GB 서버면 먼저 스왑 2GB:
     ```bash
     sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
     echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
     ```
1. VM 만들기 → **고정(Reserved) 공인 IP** 지정 → 방화벽(보안 목록)에서 TCP 8000 열기
   (Oracle 우분투는 서버 안 방화벽도 막혀 있음: `sudo iptables -I INPUT -p tcp --dport 8000 -j ACCEPT && sudo netfilter-persistent save`)
2. ODsay 콘솔 Server 플랫폼에 그 IP 등록
3. VM 에서:
   ```bash
   sudo apt update && sudo apt install -y python3-venv git
   git clone https://github.com/lehamim03/seat-forecast.git && cd seat-forecast
   python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
   ```
4. **비밀 · 저장 파일 옮기기** (git 에 없음) — 이 PC 에서:
   ```powershell
   scp api_key.py odsay_cache.json transit_cache.json trip_odsay_cache.json gg_stations.json daytype_cache.json events_cache.json nearby_stops.json ubuntu@<VM IP>:~/seat-forecast/
   ```
   - `odsay_cache.json` · `trip_odsay_cache.json` 은 ODsay 결과 저장분 — ODsay 가 잠깐 막혀도 저장된 경로로 계속 답함
   - `gg_stations.json` 이 없으면 첫 요청 때 경기도 정류장 목록(약 35쪽)을 받느라 느림
5. 모델: VM 에서 `.venv/bin/python train.py` (data/ 가 저장소에 있음, 1~3분) — 또는 `scp board_model.pkl`
   (scikit-learn 버전이 같아야 함: requirements.txt 고정값)
6. 확인: `.venv/bin/python check_keys.py` → 4개 모두 ✅, `.venv/bin/python -m unittest discover tests`
7. 자동 실행: [deploy/seat-forecast.service](../deploy/seat-forecast.service) 의 설치 방법대로
8. 앱 SERVER_BASE_URL 을 `http://<VM IP>:8000/` 로 (안드로이드는 http 를 막으니 시연 앱의 `usesCleartextTraffic="true"` 유지, 또는 B 처럼 https 주소)
9. 데이터 갱신: `git pull && .venv/bin/python train.py && sudo systemctl restart seat-forecast` (주 1회)

## B. 이 PC + Cloudflare Tunnel (계정 없이 임시 https 주소)

```powershell
winget install --id Cloudflare.cloudflared      # 처음 한 번
python server.py                                # 창 1
cloudflared tunnel --url http://localhost:8000  # 창 2 → https://….trycloudflare.com 주소가 나옴
```
- 주소는 실행할 때마다 바뀜 → 시험 · 발표 날 아침에 앱 설정에 넣기
- 누구나 들어올 수 있는 주소이므로 쓰는 동안 `FIREBASE_PROJECT_ID` 를 정해 로그인 사용자만 받기 권장
- 이 PC 공인 IP 가 ODsay 에 등록돼 있어야 함 (`python check_keys.py`)

## 서버 상태 확인
- `GET /health` → `{"status":"ok","model":true,...}`
- `http://<주소>/docs` 에서 모든 API 를 직접 불러 볼 수 있음
