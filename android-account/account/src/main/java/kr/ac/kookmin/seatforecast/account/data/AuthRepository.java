package kr.ac.kookmin.seatforecast.account.data;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.UserProfileChangeRequest;

import kr.ac.kookmin.seatforecast.account.Callback;

/** Firebase Auth 이메일 · 비밀번호 로그인과 회원가입 */
public class AuthRepository {

    private final FirebaseAuth auth;
    private final UserRepository users;

    public AuthRepository() {
        this(FirebaseAuth.getInstance(), new UserRepository());
    }

    public AuthRepository(FirebaseAuth auth, UserRepository users) {
        this.auth = auth;
        this.users = users;
    }

    public void signIn(String email, String password, Callback<FirebaseUser> callback) {
        auth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(r -> callback.onSuccess(r.getUser()))
                .addOnFailureListener(e -> callback.onError(message(e)));
    }

    /** 계정을 만들고 Firestore users/{uid} 에 회원 정보 · 기본 설정을 저장합니다. */
    public void signUp(String email, String password, String nickname, Callback<FirebaseUser> callback) {
        String trimmedEmail = email.trim();
        String trimmedNick = nickname.trim();
        auth.createUserWithEmailAndPassword(trimmedEmail, password)
                .addOnSuccessListener(r -> {
                    FirebaseUser user = r.getUser();
                    if (user == null) {
                        callback.onError("회원가입에 실패했습니다.");
                        return;
                    }
                    user.updateProfile(new UserProfileChangeRequest.Builder()
                            .setDisplayName(trimmedNick).build());
                    users.createProfile(user.getUid(), trimmedEmail, trimmedNick, new Callback<Void>() {
                        @Override
                        public void onSuccess(Void result) {
                            callback.onSuccess(user);
                        }

                        @Override
                        public void onError(String message) {
                            callback.onError(message);
                        }
                    });
                })
                .addOnFailureListener(e -> callback.onError(message(e)));
    }

    public void sendPasswordReset(String email, Callback<Void> callback) {
        auth.sendPasswordResetEmail(email.trim())
                .addOnSuccessListener(v -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onError(message(e)));
    }

    /** Firebase 오류 → 화면 문구 */
    static String message(Exception e) {
        if (e instanceof FirebaseAuthWeakPasswordException) return "비밀번호가 너무 쉽습니다. 6자 이상으로 정하세요.";
        if (e instanceof FirebaseAuthUserCollisionException) return "이미 가입된 이메일입니다.";
        if (e instanceof FirebaseAuthInvalidUserException) return "가입되지 않은 이메일입니다.";
        if (e instanceof FirebaseAuthInvalidCredentialsException) return "이메일 또는 비밀번호가 올바르지 않습니다.";
        if (e instanceof FirebaseNetworkException) return "네트워크 연결을 확인하세요.";
        if (e instanceof FirebaseAuthException) return "로그인 오류: " + ((FirebaseAuthException) e).getErrorCode();
        return "로그인 중 오류가 났습니다. 잠시 뒤 다시 시도하세요.";
    }
}
