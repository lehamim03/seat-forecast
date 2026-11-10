# C · 지도 (10주차)

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
- 이번 주에는 위치 권한까지 준비한다. 기기 좌표 표시·도보·노선 그리기는 11주차, 경로 목록은 12주차, 서버 연동은 13주차 일정이다.

## 공통 앱에 연결

공통 앱이 `:map`을 포함하고 `implementation project(':map')`에 의존하도록 한다. 루트 저장소 설정에는 이 프로젝트의 `settings.gradle`과 같은 카카오맵 Maven 저장소를 추가한다. 앱의 `application` 아래에는 시연 앱 매니페스트와 같은 이름의 키 메타데이터를 등록한다. 값은 앱의 문자열 리소스를 `android:resource`로 참조한다. 통합 앱의 패키지와 서명 키 해시도 카카오에 등록해야 한다.

```java
startActivity(new Intent(this, kr.ac.kookmin.seatforecast.map.MapActivity.class));
```
