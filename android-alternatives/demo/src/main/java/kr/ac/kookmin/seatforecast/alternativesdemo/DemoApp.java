package kr.ac.kookmin.seatforecast.alternativesdemo;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.alternatives.Alternatives;
import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.AssetAlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 시연 앱: 가짜 로그인 · 설정(DemoAltHost) + assets 의 예시 응답(alternatives.json).
 * 13주차 서버 연결 때는 new ApiAlternativesSource(BuildConfig.SERVER_BASE_URL) 로 바꿉니다.
 *
 * <p>상태 화면 확인용으로 실행할 때 {@code demo_mode} 를 넘길 수 있습니다 (README 참고):
 * <pre>
 * adb shell am start -n kr.ac.kookmin.seatforecast.alternativesdemo/kr.ac.kookmin.seatforecast.alternatives.ui.AlternativesActivity --es demo_mode relaxed
 * </pre>
 * relaxed(여유) · risk(만석 위험) · cases(경우 모음 alt_cases.json) · empty(대안 없음) · error(오류) · signed_out(로그인 필요) · loading(조회 중).
 * 없으면 보통 화면 = docs 예시 (평소 8800 만석).
 */
public class DemoApp extends Application {

    static final String EXTRA_MODE = "demo_mode";

    private final DemoAltHost host = new DemoAltHost();

    @Override
    public void onCreate() {
        super.onCreate();
        apply(null);
        // 대안 화면(AlternativesActivity)이 만들어질 때 intent 의 demo_mode 로 바꿈 — Fragment 가 불러오기 전
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
        Alternatives.init(host, source(m));
    }

    private AlternativesSource source(String mode) {
        AssetAlternativesSource example = new AssetAlternativesSource(this);
        switch (mode) {
            case "relaxed":
                // 시연용 가상 값: 다가오는 8800 에 자리가 있을 때 (여유) — 바로 타면 09:46 도착 (기획서 21쪽 값)
                return (stop, route, token) -> {
                    AlternativesResult r = usualBus(example.load(stop, route, token), 14, 0.92,
                            "평소대로 8800 (삼성1차아파트) — 다가오는 차에 자리가 있어요");
                    Option wait = r.usual();
                    if (wait != null) {
                        wait.arriveAt = "09:46";
                        wait.totalMin = 126;
                    }
                    return r;
                };
            case "risk":
                // 시연용 가상 값: 다가오는 8800 이 만석일 수 있을 때 (만석 위험)
                return (stop, route, token) -> usualBus(example.load(stop, route, token), 3, 0.55, null);
            case "cases":
                return new AssetAlternativesSource(this, "alt_cases.json");
            case "empty":
                return (stop, route, token) -> {
                    AlternativesResult r = example.load(stop, route, token);
                    r.recommendation = null;
                    r.alternatives = null;
                    return r;
                };
            case "error":
                return (stop, route, token) -> {
                    throw new SourceException("실시간 대안 서버에 연결하지 못했습니다. (시연: 오류 상태)", null);
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

    /** 예시 응답의 '기다리기'(평소 8800) 첫 차 좌석 · 확률만 바꿈 */
    private static AlternativesResult usualBus(AlternativesResult r, int seats, double prob,
                                               @Nullable String message) {
        Option wait = r.usual();
        if (wait != null && !wait.buses.isEmpty()) {
            Bus first = wait.buses.get(0);
            first.seatsAtStop = seats;
            first.boardProb = prob;
            wait.boardProb = prob;
            if (message != null && r.recommendation != null) {
                r.recommendation.kind = Option.KIND_WAIT;
                r.recommendation.route = wait.route;
                r.recommendation.stopName = wait.stop.name;
                r.recommendation.message = message;
            }
        }
        return r;
    }
}
