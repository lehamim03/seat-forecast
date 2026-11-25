package kr.ac.kookmin.seatforecast.map;

/**
 * 13주차: 지도 · 경로 목록이 읽을 예측 서버(server.py) 주소.
 * 팀 앱이 {@link #setBaseUrl} 로 주소를 정하면 GET /api/v1/map · /api/v1/route-choices · /api/v1/recommendations
 * 를 부르고, 정하지 않으면 지금까지처럼 assets 의 예시 JSON(docs/examples)을 씁니다.
 */
public final class MapServer {
    private static volatile String baseUrl;
    private static volatile String homeStop;
    private static volatile String route;

    private MapServer() {
    }

    /** 예: "http://10.0.2.2:8000/". null 이면 예시 JSON. */
    public static void setBaseUrl(String url) {
        baseUrl = url == null || url.trim().isEmpty() ? null : (url.endsWith("/") ? url : url + "/");
    }

    /** 평소 정류장 · 노선 (없으면 서버 기본값). */
    public static void setUsualTrip(String stopId, String routeNumber) {
        homeStop = stopId;
        route = routeNumber;
    }

    static String baseUrl() {
        return baseUrl;
    }

    static boolean isLive() {
        return baseUrl != null;
    }

    /** 서버 주소 + path + 평소 정류장 · 노선 쿼리 */
    static String url(String path, boolean withRoute) {
        StringBuilder s = new StringBuilder(baseUrl).append(path);
        char sep = '?';
        if (homeStop != null && !homeStop.isEmpty()) {
            s.append(sep).append("home_stop=").append(homeStop);
            sep = '&';
        }
        if (withRoute && route != null && !route.isEmpty()) s.append(sep).append("route=").append(route);
        return s.toString();
    }
}
