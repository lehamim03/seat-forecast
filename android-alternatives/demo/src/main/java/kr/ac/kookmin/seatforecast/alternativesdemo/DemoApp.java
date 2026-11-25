package kr.ac.kookmin.seatforecast.alternativesdemo;

import android.app.Activity;
import android.app.Application;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import kr.ac.kookmin.seatforecast.alternatives.Alternatives;
import kr.ac.kookmin.seatforecast.alternatives.Place;
import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.ApiAlternativesSource;
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
 * relaxed(여유) · risk(만석 위험) · cases(경우 모음 alt_cases.json) · empty(대안 없음) · error(오류) · signed_out(로그인 필요) · loading(조회 중)
 * · live(갱신마다 값이 바뀜) · flaky(첫 갱신 뒤 실패) · server(13주차: 팀 server.py + Firebase 에뮬레이터 토큰)
 * · example(출발지 · 도착지 예시 trip_alternatives.json, 서버 꺼짐 대비).
 * 출발지 · 도착지는 extra 6개로: --ed from_lat 37.385 --ed from_lng 127.1233 --es from_name 서현역 --ed to_lat … (없으면 현재 위치 + '학교')
 * 없으면 팀 server.py 에 실제로 요청 (토큰은 demo-id-token — 서버의 로그인 확인이 꺼져 있을 때).
 * 자동 갱신 간격은 {@code --ei demo_refresh_sec 10} 으로 줄일 수 있습니다 (기본 1분).
 */
public class DemoApp extends Application {

    static final String EXTRA_MODE = "demo_mode";
    static final String EXTRA_REFRESH_SEC = "demo_refresh_sec";

    private final DemoAltHost host = new DemoAltHost();

    @Override
    public void onCreate() {
        super.onCreate();
        apply(null);
        // 대안 화면(AlternativesActivity)이 만들어질 때 intent 의 demo_mode 로 바꿈 — Fragment 가 불러오기 전
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(@NonNull Activity a, @Nullable Bundle saved) {
                apply(a.getIntent());
            }

            @Override public void onActivityStarted(@NonNull Activity a) { }
            @Override public void onActivityResumed(@NonNull Activity a) { }
            @Override public void onActivityPaused(@NonNull Activity a) { }
            @Override public void onActivityStopped(@NonNull Activity a) { }
            @Override public void onActivitySaveInstanceState(@NonNull Activity a, @NonNull Bundle out) { }
            @Override public void onActivityDestroyed(@NonNull Activity a) { }
        });
    }

    private void apply(@Nullable Intent intent) {
        String mode = intent == null ? null : intent.getStringExtra(EXTRA_MODE);
        String m = mode == null ? "" : mode;
        host.signedIn = !m.equals("signed_out");
        host.authEmulator = m.equals("server") ? BuildConfig.AUTH_EMULATOR_URL : null;
        Alternatives.init(host, source(m));
        // 자동 갱신 간격 — 기본 1분, 시연 때 --ei demo_refresh_sec 10 처럼 줄여서 확인
        int sec = intent == null ? 0 : intent.getIntExtra(EXTRA_REFRESH_SEC, 0);
        Alternatives.setRefreshIntervalMs(sec > 0 ? sec * 1000L : Alternatives.DEFAULT_REFRESH_MS);
    }

    private AlternativesSource source(String mode) {
        AssetAlternativesSource example = new AssetAlternativesSource(this);
        switch (mode) {
            case "relaxed":
                // 시연용 가상 값: 다가오는 8800 에 자리가 있을 때 (여유) — 바로 타면 09:46 도착 (기획서 21쪽 값)
                return (from, to, token) -> {
                    AlternativesResult r = usualBus(example.load(from, to, token), 14, 0.92,
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
                return (from, to, token) -> usualBus(example.load(from, to, token), 3, 0.55, null);
            case "cases":
                return new AssetAlternativesSource(this, "alt_cases.json");
            case "server":
            case "":
                // 13주차: 팀 server.py GET /api/v1/alternatives — 에뮬레이터에서 10.0.2.2 = 내 컴퓨터
                return new ApiAlternativesSource(BuildConfig.SERVER_BASE_URL);
            case "example":
                // 출발지 · 도착지 모드 예시 (docs/examples/trip_alternatives.json 복사본, 서버 꺼짐 대비)
                return new AssetAlternativesSource(this, "trip_alternatives.json");
            case "live":
                // 시연용 가상 값: 불러올 때마다 다가오는 차가 1분씩 가까워지고 좌석이 줄어듦 (자동 갱신 확인)
                return new AlternativesSource() {
                    private int n;

                    @NonNull
                    @Override
                    public AlternativesResult load(@NonNull Place from, @NonNull Place to, @Nullable String token)
                            throws SourceException {
                        AlternativesResult r = example.load(from, to, token);
                        int step = n++;
                        r.generatedAt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.KOREA).format(new Date());
                        for (Option o : r.options()) {
                            for (Bus b : o.buses) b.etaMin = Math.max(0, b.etaMin - step);
                        }
                        Option other = r.alternatives == null ? null : r.alternatives.otherBus;
                        if (other != null && !other.buses.isEmpty() && other.buses.get(0).seatsAtStop != null) {
                            other.buses.get(0).seatsAtStop = Math.max(0, other.buses.get(0).seatsAtStop - 3 * step);
                        }
                        return r;
                    }
                };
            case "flaky":
                // 처음 한 번만 성공, 이후 갱신은 실패 → 이전 정보 유지 + '갱신하지 못했어요'
                return new AlternativesSource() {
                    private boolean first = true;

                    @NonNull
                    @Override
                    public AlternativesResult load(@NonNull Place from, @NonNull Place to, @Nullable String token)
                            throws SourceException {
                        if (first) {
                            first = false;
                            return example.load(from, to, token);
                        }
                        throw new SourceException("서버에 연결할 수 없습니다. (시연: 갱신 실패)", null);
                    }
                };
            case "empty":
                return (from, to, token) -> {
                    AlternativesResult r = example.load(from, to, token);
                    r.recommendation = null;
                    r.alternatives = null;
                    return r;
                };
            case "error":
                return (from, to, token) -> {
                    throw new SourceException("실시간 대안 서버에 연결하지 못했습니다. (시연: 오류 상태)", null);
                };
            case "loading":
                return (from, to, token) -> {
                    try {
                        Thread.sleep(10 * 60 * 1000L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return example.load(from, to, token);
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
