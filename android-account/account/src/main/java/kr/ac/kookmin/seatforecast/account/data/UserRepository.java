package kr.ac.kookmin.seatforecast.account.data;

import kr.ac.kookmin.seatforecast.account.Place;

import java.util.List;

import java.util.ArrayList;

import androidx.annotation.NonNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;

/**
 * Firestore users/{uid} 읽기 · 쓰기.
 * 문서 하나에 회원 정보(email, nickname, created_at)와 마이페이지 설정(settings.json 필드)을 함께 둡니다.
 * 항상 merge 로 써서 다른 화면이 같은 문서에 넣은 필드(예: FCM 토큰)를 지우지 않습니다.
 */
public class UserRepository {

    public static final String COLLECTION = "users";

    private final FirebaseFirestore db;

    /** 둘러보기(Account.isPreview) 중 설정 — Firestore 대신 앱 메모리에만 둡니다. */
    private static UserSettings previewSettings;

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
        if (Account.isPreview()) {
            UserProfile p = new UserProfile();
            p.uid = uid;
            p.email = Account.PREVIEW_EMAIL;
            p.nickname = Account.PREVIEW_NICKNAME;
            p.createdAt = Timestamp.now();
            callback.onSuccess(p);
            return;
        }
        userDoc(uid).get()
                .addOnSuccessListener(doc -> callback.onSuccess(toProfile(uid, doc)))
                .addOnFailureListener(e -> callback.onError("회원 정보를 불러오지 못했습니다."));
    }

    /** 문서가 아직 없으면 기본 설정을 돌려줍니다. */
    public void loadSettings(@NonNull String uid, Callback<UserSettings> callback) {
        if (Account.isPreview()) {
            if (previewSettings == null) previewSettings = UserSettings.defaults();
            callback.onSuccess(previewSettings);
            return;
        }
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
        if (Account.isPreview()) {
            previewSettings = settings;
            callback.onSuccess(null);
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

    // ---------- 출발지 · 도착지 모드 (A 홈 · B 대안 · E 알림이 씀) ----------

    /** 처음 열 때 쓸 도착지 — destination, 없으면 last_trip.to, 없으면 null 로 성공 (HomeHost.loadLastDestination) */
    public void loadLastDestination(@NonNull String uid, Callback<Place> callback) {
        loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                callback.onSuccess(s.lastDestination());
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** 즐겨찾는 곳 (HomeHost.loadFavorites) */
    public void loadFavorites(@NonNull String uid, Callback<List<Place>> callback) {
        loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                callback.onSuccess(new ArrayList<>(s.favorites));
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /** 즐겨찾는 곳 추가 (HomeHost.addFavorite) — 같은 곳이 있으면 그대로 성공 */
    public void addFavorite(@NonNull String uid, Place place, Callback<Void> callback) {
        loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                for (Place p : s.favorites) {
                    if (p.samePlace(place)) {
                        callback.onSuccess(null);
                        return;
                    }
                }
                s.favorites.add(new Place(place.name, place.lat, place.lng));
                saveSettings(uid, s, callback);
            }

            @Override
            public void onError(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * 마지막 검색 저장 (HomeHost.saveLastTrip) — last_trip {from, to, at(서버 시각)}, 도착지는 destination 에도.
     * 출발지가 "현재 위치"면 그때의 GPS 좌표가 들어 있음 (E 돌발 알림이 그 경로로 /incidents 를 부름).
     */
    public void saveLastTrip(@NonNull String uid, Place from, Place to, Callback<Void> callback) {
        if (from == null || to == null || !from.isValid() || !to.isValid()) {
            callback.onError("출발지 · 도착지 위치가 올바르지 않습니다.");
            return;
        }
        if (Account.isPreview()) {
            if (previewSettings == null) previewSettings = UserSettings.defaults();
            previewSettings.lastTrip = new UserSettings.LastTrip();
            previewSettings.lastTrip.from = from;
            previewSettings.lastTrip.to = to;
            previewSettings.lastTrip.at = Timestamp.now();
            previewSettings.destination = to;
            callback.onSuccess(null);
            return;
        }
        Map<String, Object> trip = UserSettings.lastTripMap(from, to);
        trip.put("at", FieldValue.serverTimestamp());
        Map<String, Object> data = new HashMap<>();
        data.put(UserSettings.LAST_TRIP, trip);
        data.put(UserSettings.DESTINATION, to.toMap());
        data.put(UserProfile.UPDATED_AT, FieldValue.serverTimestamp());
        userDoc(uid).set(data, SetOptions.merge())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError("최근 경로를 저장하지 못했습니다."));
    }
}
