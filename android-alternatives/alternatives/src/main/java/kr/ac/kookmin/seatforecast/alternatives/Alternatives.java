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

    private Alternatives() {
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
