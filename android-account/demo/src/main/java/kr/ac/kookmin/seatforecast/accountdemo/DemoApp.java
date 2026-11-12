package kr.ac.kookmin.seatforecast.accountdemo;

import android.app.Application;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import kr.ac.kookmin.seatforecast.account.Account;

/**
 * google-services.json 이 없으면 로컬 Firebase 에뮬레이터(demo- 프로젝트)로 연결합니다.
 * 팀 앱에서는 google-services.json 만 넣으면 되고, 이 코드는 필요 없습니다.
 */
public class DemoApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
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
        Account.init(this);   // '로그인 상태 유지'를 끄고 로그인했으면 여기서 로그아웃
    }
}
