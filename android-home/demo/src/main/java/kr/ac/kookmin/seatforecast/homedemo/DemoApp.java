package kr.ac.kookmin.seatforecast.homedemo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.data.ApiRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.AssetRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 시연 앱: 가짜 로그인 · 설정(DemoHomeHost) + 예측 서버 {@code GET /api/v1/recommendations}.
 * 13주차: 기본(또는 {@code demo_mode server})은 예측 서버에 실제로 요청하고, {@code demo_mode example} 이면
 * assets 의 예시 응답(recommendations.json)을 씁니다
 * (주소 = {@code server_url} 값, 없으면 BuildConfig.SERVER_BASE_URL = 에뮬레이터에서 내 컴퓨터 8000번).
 * 서버가 아직 없으면 {@code android-home/tools/mock_server.py} 로 같은 형식의 테스트 서버를 띄웁니다.
 *
 * <p>상태 화면 확인용으로 실행할 때 {@code demo_mode} 를 넘길 수 있습니다 (README 참고):
 * <pre>
 * adb shell am start -n kr.ac.kookmin.seatforecast.homedemo/kr.ac.kookmin.seatforecast.home.ui.HomeActivity --es demo_mode cases
 * </pre>
 * cases(경우 모음 home_cases.json) · empty(빈 목록) · error(오류) · signed_out(로그인 필요) · loading(로딩)
 * · refresh_error(처음엔 보통, 새로고침부터 실패) · example(예시 JSON) · server(13주차 서버 연결). 없으면 서버.
 * <pre>
 * adb shell am start -n ... --es demo_mode server --es server_url http://192.168.0.10:8000/
 * </pre>
 */
public class DemoApp extends Application {

    static final String EXTRA_MODE = "demo_mode";
    static final String EXTRA_SERVER_URL = "server_url";

    private final DemoHomeHost host = new DemoHomeHost();

    @Override
    public void onCreate() {
        super.onCreate();
        apply(null);
        // 홈 화면(HomeActivity)이 만들어질 때 intent 의 demo_mode 로 바꿈 — Fragment 가 불러오기 전
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle saved) {
                if (a.getIntent() == null) apply(null);
                else apply(a.getIntent().getStringExtra(EXTRA_MODE), a.getIntent().getStringExtra(EXTRA_SERVER_URL));
            }

            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityResumed(@NonNull Activity a) { }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle out) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    private void apply(@Nullable String mode) {
        apply(mode, null);
    }

    private void apply(@Nullable String mode, @Nullable String serverUrl) {
        String m = mode == null ? "" : mode;
        host.signedIn = !m.equals("signed_out");
        Home.init(host, source(m, serverUrl));
    }

    private RecommendationSource source(String mode, @Nullable String serverUrl) {
        AssetRecommendationSource example = new AssetRecommendationSource(this);
        switch (mode) {
            case "example":
                return example;
            case "server":
            case "":
                // 13주차: 실제 HTTP 요청 (Bearer demo-id-token, 401 이면 demo-id-token-refreshed 로 한 번 더)
                return new ApiRecommendationSource(
                        serverUrl == null || serverUrl.trim().isEmpty() ? BuildConfig.SERVER_BASE_URL : serverUrl);
            case "cases":
                return new AssetRecommendationSource(this, "home_cases.json");
            case "empty":
                return (stop, route, token) -> {
                    Recommendations r = example.load(stop, route, token);
                    r.recommendation = null;
                    r.ranking.clear();
                    return r;
                };
            case "error":
                return (stop, route, token) -> {
                    throw new SourceException("추천 서버에 연결하지 못했습니다. (시연: 오류 상태)", null);
                };
            case "refresh_error": {
                // 12주차: 처음엔 보통 화면, 그다음 새로고침부터는 1초 뒤 실패 → 이전 목록 + '갱신 실패' 확인
                final int[] calls = {0};
                return (stop, route, token) -> {
                    if (calls[0]++ == 0) return example.load(stop, route, token);
                    try {
                        Thread.sleep(1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    throw new SourceException("추천 서버에 연결하지 못했습니다. (시연: 새로고침 실패)", null);
                };
            }
            case "loading":
                return (stop, route, token) -> {
                    try {
                        Thread.sleep(10 * 60 * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return example.load(stop, route, token);
                };
            default:
                return example;
        }
    }
}
