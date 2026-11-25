package kr.ac.kookmin.seatforecast.alternativesdemo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

import kr.ac.kookmin.seatforecast.alternatives.AlternativesHost;
import kr.ac.kookmin.seatforecast.alternatives.Callback;
import kr.ac.kookmin.seatforecast.alternatives.Place;
import kr.ac.kookmin.seatforecast.alternatives.TripExtras;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 가짜 값으로 채운 AlternativesHost — 로그인 상태(demo_mode=signed_out 이면 로그아웃), 마지막 도착지 '학교'(시연용 가짜 값).
 * 팀 앱에서는 D 의 Account · UserRepository 로 채웁니다 (android-alternatives/README.md).
 *
 * <p>13주차 서버 연결 확인(demo_mode=server): 로컬 Firebase Auth 에뮬레이터에서 익명 가입해 받은 진짜 ID 토큰을 씁니다.
 * 서버(팀 server.py)가 그 토큰을 Firebase 로 확인합니다.
 */
class DemoAltHost implements AlternativesHost {

    /** 시연용 마지막 도착지 (실제 앱은 D 의 destination · last_trip.to) */
    static final Place SCHOOL = new Place("학교", 37.610977, 126.995185);

    boolean signedIn = true;
    /** Firebase Auth 에뮬레이터 주소 (예: http://10.0.2.2:9099/), null 이면 가짜 토큰 */
    @Nullable
    String authEmulator;

    private final Handler main = new Handler(Looper.getMainLooper());
    @Nullable
    private String cachedToken;

    @Override
    public boolean isSignedIn() {
        return signedIn;
    }

    @Override
    public void requestSignIn(Context context) {
        Toast.makeText(context, "로그인 화면 (D 담당)", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void loadLastDestination(Callback<Place> callback) {
        callback.onSuccess(SCHOOL);
    }

    @Override
    public void getIdToken(Callback<String> callback) {
        if (authEmulator == null) {
            callback.onSuccess("demo-id-token");
            return;
        }
        if (cachedToken != null) {   // 에뮬레이터 토큰은 1시간 유효 — 시연 동안은 하나로 충분
            callback.onSuccess(cachedToken);
            return;
        }
        final String base = authEmulator;
        new Thread(() -> {
            try {
                String token = signUpAnonymously(base);
                main.post(() -> {
                    cachedToken = token;
                    callback.onSuccess(token);
                });
            } catch (Exception e) {
                main.post(() -> callback.onError("로그인 토큰을 받지 못했습니다. Firebase 에뮬레이터가 켜져 있는지 확인하세요."));
            }
        }).start();
    }

    /** Firebase Auth 에뮬레이터 REST: 익명 가입 → idToken */
    private static String signUpAnonymously(String base) throws Exception {
        URL url = new URL(base + "identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-key");
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(5000);
        c.setReadTimeout(5000);
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = c.getOutputStream()) {
            out.write("{\"returnSecureToken\":true}".getBytes(StandardCharsets.UTF_8));
        }
        try (InputStream in = c.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return new JSONObject(s.hasNext() ? s.next() : "{}").getString("idToken");
        } finally {
            c.disconnect();
        }
    }

    @Override
    public void onOptionClicked(Context context, Option option, Place from, Place to) {
        Toast.makeText(context, option.kind + " · " + option.route + " — 탑승 피드백 (D 담당)\n"
                        + TripExtras.FROM_NAME + "=" + from.name + " → " + TripExtras.TO_NAME + "=" + to.name,
                Toast.LENGTH_LONG).show();
    }
}
