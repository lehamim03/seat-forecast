package kr.ac.kookmin.seatforecast.home;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.data.PlaceSource;
import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.ReplaySource;

/**
 * 홈 모듈 진입점. 앱 시작 시(Application.onCreate) 한 번:
 * <pre>
 * Home.init(new MyHomeHost(), new ApiRecommendationSource("https://&lt;서버&gt;/")); // 예측 서버 (추천 · 장소 검색)
 * Home.init(new MyHomeHost(), new AssetRecommendationSource(this));          // 서버 없이 (예시 JSON)
 * </pre>
 * 추천 소스가 {@link PlaceSource} 도 구현하면(두 클래스 모두 구현) 장소 검색도 그것을 씁니다.
 */
public final class Home {

    @Nullable
    private static HomeHost host;
    @Nullable
    private static RecommendationSource source;
    @Nullable
    private static PlaceSource places;

    private Home() {
    }

    public static void init(HomeHost host, RecommendationSource source) {
        init(host, source, source instanceof PlaceSource ? (PlaceSource) source : null);
    }

    public static void init(HomeHost host, RecommendationSource source, @Nullable PlaceSource places) {
        Home.host = host;
        Home.source = source;
        Home.places = places;
    }

    public static HomeHost host() {
        if (host == null) throw new IllegalStateException("Home.init(host, source) 를 먼저 불러 주세요.");
        return host;
    }

    public static RecommendationSource source() {
        if (source == null) throw new IllegalStateException("Home.init(host, source) 를 먼저 불러 주세요.");
        return source;
    }

    /** 지난 출근길 재생 — 추천 소스가 지원하면 그것, 아니면 null (홈의 '지난 출근길' 버튼을 숨김) */
    @Nullable
    public static ReplaySource replays() {
        return source instanceof ReplaySource ? (ReplaySource) source : null;
    }

    /** 장소 검색 — 없으면 검색 화면에서 즐겨찾기 · 현재 위치만 고를 수 있음 */
    @Nullable
    public static PlaceSource places() {
        return places;
    }
}
