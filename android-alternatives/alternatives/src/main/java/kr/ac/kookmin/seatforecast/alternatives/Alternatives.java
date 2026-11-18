package kr.ac.kookmin.seatforecast.alternatives;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesSource;

/**
 * 실시간 대안 모듈 진입점. 앱 시작 시(Application.onCreate) 한 번:
 * <pre>
 * Alternatives.init(new MyAltHost(), new AssetAlternativesSource(this));            // 지금 (예시 JSON)
 * Alternatives.init(new MyAltHost(), new ApiAlternativesSource("https://&lt;서버&gt;/")); // 13주차 서버 연결
 * </pre>
 */
public final class Alternatives {

    @Nullable
    private static AlternativesHost host;
    @Nullable
    private static AlternativesSource source;

    /** 화면이 보이는 동안 자동으로 다시 불러오는 간격 — 기본 1분 (12주차) */
    public static final long DEFAULT_REFRESH_MS = 60_000L;

    private static long refreshIntervalMs = DEFAULT_REFRESH_MS;

    private Alternatives() {
    }

    /** 자동 갱신 간격(밀리초). 0 이하면 자동 갱신을 끕니다 (시연 앱에서 짧게 바꿔 확인할 때 사용). */
    public static void setRefreshIntervalMs(long ms) {
        refreshIntervalMs = ms;
    }

    public static long refreshIntervalMs() {
        return refreshIntervalMs;
    }

    public static void init(AlternativesHost host, AlternativesSource source) {
        Alternatives.host = host;
        Alternatives.source = source;
    }

    public static AlternativesHost host() {
        if (host == null) throw new IllegalStateException("Alternatives.init(host, source) 를 먼저 불러 주세요.");
        return host;
    }

    public static AlternativesSource source() {
        if (source == null) throw new IllegalStateException("Alternatives.init(host, source) 를 먼저 불러 주세요.");
        return source;
    }
}
