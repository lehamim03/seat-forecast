package kr.ac.kookmin.seatforecast.account.data;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

import kr.ac.kookmin.seatforecast.account.Callback;

/**
 * Firestore users/{uid} 읽기 · 쓰기.
 * 문서 하나에 회원 정보(email, nickname, created_at)와 마이페이지 설정(settings.json 필드)을 함께 둡니다.
 * 항상 merge 로 써서 다른 화면이 같은 문서에 넣은 필드(예: FCM 토큰)를 지우지 않습니다.
 */
public class UserRepository {

    public static final String COLLECTION = "users";

    private final FirebaseFirestore db;

    public UserRepository() {
        this(FirebaseFirestore.getInstance());
    }

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public DocumentReference userDoc(@NonNull String uid) {
        return db.collection(COLLECTION).document(uid);
    }

    /** 회원가입 직후: 회원 정보 + 기본 설정 저장 */
    public void createProfile(@NonNull String uid, String email, String nickname, Callback<Void> callback) {
        Map<String, Object> data = UserSettings.defaults().toMap();
        data.put(UserProfile.UID, uid);
        data.put(UserProfile.EMAIL, email);
        data.put(UserProfile.NICKNAME, nickname);
        data.put(UserProfile.CREATED_AT, FieldValue.serverTimestamp());
        data.put(UserProfile.UPDATED_AT, FieldValue.serverTimestamp());
        userDoc(uid).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("회원 정보를 저장하지 못했습니다."));
    }

    public void loadProfile(@NonNull String uid, Callback<UserProfile> callback) {
        userDoc(uid).get()
                .addOnSuccessListener(doc -> callback.onSuccess(toProfile(uid, doc)))
                .addOnFailureListener(e -> callback.onError("회원 정보를 불러오지 못했습니다."));
    }

    /** 문서가 아직 없으면 기본 설정을 돌려줍니다. */
    public void loadSettings(@NonNull String uid, Callback<UserSettings> callback) {
        userDoc(uid).get()
                .addOnSuccessListener(doc -> callback.onSuccess(UserSettings.fromMap(doc.getData())))
                .addOnFailureListener(e -> callback.onError("설정을 불러오지 못했습니다."));
    }

    public void saveSettings(@NonNull String uid, UserSettings settings, Callback<Void> callback) {
        String problem = settings.validate();
        if (problem != null) {
            callback.onError(problem);
            return;
        }
        Map<String, Object> data = new HashMap<>(settings.toMap());
        data.put(UserProfile.UID, uid);
        data.put(UserProfile.UPDATED_AT, FieldValue.serverTimestamp());
        userDoc(uid).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("설정을 저장하지 못했습니다."));
    }

    private static UserProfile toProfile(String uid, DocumentSnapshot doc) {
        UserProfile p = new UserProfile();
        p.uid = uid;
        p.email = doc.getString(UserProfile.EMAIL);
        p.nickname = doc.getString(UserProfile.NICKNAME);
        p.createdAt = doc.getTimestamp(UserProfile.CREATED_AT);
        return p;
    }
}
