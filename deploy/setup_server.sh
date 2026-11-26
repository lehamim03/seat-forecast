#!/bin/bash
# 좌석예보 예측 서버 설치 (우분투 22.04 · Lightsail/Oracle 등) — deploy/upload.ps1 이 올린 묶음을 풀고 실행
# 서버에서:  bash ~/seat-forecast/deploy/setup_server.sh
set -e
cd ~/seat-forecast
sudo apt-get update -y
sudo apt-get install -y python3-venv python3-pip
# 메모리가 2GB 미만이면 스왑 2GB (모델 125MB · 서버 약 600MB)
if [ "$(free -m | awk '/Mem:/{print $2}')" -lt 1900 ] && [ ! -f /swapfile ]; then
  sudo fallocate -l 2G /swapfile && sudo chmod 600 /swapfile && sudo mkswap /swapfile && sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi
python3 -m venv .venv
.venv/bin/pip install --upgrade pip -q
.venv/bin/pip install -r requirements.txt -q
# 모델이 안 올라왔으면 서버에서 학습 (data/ 가 묶음에 있음)
[ -f board_model.pkl ] || .venv/bin/python train.py
# 자동 실행 (서비스 파일의 사용자 · 경로를 이 서버에 맞춤)
sed "s#User=ubuntu#User=$USER#; s#/home/ubuntu/seat-forecast#$HOME/seat-forecast#g" deploy/seat-forecast.service \
  | sudo tee /etc/systemd/system/seat-forecast.service > /dev/null
sudo systemctl daemon-reload
sudo systemctl enable --now seat-forecast
sudo systemctl restart seat-forecast
sleep 5
.venv/bin/python check_keys.py || true
curl -s localhost:8000/health && echo
echo "완료 — 공인 IP 를 ODsay 에 등록하고, 앱 주소를 http://<공인 IP>:8000/ 로"
