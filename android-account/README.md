# D · 로그인 · 마이페이지 (account 모듈)

담당: D 이창록 · 브랜치 `feature/D-login-mypage`

다른 화면(A~E)과 파일이 겹치지 않도록 **독립된 안드로이드 라이브러리 모듈**로 만들었습니다.
팀 앱 뼈대(app 모듈)가 생기면 이 모듈을 붙이기만 하면 됩니다. 저장소의 다른 파일은 고치지 않았습니다.

| 만든 것 | 위치 |
|---|---|
| 회원가입 · 로그인 · 로그인 상태 유지 · 비밀번호 찾기 (Firebase Auth 이메일) | `ui/LoginActivity`, `ui/SignUpActivity`, `data/AuthRepository` |
| 마이페이지: 회원 정보 · 오늘 탑승 결과 카드 · 메뉴 · 주변 정류장 | `ui/MyPageActivity` |
| └ 즐겨찾기 경로 (평소 정류장 · 노선 · 비교 정류장 · 도착 목표 시각) | `ui/RouteSettingsActivity`, `data/UserRepository`, `data/UserSettings` |
| └ 출발 알림 설정 (출발 알림 · 돌발 알림 · 몇 분 전) | `ui/NotifySettingsActivity` |
| └ 탑승 피드백 기록 (이번 달 N회 · 탑승 성공 M회) | `ui/FeedbackHistoryActivity` |
| └ 계정 관리 (비밀번호 재설정 메일 · 로그아웃) | `ui/MyPageActivity` 메뉴 |
| 탑승 피드백 "탔어요 / 못 탔어요" | `ui/FeedbackActivity`, `feedback/*` |
| Firestore 보안 규칙 (본인 문서만) | `firebase/firestore.rules` |
| 단독 실행용 시연 앱 | `demo/` (팀 앱에 합칠 때는 필요 없음) |

화면 디자인은 Figma '좌석예보 앱 UI'의 `01 로그인` · `07 마이페이지` 프레임을 따랐습니다 (빨강 `#C8102E`, 회색 바탕 `#F2F3F5`, 흰 카드).
회원가입 · 하위 설정 화면 · 피드백 화면은 Figma 에 프레임이 없어 같은 색 · 카드 모양으로 맞췄습니다.
하단 탭(홈 · 지도 · 알림 · 마이)은 팀 공통 앱 뼈대에서 만들고, 마이 탭에서 `MyPageActivity` 를 열면 됩니다.

## 데이터 형식 (docs/api.md ⑦ 그대로)

- **Firestore `users/{uid}`** — `docs/examples/settings.json` 의 필드 그대로 (`home_stop_id`, `candidate_stop_ids`, `my_route`,
  `destination`, `arrive_by`, `notify{departure, incident, minutes_before}`) + 회원 정보 `uid`, `email`, `nickname`, `created_at`, `updated_at`.
  항상 merge 로 쓰므로 다른 화면이 같은 문서에 넣은 필드(예: E 의 FCM 토큰)는 지워지지 않습니다.
- **`POST /api/v1/feedback`** — `docs/examples/feedback.json` 형식, 헤더 `Authorization: Bearer <Firebase ID 토큰>`.
  보내기 전에 `users/{uid}/feedback/{id}` 에 먼저 기록하고(`sent: false`), 서버가 201 을 주면 `sent: true`, `server_id` 를 남깁니다.
  서버가 꺼져 있으면 기록만 남기고, 마이페이지를 열 때 다시 보냅니다.
- 정류장 · 노선 목록은 `data/Stops.java` — 루트 `config.py` 의 STOPS · ROUTES 와 같게 유지 (정류장에 서지 않는 노선은 고를 수 없음).

## 다른 화면에서 쓰는 법

```java
// 로그인 확인 (홈 화면 시작 시)
if (!Account.isSignedIn()) loginLauncher.launch(new Intent(this, LoginActivity.class));  // 성공하면 RESULT_OK

// 평소 정류장 · 노선 → GET /recommendations, /alternatives 요청 값
new UserRepository().loadSettings(Account.currentUid(), new Callback<UserSettings>() {
    public void onSuccess(UserSettings s) { /* s.homeStopId, s.myRoute, s.arriveBy ... */ }
    public void onError(String message) { }
});

// 서버 요청 인증 헤더
Account.getIdToken(callback);   // "Bearer " + token

// 추천 카드에서 탑승 피드백 열기 (그때 보여준 확률을 넘김)
startActivity(FeedbackActivity.newIntent(this, "3007", "203000161", 0.95));

// 마이페이지 (로그아웃하면 MyPageActivity.RESULT_SIGNED_OUT 으로 닫힘)
startActivity(new Intent(this, MyPageActivity.class));                       // '오늘 탑승 결과' 카드 = 평소 노선
startActivity(MyPageActivity.newIntent(this, "3007", "203000162", 0.95));    // 카드 = 오늘 추천했던 버스
```

## 팀 앱 모듈에 붙이기

```gradle
// settings.gradle (팀 앱 루트)
include ':account'
project(':account').projectDir = file('android-account/account')   // 팀 앱 위치에 맞게 경로 조정

// app/build.gradle
dependencies { implementation project(':account') }
```

- Firebase 콘솔에서 받은 `google-services.json` 은 팀 app 모듈에 넣습니다 (저장소에는 올리지 않음).
- 앱 시작 시(Application.onCreate) `Account.setServerBaseUrl("https://<서버>/")` 와 `Account.init(this)` 한 번
  (`init` 은 '로그인 상태 유지'를 끄고 로그인한 사용자를 다시 켤 때 로그아웃시킵니다).
- 화면 테마는 모듈 안의 `AccountTheme` 를 쓰므로 팀 테마와 상관없이 보입니다. 리소스 이름은 모두 `account_` 로 시작해 겹치지 않습니다.

## 실행 · 테스트

준비: Android Studio(SDK 35), JDK 17 이상, Node.js(Firebase 에뮬레이터용)

```bash
# 1) 로컬 Firebase 에뮬레이터 (Auth 9099 · Firestore 8080, 실제 Firebase 프로젝트 없이 동작)
cd android-account/firebase
npx firebase-tools emulators:start --project demo-seat-forecast --only auth,firestore

# 2) 다른 터미널에서 — JVM 단위 테스트 (형식이 docs/examples 와 같은지 포함)
cd android-account
./gradlew :account:testDebugUnitTest

# 3) 안드로이드 에뮬레이터를 켠 뒤 — 실제 Firebase SDK 로 회원가입 → 로그인 → 설정 저장 → 피드백 흐름 검사
./gradlew :account:connectedDebugAndroidTest

# 4) 시연 앱 설치 (google-services.json 이 없으면 자동으로 로컬 에뮬레이터 사용)
./gradlew :demo:installDebug
```

| 테스트 | 확인하는 것 |
|---|---|
| `ValidatorsTest` (5) | 이메일 · 비밀번호 · 닉네임 · 시각 검사, 정류장별 노선이 config.py 와 같은지 |
| `UserSettingsTest` (8) | `settings.json` 읽기 · 같은 필드 이름으로 쓰기, 잘못된 설정 거부 |
| `FeedbackRecordTest` (2) | 이번 달 기록 수 · 탑승 성공 수, 오늘 같은 노선 기록이 있는지 |
| `FeedbackApiTest` (4) | `POST /api/v1/feedback` 경로 · Bearer 헤더 · 본문이 `feedback.json` 과 같은지, 오류 응답 |
| `FirebaseEmulatorTest` (7) | 가입 시 users/{uid} 생성, 로그인 · 오류 문구, 설정 저장/읽기(다른 필드 보존), 남의 문서 접근 차단, 피드백 저장 · 전송 · 재전송 |
