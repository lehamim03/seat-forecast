# C · 지도·경로 (10–12주차)

기존 C 데모의 카카오맵 초기화와 위치 권한 처리를 팀 앱에서 재사용할 수 있도록 분리했다. `:map`은 Android 라이브러리이며 `:demo`는 지도 화면을 바로 여는 시연 앱이다. 팀 A·D와 같은 Gradle 8.9, AGP 8.7.3, Java 17, compileSdk 35, minSdk 24를 사용한다.

## 실행

`android-map`을 Android Studio에서 열고 `demo`를 실행하거나 다음 명령을 사용한다.

```sh
./gradlew :demo:installDebug
adb shell am start -n kr.ac.kookmin.seatforecast.mapdemo/.MapLauncher
```

기본 지도 타일을 보려면 카카오 디벨로퍼스에서 카카오맵 사용을 활성화하고 Android 앱 패키지 `kr.ac.kookmin.seatforecast.mapdemo`와 실행 환경의 서명 키 해시를 등록한다. 네이티브 앱 키는 환경 변수 `KAKAO_NATIVE_APP_KEY` 또는 `android-map/local.properties`의 같은 이름으로 지정하고 다시 빌드한다. 키가 없으면 화면에 설정 안내가 표시된다. [카카오맵 Android SDK 시작하기](https://apis.map.kakao.com/android_v2/docs/getting-started/)

## 확인할 동작

- 앱을 열면 위치 권한에 관계없이 지도 초기화를 시도한다. 기본 중심은 예시 지역인 수원 매탄동이다.
- **내 위치 권한 설정**을 누르면 Android가 앱 사용 중 위치 권한을 요청한다. Android 12 이상에서는 대략적인 위치도 선택할 수 있다.
- 허용한 권한 수준을 화면에서 확인할 수 있다. 거부 후 다시 요청할 수 있고, 시스템이 추가 요청을 차단한 경우 **앱 설정 열기**로 이동한다. 설정에서 돌아오면 권한 상태가 갱신된다.
- **주변 정류장**에서는 예시 출발점, 정류장 3곳과 도보 경로 2개(파란 점선)를 함께 볼 수 있다. 위치 권한이 없어도 사용할 수 있다.
- **8800·3007·7001·3008** 중 번호를 누르면 선택한 노선 전체를 그 색으로 표시한다. **주변 정류장**을 누르면 도보 구간으로 돌아간다.
- **내 위치**를 누르면 Android 위치 서비스에서 좌표를 한 번 받아 지도에 표시한다. 예시 출발점과 도보선은 고정되어 있으며, 기기 위치를 출발점으로 경로를 다시 계산하지 않는다. 위치 서비스를 사용할 수 없으면 화면에 안내한다.

카카오맵 SDK에서 배경 지도를 불러온다. 노선과 도보선은 빌드할 때 [공용 GeoJSON 예시](../docs/examples/map.geojson)를 지도 라이브러리에 포함한다. 실시간 교통 데이터나 팀 서버의 경로 API는 호출하지 않는다.

에뮬레이터에서는 위치 권한을 허용하고 아래처럼 수원 예시 좌표를 보낸 뒤 **내 위치**를 누른다. 직전 기기 좌표를 사용한 경우에는 최대 2분 후 다시 시도하거나 Android의 테스트 위치 공급자로 같은 좌표를 지정한다.

```sh
adb emu geo fix 127.0402 37.2649
```

Android 15 에뮬레이터 실행 화면: [권한 거부 후 주변 정류장](docs/week11-stops.png) · [7001번 전체 경로](docs/week11-bus-route.png) · [모의 GPS로 표시한 내 위치](docs/week11-location.png)

## 12주차 · 경로 목록과 비교

지도 하단의 원래 경로·대안 경로 카드를 누르면 선택 경로는 빨강, 비교 경로는 회색으로 표시하고 두 경로를 함께 볼 수 있게 이동한다. 도착 시각·정류장·도보 시간·탑승 확률은 공용 `recommendations.json` 예시의 같은 선택지에서 가져온다.

**서울 → 국민대 경로 여러 개 보기**를 누르면 현재 노선의 최대 4개 환승 경로가 열린다. 노선을 바꾸거나 카드를 누르면 추천·최단시간·최소환승 태그, 도착 시각, 환승 횟수, 걷는 시간과 지도의 강조 경로가 함께 바뀐다. 화면을 재생성해도 노선과 경로 선택을 복원한다.

환승 선은 `map.geojson`의 노선 번호와 경로 요약이 일치할 때만 그린다. 현재 예시에는 8800번 환승 좌표만 있다. 다른 노선은 지도 좌표가 없다는 안내와 경로 목록을 표시한다. 도착 시각이 없는 항목은 서울 구간 소요 시간을 표시한다. 목록이 비어 있거나 파일을 읽지 못하면 빈 상태·다시 시도를 보여준다.

A·B 화면에서 특정 노선의 경로 목록을 열 때도 사용할 수 있다.

```java
startActivity(RouteChoicesActivity.newIntent(this, "3007"));
```

Android 15 에뮬레이터에서 카드 선택·노선 변경·좌표 없음 안내·회전 후 선택 복원·뒤로 가기를 확인했다.

[원래·대안 경로 비교](docs/week12-comparison.png) · [환승 경로 목록](docs/week12-route-choices.png) · [좌표 없는 노선](docs/week12-missing-shape.png)

예시 JSON 세 파일은 저장소 `docs/examples`에서 빌드 시 포함한다. 서버 연결은 13주차 일정이다. 데이터 연결 회귀 테스트는 `./gradlew :map:testDebugUnitTest`로 실행한다.

## 공통 앱에 연결

공통 앱이 `:map`을 포함하고 `implementation project(':map')`에 의존하도록 한다. 루트 저장소 설정에는 이 프로젝트의 `settings.gradle`과 같은 카카오맵 Maven 저장소를 추가한다. 앱의 `application` 아래에는 시연 앱 매니페스트와 같은 이름의 키 메타데이터를 등록한다. 값은 앱의 문자열 리소스를 `android:resource`로 참조한다. 통합 앱의 패키지와 서명 키 해시도 카카오에 등록해야 한다.

```java
startActivity(new Intent(this, kr.ac.kookmin.seatforecast.map.MapActivity.class));
```
