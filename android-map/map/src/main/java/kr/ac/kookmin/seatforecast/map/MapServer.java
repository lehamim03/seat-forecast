package kr.ac.kookmin.seatforecast.map;

import android.net.Uri;

/**
 * 지도 · 경로 목록이 읽을 예측 서버(server.py) 주소와 마지막 출발지 · 도착지.
 * 팀 앱이 {@link #setBaseUrl} 로 주소를 정하면 GET /api/v1/map · /api/v1/route-choices · /api/v1/recommendations 를
 * from_* · to_* 와 함께 부르고, 정하지 않았거나 서버에 연결하지 못하면 assets 의 예시 JSON(docs/examples)을 씁니다.
 */
public final class MapServer {
    private static volatile String baseUrl;
    private static volatile Place lastFrom;
    private static volatile Place lastTo;

    private MapServer() {
    }

    /** 예: "http://10.0.2.2:8000/". null 이면 예시 JSON. */
    public static void setBaseUrl(String url) {
        baseUrl = url == null || url.trim().isEmpty() ? null : (url.endsWith("/") ? url : url + "/");
    }

    /**
     * 지도 탭을 extra 없이 열 때 쓸 마지막 출발지 · 도착지 (A 홈이 검색할 때 = D last_trip 과 같은 값).
     * from 이 null 이거나 "현재 위치"면 기기 위치를 씁니다.
     */
    public static void setLastTrip(Place from, Place to) {
        lastFrom = from;
        lastTo = to;
    }

    static Place lastFrom() {
        return lastFrom;
    }

    static Place lastTo() {
        return lastTo;
    }

    static String baseUrl() {
        return baseUrl;
    }

    static boolean isLive() {
        return baseUrl != null;
    }

    /** 서버 주소 + path + from_* · to_* (docs/api.md '출발지 · 도착지 모드') */
    static String url(String path, Place from, Place to) {
        return url(baseUrl, path, from, to);
    }

    static String url(String base, String path, Place from, Place to) {
        return base + path + "?" + query(from, to);
    }

    /** from_lat=…&from_lng=…&from_name=…&to_lat=…&to_lng=…&to_name=… (이름은 URL 인코딩, 좌표는 점 소수) */
    static String query(Place from, Place to) {
        return "from_lat=" + from.lat + "&from_lng=" + from.lng + "&from_name=" + Uri.encode(from.nameOr(Place.CURRENT_LOCATION))
                + "&to_lat=" + to.lat + "&to_lng=" + to.lng + "&to_name=" + Uri.encode(to.nameOr("도착지"));
    }
}
