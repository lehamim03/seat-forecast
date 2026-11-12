package kr.ac.kookmin.seatforecast.account;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * 다른 화면(A~E)에서 쓰는 계정 관련 진입점.
 *
 * <pre>
 * if (!Account.isSignedIn()) startActivity(new Intent(this, LoginActivity.class));
 * Account.getIdToken(callback);                        // 서버 요청 Authorization 헤더용
 * new UserRepository().loadSettings(uid, callback);    // 평소 정류장 · 노선 (/recommendations 요청 값)
 * </pre>
 */
public final class Account {

    private static String serverBaseUrl = "http://10.0.2.2:8000/";

    private Account() {
    }

    /** 예측 서버 주소 (끝에 / 포함). 앱 시작 시 한 번 설정합니다. */
    public static void setServerBaseUrl(String baseUrl) {
        serverBaseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public static String getServerBaseUrl() {
        return serverBaseUrl;
    }

    /**
     * 로컬 Firebase 에뮬레이터를 씁니다 (개발 · 테스트용). Firebase 를 처음 쓰기 전에 불러야 합니다.
     * 안드로이드 에뮬레이터에서 내 컴퓨터는 10.0.2.2 입니다.
     */
    public static void useEmulator(String host) {
        FirebaseAuth.getInstance().useEmulator(host, 9099);
        FirebaseFirestore.getInstance().useEmulator(host, 8080);
    }

    private static final String PREFS = "kr.ac.kookmin.seatforecast.account";
    private static final String KEY_KEEP_SIGNED_IN = "keep_signed_in";

    /**
     * 앱 시작 시 한 번 (Application.onCreate, Firebase 초기화 뒤).
     * 로그인 화면에서 '로그인 상태 유지'를 끄고 로그인했다면, 앱을 다시 켤 때 로그아웃합니다.
     */
    public static void init(Context context) {
        if (!prefs(context).getBoolean(KEY_KEEP_SIGNED_IN, true)) signOut();
    }

    public static void setKeepSignedIn(Context context, boolean keep) {
        prefs(context).edit().putBoolean(KEY_KEEP_SIGNED_IN, keep).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ---------- 미리보기 (로그인 없이 둘러보기) ----------
    // 시연 · 발표 영상용. 예시 사용자로 화면만 보여 주고, 설정 · 피드백은 Firebase 대신 앱 메모리에만 저장합니다.

    public static final String PREVIEW_UID = "preview-user";
    public static final String PREVIEW_EMAIL = "demo@kookmin.ac.kr";
    public static final String PREVIEW_NICKNAME = "둘러보기";

    private static boolean previewAllowed = false;
    private static boolean preview = false;

    /** 로그인 화면에 '로그인 없이 둘러보기' 버튼을 보일지 (기본 꺼짐, 시연 앱에서만 켬). */
    public static void setPreviewAllowed(boolean allowed) {
        previewAllowed = allowed;
    }

    public static boolean isPreviewAllowed() {
        return previewAllowed;
    }

    /** 로그인 없이 예시 사용자로 둘러보기 시작. signOut() 하면 끝납니다. */
    public static void startPreview() {
        preview = true;
    }

    public static boolean isPreview() {
        return preview;
    }

    public static boolean isSignedIn() {
        return preview || FirebaseAuth.getInstance().getCurrentUser() != null;
    }

    @Nullable
    public static String currentUid() {
        if (preview) return PREVIEW_UID;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getUid();
    }

    @Nullable
    public static String currentEmail() {
        if (preview) return PREVIEW_EMAIL;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user == null ? null : user.getEmail();
    }

    /** 서버 요청 헤더 {@code Authorization: Bearer <토큰>} 에 넣을 Firebase ID 토큰. */
    public static void getIdToken(Callback<String> callback) {
        if (preview) {
            callback.onError("둘러보기 중에는 서버에 보내지 않습니다.");
            return;
        }
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }
        user.getIdToken(false)
                .addOnSuccessListener(r -> callback.onSuccess(r.getToken()))
                .addOnFailureListener(e -> callback.onError("인증 토큰을 받지 못했습니다."));
    }

    public static void signOut() {
        preview = false;
        FirebaseAuth.getInstance().signOut();
    }
}
