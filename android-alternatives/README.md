# B · 실시간 대안 (alternatives 모듈)

담당: B (후스렌 → 이창록 인계) · 브랜치 `feature/B-alternatives`

A 홈(`android-home`) · D 로그인(`android-account`) 모듈과 같은 방식으로 **독립된 안드로이드 라이브러리 모듈**로 만들었습니다.
팀 앱 뼈대(app 모듈)가 생기면 이 모듈을 붙이기만 하면 됩니다. 저장소의 다른 파일은 고치지 않았습니다.

**지금 상태: 11주차까지 (10주차 상태별 화면 설계 · API 확인 + 11주차 대안 카드 3장 · 차량별 좌석).**
다음: 12주차 1분 자동 갱신 · 상태 구분 완성 → 13주차 `/alternatives` 서버 연결.

| 주차 | 만든 것 | 위치 |
|---|---|---|
| 10주차 1차 | 평소 버스 상태 화면 — **여유**(70% 이상) · **만석 위험**(40~70%) · **만석**(40% 미만) · 우회 중 · 정보 없음 | `ui/AltFormat.banner`, `ui/AlternativesFragment.bindBanner` |
| 10주차 2차 | **조회 중**(로딩) · **오류**(다시 시도) · 로그인 필요 · 대안 없음 상태 화면 | `ui/AlternativesFragment` |
| 10주차 2차 | `GET /api/v1/alternatives` 응답 모델 · 예시 JSON / 서버 데이터 소스 · 오류 응답 처리 | `model/*`, `data/*` |
| 11주차 1차 | 대안 카드 3장 **① 기다리기 ② 걸어가기 ③ 다른 버스** — ★ 추천, 도착 시각, 가장 빠른 대안과 비교, 서울 하차 · 환승 경로 | `ui/OptionCard`, `res/layout/alt_item_option.xml` |
| 11주차 2차 | 카드 안 **다가오는 차량별 남은 좌석** — N번째 · N분 후 · 약 N석/만석/좌석 정보 없음 · 탑승 확률, 걸어가는 동안 지나가는 차 표시, '탈 차' | `ui/AltFormat.busRows`, `res/layout/alt_item_bus.xml` |
| — | 단독 화면 (A 홈 카드 · E 돌발 알림 푸시에서 열기) | `ui/AlternativesActivity` |
| — | 다른 화면과 연결할 콜백 | `AlternativesHost`, `Alternatives.init` |
| — | 단독 실행용 시연 앱 (상태 화면 바로 보기) | `demo/` (팀 앱에 합칠 때는 필요 없음) |

화면은 기획서 11쪽 '실시간 대안' 화면(다가오는 8800 · N분 후 / 이렇게 해보세요 ①②③)을 따랐고,
색 · 카드 모양은 Figma '좌석예보 앱 UI' 공통 값(빨강 `#C8102E`, 회색 바탕 `#F2F3F5`, 흰 카드)과 A 홈 카드 · 확률 배지를 그대로 썼습니다.
색 값은 `res/values/alt_colors.xml` 한 곳에만 있습니다. 리소스 이름은 모두 `alt_` 로 시작해 다른 모듈과 겹치지 않습니다.

### 화면 규칙

| 부분 | 내용 | 규칙 |
|---|---|---|
| 맨 위 | `삼성1차아파트 · 8800 · 07:40 기준` | `home_stop.name` · `my_route` · `generated_at` 시각 |
| 상태 카드 | `만석` 알약 · `다가오는 8800 · 6분 후` · `도착 때 만석 · 탑승 확률 2%` · 안내 문장 | 평소 버스 = `alternatives["기다리기"]`. 상태는 그 `board_prob` 단계 (A 배지와 같은 경계 0.7 · 0.4). 차는 걸어서 탈 수 있는 첫 차(`reachable`) |
| 추천 문장 | `recommendation.message` 그대로 | 없으면 숨김 |
| 제목 | 여유면 `다른 선택지`, 아니면 `이렇게 해보세요` | |
| 카드 순서 · 번호 | ① 기다리기 ② 걸어가기 ③ 다른 버스 | `null` 인 대안은 빼고, 번호는 기획서 그대로 유지 |
| ★ 추천 | `recommendation.kind` 와 같은 카드 (빨간 테두리) | 건너뛰는(`skipped`) 카드는 추천 표시 안 함 |
| 도착 | `도착 HH:MM` · `가장 빨라요` / `+N분` | `total_min` 이 가장 작은 대안과 비교 (건너뜀 · 시간 모름은 비교에서 뺌) |
| 타는 법 | 기다리기 `○○에서 그대로 기다리기` · 걸어가기 `○○까지 도보 N분` · 다른 버스 `○○ · 도보 N분` | 기다리기에서 첫 차가 빨강인데 뒤 차가 탈 만하면 `첫 차 보내고 다음 8800 타기 · 12분 후 · 약 18석` (기획서 11쪽) |
| 서울 | `서울시청 하차 · 도보 6분 → 버스 1711(…) → 도보 4분` | `alight_stop` + `route_choices[0].summary` (추천 경로) |
| 다가오는 차 | `1번째 · 6분 후 · 만석 [탈 차] · 2%` | `seats_at_stop` 0 → 만석, null → 좌석 정보 없음. `reachable: false` → 흐리게 + `걸어가는 동안 지나가요`. `buses: []` → `다가오는 차 정보 없음 · 배차간격으로 예상` |
| 알림 | `돌발 · 행사로 +N분 지연 반영` (노랑) / `우회 중 · 이 정류장은 지나가서 탈 수 없어요` (빨강, 카드 흐리게) | 건너뜀이 지연보다 우선 |
| 지하철 | `지하철로 가면 · 도착 HH:MM · 환승 N회 · 도보 N분` + 경로 | `subway` 가 있을 때만 (docs 예시에는 없음) |

## 데이터 형식 (docs/api.md ③ 그대로)

- **`GET /api/v1/alternatives?home_stop=203000063&route=8800`**, 헤더 `Authorization: Bearer <Firebase ID 토큰>` — 요청 값은 ② 홈과 같습니다.
- 응답: `generated_at`, `my_route`, `home_stop{id,name}`, `day`, `recommendation{kind,route,stop_name,message}`,
  `alternatives{"기다리기","걸어가기","다른 버스"}`(각각 선택지 또는 `null`), `subway`(없으면 `null`) — 예시 `docs/examples/alternatives.json`.
- 선택지(`model/Option`)는 A 홈 모듈의 `Option` 과 필드가 같습니다 (`kind`, `route`, `stop`, `walk_min`, `board_prob`, `arrive_at`, `total_min`,
  `delay_min`, `skipped`, `alight_stop`, `buses[]`, `route_choices[]`). null 이 올 수 있는 숫자는 `Integer` 입니다.
- 오류 응답 `{"error": {"code", "message"}}` 은 `SourceException`(`code`, `httpStatus`, 화면에 보여 줄 `message`)으로 바뀝니다.
- 시연 앱의 `demo/src/main/assets/alternatives.json` 은 `docs/examples` 복사본입니다. 원본이 바뀌면 다시 복사하세요 (테스트가 같은지 검사).
  `alt_cases.json` 은 경우 확인용 **가상 값**입니다 (docs 예시 아님).

## 다른 화면과 연결 (AlternativesHost)

alternatives 모듈은 다른 모듈에 직접 의존하지 않습니다. 팀 앱이 `AlternativesHost` 를 구현해 넘깁니다 (A 의 `HomeHost` 와 같은 모양).

```java
public class TeamAltHost implements AlternativesHost {
    public boolean isSignedIn() { return Account.isSignedIn(); }
    public void requestSignIn(Context c) { c.startActivity(new Intent(c, LoginActivity.class)); }
    public void loadUsualTrip(Callback<UsualTrip> cb) {
        new UserRepository().loadSettings(Account.currentUid(), new kr.ac.kookmin.seatforecast.account.Callback<UserSettings>() {
            public void onSuccess(UserSettings s) { cb.onSuccess(new UsualTrip(s.homeStopId, s.myRoute)); }
            public void onError(String message) { cb.onError(message); }
        });
    }
    public void getIdToken(Callback<String> cb) { /* D Account.getIdToken — "Bearer " 는 이 모듈이 붙임 */ }
    // 선택: 카드를 누르면 C 지도에서 경로 보기 / D 탑승 피드백
    public void onOptionClicked(Context c, Option o) { c.startActivity(FeedbackActivity.newIntent(c, o.route, o.stop.id, o.boardProb)); }
}
```

```java
// A 홈 HomeHost.onOptionClicked 에서 실시간 대안 열기
c.startActivity(AlternativesActivity.newIntent(c));                              // 평소 정류장 · 노선
// E 돌발 알림 푸시 (data.screen == "alternatives", data.route)
startActivity(AlternativesActivity.newIntent(this, null, data.get("route")));    // 정류장은 평소 값, 노선만 지정
```

## 팀 앱 모듈에 붙이기

```gradle
// settings.gradle (팀 앱 루트)
include ':alternatives'
project(':alternatives').projectDir = file('android-alternatives/alternatives')   // 팀 앱 위치에 맞게 경로 조정

// app/build.gradle
dependencies { implementation project(':alternatives') }
```

```java
// Application.onCreate — 한 번
Alternatives.init(new TeamAltHost(), new AssetAlternativesSource(this));            // 서버 전: 앱 assets 에 alternatives.json
Alternatives.init(new TeamAltHost(), new ApiAlternativesSource("https://<서버>/"));  // 13주차 서버 연결
```

- 화면 하나로 열 때는 `AlternativesActivity`(모듈 안 `AltTheme`, 흰 상단바), 다른 화면 안에 넣을 때는 `AlternativesFragment.newInstance(stop, route)`.
- `alternativesFragment.reload()` 로 다시 불러올 수 있습니다 (12주차 1분 자동 갱신에서 사용 예정).

## 실행 · 테스트

준비: Android Studio(SDK 35), JDK 17 (A 모듈 README 참고 — Gradle 8.9 는 Java 23 이상에서 실행되지 않음)

```bash
cd android-alternatives
./gradlew :alternatives:testDebugUnitTest   # JVM 단위 테스트 (docs/examples 형식 포함)
./gradlew :demo:installDebug                # 에뮬레이터 · 기기에 설치 → '좌석예보 실시간 대안 시연'
```

| 테스트 | 확인하는 것 |
|---|---|
| `AlternativesParseTest` (8) | `alternatives.json` 읽기 (대안 3개 · 화면 순서 · 필드 값), 정류장 이름 그대로, `subway` 없음, null 필드(`arrive_at` · `total_min` · `seats_at_stop` · 빈 `buses`), 대안 모두 null, 깨진 JSON, 시연 앱 assets 가 원본과 같은지 |
| `ApiAlternativesSourceTest` (6) | 요청 주소 `/api/v1/alternatives` · 쿼리 `home_stop` · `route` · `Bearer` 헤더, 토큰 없을 때 헤더 없음, 주소 끝 `/`, 오류 응답 `{"error":…}` · 본문 없는 500 · 401 · 서버 꺼짐 |
| `ui.AltFormatTest` (9) | 확률 단계 경계 · % 표시, 평소 버스 상태(만석 · 여유 · 만석 위험 · 우회 · 정보 없음), 카드 번호, 추천 카드, 가장 빠른 대안과 비교, 차량별 좌석(지나가는 차 · 탈 차 · 만석 · 좌석 모름), 첫 차 보내고 다음 차 |

### 시연 앱으로 상태 화면 확인

```bash
adb shell am force-stop kr.ac.kookmin.seatforecast.alternativesdemo
adb shell am start -n kr.ac.kookmin.seatforecast.alternativesdemo/kr.ac.kookmin.seatforecast.alternatives.ui.AlternativesActivity --es demo_mode relaxed
```

| `demo_mode` | 화면 |
|---|---|
| (없음) | **만석** — docs 예시 그대로 (평소 8800 · 6분 후 · 만석 · 2%, 추천 ③ 다른 버스 3007) |
| `relaxed` | **여유** — 다가오는 8800 약 14석 · 92%, 추천 ① 기다리기 (시연용 가상 값) |
| `risk` | **만석 위험** — 다가오는 8800 약 3석 · 55% (시연용 가상 값) |
| `cases` | 경우 모음 `alt_cases.json` — 첫 차 보내고 다음 차 · 놓치는 첫 차 · 좌석 모름 · 돌발 지연 · 우회로 건너뜀 · 지하철 비교 |
| `empty` · `error` · `signed_out` · `loading` | 대안 없음 · 오류(다시 시도) · 로그인 필요 · 조회 중 |

`loading` 은 10분 동안 응답하지 않으므로, 다른 모드로 바꿀 때는 위처럼 `force-stop` 을 먼저 하세요.
