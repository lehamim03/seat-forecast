package kr.ac.kookmin.seatforecast.account.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
import android.widget.ProgressBar;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.AuthRepository;
import kr.ac.kookmin.seatforecast.account.data.Validators;

/**
 * ① 로그인 화면. 로그인(또는 회원가입)에 성공하면 RESULT_OK 로 닫힙니다.
 * 호출하는 쪽에서 결과를 받아 홈 화면으로 넘어가면 됩니다.
 */
public class LoginActivity extends AppCompatActivity {

    private final AuthRepository auth = new AuthRepository();

    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText email;
    private TextInputEditText password;
    private MaterialButton signIn;
    private ProgressBar progress;
    private CheckBox keepSignedIn;

    private final ActivityResultLauncher<Intent> signUp = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK) {
                    Account.setKeepSignedIn(this, keepSignedIn.isChecked());
                    finishSignedIn();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_activity_login);

        emailLayout = findViewById(R.id.account_email_layout);
        passwordLayout = findViewById(R.id.account_password_layout);
        email = findViewById(R.id.account_email);
        password = findViewById(R.id.account_password);
        signIn = findViewById(R.id.account_sign_in);
        progress = findViewById(R.id.account_progress);
        keepSignedIn = findViewById(R.id.account_keep_signed_in);

        signIn.setOnClickListener(v -> attemptSignIn());
        password.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSignIn();
                return true;
            }
            return false;
        });
        findViewById(R.id.account_go_sign_up).setOnClickListener(
                v -> signUp.launch(new Intent(this, SignUpActivity.class)));
        findViewById(R.id.account_forgot).setOnClickListener(v -> sendReset());
    }

    private void attemptSignIn() {
        String e = text(email);
        String p = text(password);
        String emailError = Validators.email(e);
        String passwordError = Validators.password(p);
        emailLayout.setError(emailError);
        passwordLayout.setError(passwordError);
        if (emailError != null || passwordError != null) return;

        setLoading(true);
        auth.signIn(e, p, new Callback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                Account.setKeepSignedIn(LoginActivity.this, keepSignedIn.isChecked());
                finishSignedIn();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(signIn, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void sendReset() {
        String e = text(email);
        String emailError = Validators.email(e);
        emailLayout.setError(emailError);
        if (emailError != null) return;
        auth.sendPasswordReset(e, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Snackbar.make(signIn, R.string.account_reset_sent, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Snackbar.make(signIn, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void finishSignedIn() {
        setResult(RESULT_OK);
        finish();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        signIn.setEnabled(!loading);
    }

    static String text(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
