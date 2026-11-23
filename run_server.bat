@echo off
rem Seat forecast prediction server (Windows). See README "예측 서버".
rem   Emulator: http://10.0.2.2:8000/    Phone on same Wi-Fi: http://<this PC IP>:8000/
rem   To check Firebase login tokens, remove "rem" below and put your Firebase project ID.
rem set FIREBASE_PROJECT_ID=your-firebase-project-id
chcp 65001 > nul
cd /d %~dp0
python -X utf8 -m uvicorn server:app --host 0.0.0.0 --port 8000
