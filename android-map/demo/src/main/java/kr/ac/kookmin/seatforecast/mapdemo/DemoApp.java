package kr.ac.kookmin.seatforecast.mapdemo;

import android.app.Application;

import kr.ac.kookmin.seatforecast.map.MapServer;
import kr.ac.kookmin.seatforecast.map.Place;

/**
 * 지도 시연 앱: 예측 서버(server.py)에서 출발지 · 도착지 모드로 지도 · 경로 목록 · 추천을 읽음 (서버 꺼짐이면 예시).
 * 마지막 출발지 · 도착지는 시연용 가짜 값 — 출발 "현재 위치"(기기 위치) → 도착 '학교'.
 * 다른 곳으로 시험: adb shell am start -n …/.MapLauncher --ed to_lat 37.5118 --ed to_lng 127.0576 --es to_name 코엑스
 */
public class DemoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapServer.setBaseUrl(BuildConfig.SERVER_BASE_URL);
        MapServer.setLastTrip(null, new Place("학교", 37.610977, 126.995185));
    }
}
