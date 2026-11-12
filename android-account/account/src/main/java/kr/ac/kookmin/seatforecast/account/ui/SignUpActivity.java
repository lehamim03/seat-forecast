package kr.ac.kookmin.seatforecast.account.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseUser;

import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.AuthRepository;
import kr.ac.kookmin.seatforecast.account.data.Validators;

/** 회원가입: 닉네임 · 이메일 · 비밀번호. 성공하면 바로 로그인된 상태로 RESULT_OK. */
public class SignUpActivity extends AppCompatActivity {

    private final AuthRepository auth = new AuthRepository();

    private TextInputLayout nicknameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText nickname;
    private TextInputEditText email;
    private TextInputEditText password;
    private TextInputEditText confirm;
    private MaterialButton submit;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.account_activity_sign_up);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        nicknameLayout = findViewById(R.id.account_nickname_layout);
        emailLayout = findViewById(R.id.account_email_layout);
        passwordLayout = findViewById(R.id.account_password_layout);
        confirmLayout = findViewById(R.id.account_password_confirm_layout);
        nickname = findViewById(R.id.account_nickname);
        email = findViewById(R.id.account_email);
        password = findViewById(R.id.account_password);
        confirm = findViewById(R.id.account_password_confirm);
        submit = findViewById(R.id.account_sign_up_submit);
        progress = findViewById(R.id.account_progress);

        submit.setOnClickListener(v -> attemptSignUp());
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void attemptSignUp() {
        String n = LoginActivity.text(nickname);
        String e = LoginActivity.text(email);
        String p = LoginActivity.text(password);
        String c = LoginActivity.text(confirm);

        String nickError = Validators.nickname(n);
        String emailError = Validators.email(e);
        String passwordError = Validators.password(p);
        String confirmError = passwordError == null ? Validators.passwordConfirm(p, c) : null;
        nicknameLayout.setError(nickError);
        emailLayout.setError(emailError);
        passwordLayout.setError(passwordError);
        confirmLayout.setError(confirmError);
        if (nickError != null || emailError != null || passwordError != null || confirmError != null) return;

        setLoading(true);
        auth.signUp(e, p, n, new Callback<FirebaseUser>() {
            @Override
            public void onSuccess(FirebaseUser user) {
                setLoading(false);
                Toast.makeText(SignUpActivity.this,
                        getString(R.string.account_welcome, n.trim()), Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(submit, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        submit.setEnabled(!loading);
    }
}
