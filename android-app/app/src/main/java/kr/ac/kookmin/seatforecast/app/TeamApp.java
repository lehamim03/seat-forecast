package kr.ac.kookmin.seatforecast.app;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.alternatives.Alternatives;
import kr.ac.kookmin.seatforecast.alternatives.data.ApiAlternativesSource;
import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.data.ApiRecommendationSource;
import kr.ac.kookmin.seatforecast.map.MapServer;

/**
 * 팀 앱 시작 — 각 담당 모듈 README 의 '팀 앱 모듈에 붙이기' 순서 그대로.
 * D(Firebase) → A 홈 · B 실시간 대안 (13주차: 예측 서버 server.py 에 실제 요청).
 */
public class TeamApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // D: google-services.json 이 없으면 로컬 Firebase 에뮬레이터 (휴대폰에서는 '로그인 없이 둘러보기'로 확인)
        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            if (FirebaseApp.getApps(this).isEmpty()) {
                FirebaseApp.initializeApp(this, new FirebaseOptions.Builder()
                        .setProjectId("demo-seat-forecast")
                        .setApplicationId("1:000000000000:android:0000000000000000")
                        .setApiKey("demo-api-key")
                        .build());
            }
            Account.useEmulator("10.0.2.2");
        }
        Account.setServerBaseUrl(BuildConfig.SERVER_BASE_URL);
        Account.setPreviewAllowed(true);
        Account.init(this);

        // A · B: 13주차 예측 서버 연결 — GET /api/v1/recommendations · /api/v1/alternatives
        Home.init(new TeamHomeHost(), new ApiRecommendationSource(BuildConfig.SERVER_BASE_URL));
        Alternatives.init(new TeamAltHost(), new ApiAlternativesSource(BuildConfig.SERVER_BASE_URL));
        // C: 13주차 지도 · 경로 목록 — GET /api/v1/map · /api/v1/route-choices · /api/v1/recommendations
        MapServer.setBaseUrl(BuildConfig.SERVER_BASE_URL);
    }
}
