package kr.ac.kookmin.seatforecast.mapdemo;

import android.app.Application;

import kr.ac.kookmin.seatforecast.map.MapServer;

/** 지도 시연 앱: 13주차 예측 서버(server.py)에서 지도 · 경로 목록 · 추천을 읽음 */
public class DemoApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        MapServer.setBaseUrl(BuildConfig.SERVER_BASE_URL);
    }
}
