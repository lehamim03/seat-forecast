# A · 홈 — 정류장 · 버스 추천 (home 모듈)

담당: A 이창민 · 브랜치 `feature/A-home`

D 모듈(`android-account`)과 같은 방식으로 **독립된 안드로이드 라이브러리 모듈**로 만들었습니다.
팀 앱 뼈대(app 모듈)가 생기면 이 모듈을 붙이기만 하면 됩니다. 저장소의 다른 파일은 고치지 않았습니다.

**지금 상태: 10주차 (화면 구조 · Retrofit 준비).** 목록 칸은 노선 · 정류장 이름만 보이는 임시 항목입니다.

| 만든 것 | 위치 |
|---|---|
| 홈 화면 구조: 헤더 · 우리집 → 국민대(평소 정류장 · `day.label`) · 추천 요약(`recommendation.message`) · 추천 목록 · 출발 알림 버튼 | `ui/HomeFragment`, `res/layout/home_fragment_home.xml` |
| 로딩 · 오류(다시 시도) · 빈 목록 · 로그인 필요 상태 화면 | `ui/HomeFragment` |
| 추천 목록 (ranking 순서 그대로, 임시 항목: 노선 칩 + 정류장 이름) | `ui/OptionAdapter`, `res/layout/home_item_option.xml` |
| 단독 화면 (시연 앱 · 하단 탭이 없을 때) | `ui/HomeActivity` |
| `GET /api/v1/recommendations` 응답 모델 (Gson) | `model/*` |
| 데이터 소스: 예시 JSON(지금) / 서버(13주차) | `data/RecommendationSource`, `AssetRecommendationSource`, `ApiRecommendationSource` |
| 다른 화면과 연결할 콜백 | `HomeHost`, `Home.init` |
| 단독 실행용 시연 앱 | `demo/` (팀 앱에 합칠 때는 필요 없음) |

화면 구조는 Figma '좌석예보 앱 UI'의 `02 홈` 프레임을 따랐습니다 (빨강 `#C8102E`, 회색 바탕 `#F2F3F5`, 흰 카드).
색 값은 `res/values/home_colors.xml` 한 곳에만 있습니다 (탑승 확률 배지 색 초록 `#1E9E5A` · 노랑 `#C98A00` · 빨강 `#C8102E` 포함, 11주차부터 사용).
하단 탭(홈 · 지도 · 알림 · 마이)과 헤더의 새로고침 · 알림 아이콘은 넣지 않았습니다 (하단 탭은 팀 공통 앱 뼈대, 새로고침은 12주차).

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
- 설정이 바뀌면 `homeFragment.reload()` 로 다시 불러올 수 있습니다.

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
| `RecommendationsParseTest` (8) | `recommendations.json` 읽기 (ranking 11개 · 순서 · 첫 항목 값), 정류장 이름 그대로, 빈 `buses[]` · 갈 수 없는 첫 차, null 필드(`arrive_at` · `total_min` · `seats_at_stop` · `recommendation`), 깨진 JSON, 시연 앱 assets 가 원본과 같은지 |
| `ApiRecommendationSourceTest` (6) | 요청 주소 `/api/v1/recommendations` · 쿼리 `home_stop` · `route` · `Bearer` 헤더, 토큰 없을 때 헤더 없음, 주소 끝 `/` 처리, 오류 응답 `{"error":…}` · 본문 없는 500 · 서버 꺼짐 |

**Windows 사용자 이름이 한글인 경우** (`C:\Users\홍길동`): 테스트 실행 중 `GradleWorkerMain` 을 찾지 못하는 오류가 나면
Gradle 캐시를 영문 경로로 옮기세요 — 환경 변수 `GRADLE_USER_HOME=C:\gradle-home` (Android Studio: Settings → Build Tools → Gradle → Gradle user home).
에뮬레이터도 같은 이유로 꺼질 수 있으면 `ANDROID_AVD_HOME` 을 영문 경로로 두세요.
