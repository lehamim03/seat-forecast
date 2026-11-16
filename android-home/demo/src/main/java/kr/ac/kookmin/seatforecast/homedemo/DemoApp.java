package kr.ac.kookmin.seatforecast.homedemo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.data.AssetRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 시연 앱: 가짜 로그인 · 설정(DemoHomeHost) + assets 의 예시 응답(recommendations.json).
 * 13주차 서버 연결 때는 new ApiRecommendationSource(BuildConfig.SERVER_BASE_URL) 로 바꿉니다.
 *
 * <p>상태 화면 확인용으로 실행할 때 {@code demo_mode} 를 넘길 수 있습니다 (README 참고):
 * <pre>
 * adb shell am start -n kr.ac.kookmin.seatforecast.homedemo/kr.ac.kookmin.seatforecast.home.ui.HomeActivity --es demo_mode cases
 * </pre>
 * cases(경우 모음 home_cases.json) · empty(빈 목록) · error(오류) · signed_out(로그인 필요) · loading(로딩). 없으면 보통 화면.
 */
public class DemoApp extends Application {

    static final String EXTRA_MODE = "demo_mode";

    private final DemoHomeHost host = new DemoHomeHost();

    @Override
    public void onCreate() {
        super.onCreate();
        apply(null);
        // 홈 화면(HomeActivity)이 만들어질 때 intent 의 demo_mode 로 바꿈 — Fragment 가 불러오기 전
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle saved) {
                apply(a.getIntent() == null ? null : a.getIntent().getStringExtra(EXTRA_MODE));
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
        String m = mode == null ? "" : mode;
        host.signedIn = !m.equals("signed_out");
        Home.init(host, source(m));
    }

    private RecommendationSource source(String mode) {
        AssetRecommendationSource example = new AssetRecommendationSource(this);
        switch (mode) {
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
