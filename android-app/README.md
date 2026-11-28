# 팀 앱 (A · B · C · D 통합)

담당 모듈을 원래 폴더 그대로 가져와 하나의 앱으로 묶습니다. 연결(Host 구현)은 이 앱에 있습니다.

| 하단 탭 · 동작 | 모듈 |
|---|---|
| 홈 — 출발지(현재 위치 · 검색) → 도착지, 지난 출근길 재생 | A `android-home/home` (`HomeFragment`) |
| 실시간 대안 · 홈 추천 카드 누르기 (재생 중이면 B 도 같은 시각으로 재생) | B `android-alternatives/alternatives` (출발지 · 도착지 extra 6개 + `replay`) |
| 지도 | C `android-map/map` (`MapActivity`, 홈에서 마지막으로 검색한 경로) |
| 마이 · 로그인 · 즐겨찾는 곳 · 기본 도착지 · last_trip · 대안 카드 누르기(탑승 피드백) | D `android-account/account` |
| 돌발 알림 | E — 서버(`week13_fcm_sender.py`)가 D 의 `last_trip` 기준으로 FCM 전송. 홈의 출발 알림 버튼은 안내 대화상자만 띄움 |

## 출발지 · 도착지 연결 (`app/` 안)

- `TeamHomeHost` — 즐겨찾기 · 처음 도착지(D `destination`, 없으면 `last_trip.to`) · 즐겨찾기 추가 · 검색할 때마다 `last_trip` 저장.
  재생 중 카드 → `AlternativesActivity.newIntent(…, replayAt)`.
- `LastTrip` — 이번 실행에서 마지막으로 검색한 경로. 대안 탭(경로가 바뀌면 새로 만듦) · 지도 탭(`MapServer.setLastTrip`)이 씀.
- `TeamAltHost` — 도착지 extra 가 없으면 `LastTrip` → D 순서, 대안 카드 → D `FeedbackActivity`.
- `FeedbackProb` — 피드백의 `predicted_prob` 는 카드가 `seat_limited=true` · `seat_known=true` 일 때만 그 `board_prob`, 아니면 `null`
  (좌석 정보가 없을 때의 값은 평소 탑승률이라 예측이 아님 — `field_test.py` 채점을 흐림).

## 설정 (`local.properties`, 저장소에 올리지 않음)

| 키 | 없으면 | 설명 |
|---|---|---|
| `SERVER_BASE_URL` | `http://52.65.2.202:8000/` | 예측 서버. 기본값은 AWS Lightsail 고정 IP (LTE 휴대폰에서도 접속). 에뮬레이터에서 내 컴퓨터 서버로 시험할 때만 `http://10.0.2.2:8000/`, USB 휴대폰 + `adb reverse tcp:8000 tcp:8000` 이면 `http://127.0.0.1:8000/` |
| `KAKAO_NATIVE_APP_KEY` | 지도 대신 안내 문장 | C 지도 (`android-map/local.properties` 와 같은 값) |
| `TMAP_APP_KEY` | 경로 선을 정류장 직선으로 | C 지도의 경로 선을 도로 모양으로 (아래 '지도 경로 선') |

- 환경 변수로도 줄 수 있습니다 (같은 이름).
- 서버가 http 라서 `app/src/main/res/xml/app_network_security.xml` 로 `52.65.2.202` · `10.0.2.2` · `127.0.0.1` 만 평문 통신을 허용합니다. 서버 주소를 바꾸면 여기에도 추가하세요.
- `app/google-services.json` 이 없으면 로컬 Firebase 에뮬레이터를 씁니다. 휴대폰에서는 로그인 화면의 **로그인 없이 둘러보기**로 쓰세요.
  둘러보기 · 로그아웃 상태에서도 탑승 피드백은 서버로 갑니다 (Authorization 헤더 없이, `uid: null`).
  서버가 로그인한 사용자만 받으면(401) 휴대폰에만 저장하고 화면에 알립니다.

### 지도 경로 선 (C)
서버 `GET /api/v1/map` 의 경로 선(`transfer`)은 지나는 정류장 좌표를 직선으로 이은 것이라 정류장 사이가 멀면 도로를 벗어나 보입니다.
`TMAP_APP_KEY` 가 있으면 정류장 순서를 TMAP 경유지 길찾기(`routeSequential30`)로 다시 그립니다.
정류장이 도로 반대편이라 블록을 돌아 U턴하거나 지하철 구간처럼 도로가 직선의 1.5배 넘게 돌아가는 구간은 직선으로 둡니다.
한 번 그린 경로는 기기에 저장해 다시 요청하지 않습니다 (경로 하나에 TMAP 요청 1~2번).
키는 APK 안에 들어가므로 팀원 휴대폰용으로만 쓰세요.

### 카카오 지도 키 해시
카카오 지도는 APK 를 서명한 키의 해시를 확인합니다. **APK 를 빌드한 컴퓨터의 디버그 키**로 서명되므로, 같은 컴퓨터에서 빌드한 APK 는
어느 휴대폰에 설치해도 지도가 뜹니다 (휴대폰마다 등록할 필요 없음). 다른 컴퓨터에서 빌드하면 그 컴퓨터의 해시를 등록해야 합니다.

- 지금 빌드 컴퓨터(`~/.android/debug.keystore`)의 키 해시: `/xJoengHicoj9Pe+jAJdZ3fXzCM=`
- 확인 · 등록: 카카오 디벨로퍼스 → 내 애플리케이션 → 플랫폼 → Android → 패키지 `kr.ac.kookmin.seatforecast`, 키 해시 추가
- 다른 컴퓨터의 해시 구하기:
  ```bash
  keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore -storepass android \
    | openssl sha1 -binary | openssl base64
  ```

## 빌드 · 설치

```bash
cd android-app
./gradlew test :app:assembleDebug        # JDK 17
# APK: app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk   # USB 연결 휴대폰
```

팀원 휴대폰에는 APK 파일을 보내 설치합니다 (설정에서 '출처를 알 수 없는 앱 설치' 허용).

## 현장 시험 체크리스트 (실제 휴대폰)

전날 휴대폰 브라우저로 `http://52.65.2.202:8000/health` → `{"status":"ok", …}` 인지 확인.

1. **설치** — `app-debug.apk` 설치 → 앱 열기
2. **위치 권한** — 처음 홈에서 묻는 위치 권한 **허용** (정확한 위치)
3. **로그인 없이 둘러보기** — 로그인 화면에서 선택
4. **출발지 · 도착지** — 출발지 "현재 위치"(기본), 도착지 검색에 "국민대학교" → 고르기
5. **추천** — 카드가 보이는지 (노선 · 탑승 확률 또는 "좌석 정보 없음" · "입석 가능" · 도착 예상). 화면 녹화 시작
   - "근처 저장 경로 기준"이 보이면 서버가 ODsay 대신 근처 저장 경로로 계산한 것 (한도 초과 등) — 메모에 남기기
   - 오류 문장 + 다시 시도가 나오면 문장을 그대로 기록하고 다시 시도
6. **카드 → 실시간 대안** — 탈 버스 카드를 누르면 대안 화면 (1분마다 자동 갱신)
7. **탑승 피드백** — 대안 카드를 누름 → 버스가 오면 **탔어요 / 못 탔어요**, 메모에 "앞에 10명, 만석 통과" 처럼 줄 선 인원 · 못 탄 이유
   - "고마워요" 토스트 = 서버에 저장됨. "이 휴대폰에만 저장됨" 대화상자가 나오면 팀 시트에도 적기 (docs/field_test.md 백업 기록)
8. **지도 탭** — 경로 선이 도로를 따라 그려지는지, 칩(1순위 · 2순위)을 누르면 그 경로가 빨갛게 바뀌는지
9. **서버에서 확인 (서버 담당)** — 피드백은 서버의 `feedback/feedback.jsonl` 에 쌓입니다.
   서버에서 `python field_test.py` 를 실행하면 ② 현장 결과(앱이 보여 준 확률 vs 실제 탑승)로 채점합니다.
   API 만 볼 때는 브라우저로 `http://52.65.2.202:8000/docs` → `POST /api/v1/feedback` 형식 확인 (목록 조회 API 는 없음).
