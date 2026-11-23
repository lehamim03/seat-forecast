# A · 홈 — 정류장 · 버스 추천 (home 모듈)

담당: A 이창민 · 브랜치 `feature/A-home`

D 모듈(`android-account`)과 같은 방식으로 **독립된 안드로이드 라이브러리 모듈**로 만들었습니다.
팀 앱 뼈대(app 모듈)가 생기면 이 모듈을 붙이기만 하면 됩니다. 저장소의 다른 파일은 고치지 않았습니다.

**지금 상태: 12주차 (도착 시각 · 새로고침).** 11주차에 Figma `02 홈` 값으로 만든 카드에, 걸리는 시간 · 남은 분 줄이기 · 새로고침(버튼 · 당기기 · 자동)을 더했습니다.
다음: 13주차 `/recommendations` 서버 연결 → 14주차 출근길 추천 검증.

### 12주차 — 도착 시각 · 새로고침

| 기능 | 규칙 | 위치 |
|---|---|---|
| 걸리는 시간 | 카드 1줄 `도착 10:08` 옆에 `total_min` → `2시간 28분` (회색, 좁으면 이 칸만 `…`). 읽어 주기: "국민대 도착 예상 10:08, 2시간 28분 걸림" | `OptionAdapter.bindArrive`, `LiveTime.duration` |
| 남은 분 줄이기 | 응답을 **받은 순간부터** 지난 분(`SystemClock.elapsedRealtime`, 기기 시계 · `generated_at` 비교 안 함)만큼 `eta_min` 을 줄임. 30초마다 다시 그림 | `OptionFormat.busLine(o, elapsedMin)`, `LiveTime` |
| ↳ 놓친 차 | 남은 분 < 도보 분이 되면 이제 못 탐 → `앞차는 놓치고 다음 차 N분 뒤` (서버 `reachable` 규칙 eta ≥ 도보와 같음) | |
| ↳ 지나간 차 | 남은 분 < 0 이면 목록에서 뺌. 모두 지나가면 `표시된 차가 모두 지나갔어요 · 새로고침해 주세요` (회색) | |
| '지금 HH:MM' 칩 | `generated_at` 시각 + 받은 뒤 지난 분 | `LiveTime.plusMinutes` |
| 갱신 시각 | 목록 제목 아래 `07:40 기준 · 방금 갱신` / `3분 전 갱신` / `새로 불러오는 중…` / `갱신 실패 · 07:40 기준`(노랑) | `HomeFragment.showUpdated` |
| 새로고침 | 헤더 ⟳ 버튼 · 목록을 아래로 당기기(목록이 맨 위일 때만). **지금 목록은 그대로 둔 채** 불러오고 보던 자리도 유지. 불러오는 중에는 버튼 흐리게 · 중복 요청 무시 | `HomeFragment.refresh` |
| 자동 새로고침 | 화면이 보이는 동안 받은 지 **2분** 지나면 알아서. 실패하면 2분 뒤 다시 (서버를 계속 두드리지 않음). 다른 화면 · 백그라운드에서 돌아오면 바로 확인 | `LiveTime.shouldAutoRefresh`, `onResume` |
| 실패 | 새로고침 실패 = 이전 목록 + `갱신 실패`. 직접 눌렀을 때만 Snackbar `새로고침하지 못했어요. …` + `다시 시도`. 처음 불러오기 실패는 11주차처럼 오류 화면 | `HomeFragment.failed` |
| 상태 화면 | 오류 · 빈 목록에서도 당기기 · ⟳ 로 다시 시도. 로딩 · 로그인 필요 화면에서는 막음 | `HomeFragment.showState` |

배지(`board_prob`) · `arrive_at` 은 서버 값 그대로입니다 (앱에서 다시 계산하지 않음). 시간이 지나 달라지는 부분은 2분 자동 새로고침으로 맞춥니다.
팀 앱에서 쓸 수 있는 메서드: `homeFragment.reload()`(로딩 화면부터) · `homeFragment.refresh(false)`(목록을 둔 채).

| 만든 것 | 위치 |
|---|---|
| 홈 화면 구조: 헤더(⟳ 새로고침) · 우리집 → 국민대 · 추천 요약(`recommendation.message`) · 추천 목록 · 출발 알림 버튼 | `ui/HomeFragment`, `res/layout/home_fragment_home.xml` |
| 우리집 → 국민대 카드: 평소 정류장, 칩 3개(날짜 · `day.kind` · 지금 HH:MM — `generated_at` 기준), `>` 누르면 `HomeHost.onTripClicked` | `ui/HomeFragment`, `ui/TripFormat` |
| 로딩 · 오류(다시 시도) · 빈 목록 · 로그인 필요 상태 화면 (11주차에 에뮬레이터에서 4종 모두 확인) | `ui/HomeFragment` |
| 추천 카드 (ranking 순서 그대로, 아래 표) | `ui/OptionAdapter`, `ui/OptionFormat`, `res/layout/home_item_option.xml` |
| 단독 화면 (시연 앱 · 하단 탭이 없을 때) | `ui/HomeActivity` |
| `GET /api/v1/recommendations` 응답 모델 (Gson) | `model/*` |
| 데이터 소스: 예시 JSON(지금) / 서버(13주차) | `data/RecommendationSource`, `AssetRecommendationSource`, `ApiRecommendationSource` |
| 다른 화면과 연결할 콜백 | `HomeHost`, `Home.init` |
| 단독 실행용 시연 앱 | `demo/` (팀 앱에 합칠 때는 필요 없음) |

화면 구조는 Figma '좌석예보 앱 UI'의 `02 홈` 프레임을 따랐습니다 (빨강 `#C8102E`, 회색 바탕 `#F2F3F5`, 흰 카드).
색 값은 `res/values/home_colors.xml` 한 곳에만 있습니다. 아이콘(핀 · 시계 · `>`)은 Figma 아이콘을 벡터로 옮겼습니다.
하단 탭(홈 · 지도 · 알림 · 마이)과 헤더의 알림 아이콘, 노란 '만석 위험' 배너는 넣지 않았습니다 (하단 탭은 팀 공통 앱 뼈대, 알림은 E 담당). 헤더 새로고침은 12주차에 넣었습니다.

### 추천 카드 (Figma Rec/3007 · Rec/3008)

| 줄 | 내용 | 규칙 |
|---|---|---|
| 1 | 노선 칩 · `도착 HH:MM`(`arrive_at`, 없으면 `도착 —`) · `1순위` 태그 · 걸리는 시간(`total_min`, 12주차) · 탑승 확률 배지 | 1순위 = ranking 첫 항목(빨간 테두리). 배지 = `board_prob` 반올림 % |
| 2 | 📍 정류장 이름 · 도보 N분 | 정류장 이름은 API 그대로 |
| 3 | 🕒 탈 수 있는 첫 차: `N분 뒤 도착 · 도착 때 약 N석` (0석이면 `만석 예상`, `null`이면 `좌석 정보 없음`, 0분이면 `곧 도착`) | `board_prob` 이 "걸어가서 탈 수 있는 첫 차" 기준이라 `reachable: false` 차는 건너뜀 → `앞차는 놓치고 다음 차 N분 뒤`. 글자색 = 그 차의 확률 단계 |
| 3 | 탈 수 있는 차가 없음 → `걸어가는 동안 다가오는 차가 모두 지나가요` (빨강) · `buses: []` → `다가오는 차 정보 없음 · 배차간격으로 예상` (회색) | 예시 11개 중 6개가 `buses: []` |
| 4 | `delay_min > 0` → `돌발 · 행사로 +N분 지연 반영` (노랑) | |
| 4 | `skipped` → `우회 중 · 이 정류장은 지나가서 탈 수 없어요` (빨강), 카드 흐리게 · 배지 · 1순위 · 3줄 숨김 | 건너뜀이 지연보다 우선 |

탑승 확률 배지 (Figma ProbBadge): **70% 이상 초록** `#1E9E5A`/`#E6F4EC` · **40~70% 노랑** `#B45309`/`#FEF3C7` · **40% 미만 빨강** `#C8102E`/`#FBE9EC` (글자/바탕).
노랑 글자는 10주차의 `#C98A00` 에서 Figma 값 `#B45309` 로 바꿨습니다 (연노랑 바탕 위에서 읽기 쉬움).

## 데이터 형식 (docs/api.md ② 그대로)

- **`GET /api/v1/recommendations?home_stop=203000063&route=8800`**, 헤더 `Authorization: Bearer <Firebase ID 토큰>`.
- 응답: `generated_at`, `my_route`, `home_stop{id,name}`, `day{label,kind,drop_factor,headway_factor}`,
  `recommendation{kind,route,stop_name,message}`, `ranking[]` — 예시 `docs/examples/recommendations.json`.
- 선택지(`model/Option`): `kind`, `route`, `stop{id,name,mobile_no}`, `walk_min`, `board_prob`, `arrive_at`(null 가능),
  `total_min`, `delay_min`, `skipped`, `alight_stop`, `buses[{order,eta_min,seats_at_stop(null 가능),board_prob,reachable}]`,
  `route_choices[{arrive_at,transfers,walk_min,tags[],alight_stop,summary}]`. null 이 올 수 있는 숫자는 `Integer` 입니다.
- 오류 응답 `{"error": {"code", "message"}}` 은 `SourceException`(`code`, `httpStatus`, 화면에 보여 줄 `message`)으로 바뀝니다.
- 정류장 이름은 API 이름 그대로 보여 줍니다 (예: `매탄위브하늘채.경기아트센터`).
- 시연 앱의 `demo/src/main/assets/recommendations.json` 은 `docs/examples` 복사본입니다. 원본이 바뀌면 다시 복사하세요 (테스트가 같은지 검사).

## 다른 화면과 연결 (HomeHost)

home 모듈은 account 모듈에 직접 의존하지 않습니다. 팀 앱이 `HomeHost` 를 구현해 넘깁니다.
D 모듈 README 의 사용법에 그대로 연결하면 이렇게 됩니다:

```java
// home.Callback 과 account.Callback 은 이름이 같아 account 쪽은 전체 이름으로 씀
public class TeamHomeHost implements HomeHost {
    public boolean isSignedIn() { return Account.isSignedIn(); }

    public void requestSignIn(Context c) { c.startActivity(new Intent(c, LoginActivity.class)); }  // 돌아오면 홈이 다시 불러옴

    public void loadUsualTrip(Callback<UsualTrip> cb) {
        new UserRepository().loadSettings(Account.currentUid(), new kr.ac.kookmin.seatforecast.account.Callback<UserSettings>() {
            public void onSuccess(UserSettings s) { cb.onSuccess(new UsualTrip(s.homeStopId, s.myRoute)); }
            public void onError(String message) { cb.onError(message); }
        });
    }

    public void getIdToken(Callback<String> cb) {
        Account.getIdToken(new kr.ac.kookmin.seatforecast.account.Callback<String>() {
            public void onSuccess(String token) { cb.onSuccess(token); }   // "Bearer " 는 home 모듈이 붙임
            public void onError(String message) { cb.onError(message); }
        });
    }

    public void onOptionClicked(Context c, Option o) {
        // B 실시간 대안 화면 (B 모듈 생기면 연결) 또는 D 탑승 피드백:
        c.startActivity(FeedbackActivity.newIntent(c, o.route, o.stop.id, o.boardProb));
    }

    public void onDepartureAlarmClicked(Context c, Option first) { /* E 출발 알림 */ }

    // 11주차 추가 (기본 메서드라 구현하지 않아도 됨): '우리집 → 국민대' 카드 > → D 마이페이지 설정
    public void onTripClicked(Context c) { c.startActivity(new Intent(c, MyPageActivity.class)); }
}
```

## 팀 앱 모듈에 붙이기

```gradle
// settings.gradle (팀 앱 루트)
include ':home'
project(':home').projectDir = file('android-home/home')   // 팀 앱 위치에 맞게 경로 조정

// app/build.gradle
dependencies { implementation project(':home') }
```

```java
// Application.onCreate — 한 번
Home.init(new TeamHomeHost(), new AssetRecommendationSource(this));            // 서버 전: 앱 assets 에 recommendations.json
Home.init(new TeamHomeHost(), new ApiRecommendationSource("https://<서버>/"));  // 13주차 서버 연결

// 하단 탭 '홈'
getSupportFragmentManager().beginTransaction().replace(R.id.container, new HomeFragment()).commit();
// 또는 화면 하나로: startActivity(new Intent(this, HomeActivity.class));
```

- `HomeFragment` 의 레이아웃은 모듈 안 색 · 스타일을 쓰고, `HomeActivity` 는 모듈 안의 `HomeTheme` 를 씁니다.
  리소스 이름은 모두 `home_` 으로 시작해 다른 모듈과 겹치지 않습니다.
- 설정이 바뀌면 `homeFragment.reload()` 로 다시 불러올 수 있습니다. 목록을 둔 채 새로 받으려면 `homeFragment.refresh(false)`.
- 12주차부터 `androidx.swiperefreshlayout` 를 씁니다 (home 모듈 `build.gradle` 에 있음, 팀 앱이 따로 넣을 필요 없음).

## 실행 · 테스트

준비: Android Studio(SDK 35), JDK 17 (Gradle 8.9 는 Java 23 이상에서 실행되지 않음 — Android Studio 의 Gradle JDK 를 17 로)

```bash
cd android-home
./gradlew :home:testDebugUnitTest   # JVM 단위 테스트 (docs/examples 형식 포함)
./gradlew :demo:assembleDebug       # 시연 앱 빌드
./gradlew :demo:installDebug        # 에뮬레이터 · 기기에 설치 → '좌석예보 홈 시연'
```

| 테스트 | 확인하는 것 |
|---|---|
| `RecommendationsParseTest` (8) | `recommendations.json` 읽기 (11주차 '가까운 정류장 먼저' 순서 — 1순위 삼성1차아파트) (ranking 11개 · 순서 · 첫 항목 값), 정류장 이름 그대로, 빈 `buses[]` · 갈 수 없는 첫 차, null 필드(`arrive_at` · `total_min` · `seats_at_stop` · `recommendation`), 깨진 JSON, 시연 앱 assets 가 원본과 같은지 |
| `ApiRecommendationSourceTest` (6) | 요청 주소 `/api/v1/recommendations` · 쿼리 `home_stop` · `route` · `Bearer` 헤더, 토큰 없을 때 헤더 없음, 주소 끝 `/` 처리, 오류 응답 `{"error":…}` · 본문 없는 500 · 서버 꺼짐 |
| `ui.OptionFormatTest` (16) | 배지 경계(0.7 · 0.4) · % 반올림 · 0~100 자르기, 탈 수 있는 첫 차 고르기(`reachable: false` 건너뜀 · 모두 지나감 · 빈 `buses`), docs 예시 11개 · 시연 경우 모음 6개, 1순위 · 건너뜀, **12주차** 남은 분 줄이기 · 도보보다 짧아지면 다음 차 · 지나간 차 빼기 · 모두 지나감 · 0분 = 곧 도착 |
| `ui.LiveTimeTest` (6) | **12주차** 지난 분(내림 · 음수 없음), 2분 자동 새로고침 · 실패 뒤 2분 기다림, `2시간 28분` 형식, `07:40` + N분 (자정 넘김) |
| `ui.TripFormatTest` (4) | `generated_at` → `10/7(수)` · `07:40`, 형식이 다르면 칩 숨김, `day.kind` 우선 |

### 시연 앱으로 상태 화면 확인

`demo_mode` 를 넘겨 실행하면 상태 화면 · 카드 경우를 바로 볼 수 있습니다 (시연 앱에만 있는 기능).

```bash
adb shell am force-stop kr.ac.kookmin.seatforecast.homedemo
adb shell am start -n kr.ac.kookmin.seatforecast.homedemo/kr.ac.kookmin.seatforecast.home.ui.HomeActivity --es demo_mode cases
```

| `demo_mode` | 화면 |
|---|---|
| (없음) | 보통 — `recommendations.json` (docs 예시) |
| `cases` | 카드 경우 모음 `home_cases.json` — 지연 · 놓친 첫 차 · 정보 없음 · 곧 도착 · 좌석 모름 · 모두 지나감 · 건너뜀 |
| `empty` · `error` · `signed_out` · `loading` | 빈 목록 · 오류(다시 시도) · 로그인 필요 · 로딩 |
| `refresh_error` | 12주차: 처음엔 보통 화면, ⟳ · 당기기 · 자동 새로고침은 1초 뒤 실패 → 이전 목록 + `갱신 실패` + Snackbar |

12주차 에뮬레이터(Pixel 7, API 34) 확인: 1분 뒤 `6분 뒤` → `5분 뒤` · `지금 07:41` · `1분 전 갱신`, 2분 뒤 자동 새로고침(`방금 갱신`),
`refresh_error` 에서 ⟳ · 당기기 → 목록 유지 + `갱신 실패` + Snackbar, 목록을 내린 상태에서 아래로 끌면 새로고침 없이 스크롤만.

`loading` 은 10분 동안 응답하지 않으므로, 다른 모드로 바꿀 때는 위처럼 `force-stop` 을 먼저 하세요.

**Windows 사용자 이름이 한글인 경우** (`C:\Users\홍길동`): 테스트 실행 중 `GradleWorkerMain` 을 찾지 못하는 오류가 나면
Gradle 캐시를 영문 경로로 옮기세요 — 환경 변수 `GRADLE_USER_HOME=C:\gradle-home` (Android Studio: Settings → Build Tools → Gradle → Gradle user home).
에뮬레이터도 같은 이유로 꺼질 수 있으면 `ANDROID_AVD_HOME` 을 영문 경로로 두세요.
