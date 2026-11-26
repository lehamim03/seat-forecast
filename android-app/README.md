# 팀 앱 (A · B · C · D 통합)

담당 모듈을 원래 폴더 그대로 가져와 하나의 앱으로 묶습니다. 모듈 코드는 고치지 않고, 연결(Host 구현)만 이 앱에 있습니다.

| 하단 탭 · 동작 | 모듈 |
|---|---|
| 홈 — 출발지(현재 위치 · 검색) → 도착지 | A `android-home/home` (`HomeFragment`) |
| 실시간 대안 · 홈 추천 카드 누르기 | B `android-alternatives/alternatives` (출발지 · 도착지 extra 6개) |
| 지도 | C `android-map/map` (`MapActivity`, 홈에서 마지막으로 검색한 경로) |
| 마이 · 로그인 · 즐겨찾는 곳 · 기본 도착지 · last_trip · 대안 카드 누르기(탑승 피드백) | D `android-account/account` |
| 돌발 알림 | E — 서버(`week13_fcm_sender.py`)가 D 의 `last_trip` 기준으로 FCM 전송. 홈의 출발 알림 버튼은 안내 대화상자만 띄움 |

## 출발지 · 도착지 연결 (`app/` 안)

- `TeamHomeHost` — 즐겨찾기 · 처음 도착지(D `destination`, 없으면 `last_trip.to`) · 즐겨찾기 추가 · 검색할 때마다 `last_trip` 저장.
  로그인 전에는 즐겨찾기 빈 목록, 도착지 없음, 저장 안 함.
- `LastTrip` — 이번 실행에서 마지막으로 검색한 경로. 대안 탭(경로가 바뀌면 새로 만듦) · 지도 탭(`MapServer.setLastTrip`)이 씀.
  모듈마다 `Place` 클래스가 따로 있어 여기서 바꿔 줍니다.
- `TeamAltHost` — 도착지 extra 가 없으면 `LastTrip` → D 순서, 대안 카드 → D `FeedbackActivity`.

## 설정

- 예측 서버: `local.properties` 의 `SERVER_BASE_URL` (기본 `http://10.0.2.2:8000/` = 에뮬레이터 → 내 컴퓨터).
  휴대폰은 `SERVER_BASE_URL=http://127.0.0.1:8000/` + `adb reverse tcp:8000 tcp:8000` (USB 연결).
- `app/google-services.json` 이 없으면 로컬 Firebase 에뮬레이터를 씁니다. 휴대폰에서는 로그인 화면의 **로그인 없이 둘러보기**로 확인하세요.
- 지도 키: `local.properties` 에 `KAKAO_NATIVE_APP_KEY=...` (저장소에 올리지 않음, `android-map/local.properties` 와 같은 값).
  카카오 개발자 콘솔에 이 앱 패키지(`kr.ac.kookmin.seatforecast`)와 키 해시도 등록되어 있어야 합니다.

## 실행

```bash
cd android-app
./gradlew :app:installDebug      # JDK 17
```
