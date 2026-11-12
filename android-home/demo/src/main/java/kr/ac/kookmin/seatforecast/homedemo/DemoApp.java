package kr.ac.kookmin.seatforecast.homedemo;

import android.app.Application;

import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.data.AssetRecommendationSource;

/**
 * 시연 앱: 가짜 로그인 · 설정(DemoHomeHost) + assets 의 예시 응답(recommendations.json).
 * 13주차 서버 연결 때는 new ApiRecommendationSource(BuildConfig.SERVER_BASE_URL) 로 바꿉니다.
 */
public class DemoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Home.init(new DemoHomeHost(), new AssetRecommendationSource(this));
    }
}
