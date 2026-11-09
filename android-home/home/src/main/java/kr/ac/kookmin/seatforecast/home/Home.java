package kr.ac.kookmin.seatforecast.home;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;

/**
 * 홈 모듈 진입점. 앱 시작 시(Application.onCreate) 한 번:
 * <pre>
 * Home.init(new MyHomeHost(), new AssetRecommendationSource(this));          // 지금 (예시 JSON)
 * Home.init(new MyHomeHost(), new ApiRecommendationSource("https://<서버>/")); // 13주차 서버 연결
 * </pre>
 */
public final class Home {

    @Nullable
    private static HomeHost host;
    @Nullable
    private static RecommendationSource source;

    private Home() {
    }

    public static void init(HomeHost host, RecommendationSource source) {
        Home.host = host;
        Home.source = source;
    }

    public static HomeHost host() {
        if (host == null) throw new IllegalStateException("Home.init(host, source) 를 먼저 불러 주세요.");
        return host;
    }

    public static RecommendationSource source() {
        if (source == null) throw new IllegalStateException("Home.init(host, source) 를 먼저 불러 주세요.");
        return source;
    }
}
