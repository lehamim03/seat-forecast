package kr.ac.kookmin.seatforecast.accountdemo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.ui.FeedbackActivity;
import kr.ac.kookmin.seatforecast.account.ui.LoginActivity;
import kr.ac.kookmin.seatforecast.account.ui.MyPageActivity;

/** 팀 앱의 홈 화면 자리. account 모듈을 어떻게 부르는지 보여주는 예시입니다. */
public class MainActivity extends AppCompatActivity {

    private TextView status;

    private final ActivityResultLauncher<Intent> login = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (!Account.isSignedIn()) finish();   // 로그인 안 하고 뒤로 가면 앱 종료
                else refresh();
            });

    private final ActivityResultLauncher<Intent> myPage = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == MyPageActivity.RESULT_SIGNED_OUT) requireLogin();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (24 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        status = new TextView(this);
        status.setTextSize(16);
        root.addView(status);

        Button openMyPage = new Button(this);
        openMyPage.setText("마이페이지");
        openMyPage.setOnClickListener(v -> myPage.launch(new Intent(this, MyPageActivity.class)));
        root.addView(openMyPage);

        // 홈 화면에서 그날 1순위로 추천한 버스를 넘겨 마이페이지를 여는 예 → '오늘 3007 탑승 결과는?' 카드
        Button myPageWithToday = new Button(this);
        myPageWithToday.setText("마이페이지 (오늘 3007 추천 후)");
        myPageWithToday.setOnClickListener(v -> myPage.launch(
                MyPageActivity.newIntent(this, "3007", "203000162", 0.95)));
        root.addView(myPageWithToday);

        // 홈 화면 추천 카드에서 부르는 예: docs/examples/feedback.json 과 같은 값
        Button feedback = new Button(this);
        feedback.setText("오늘 3007 탑승 결과 남기기 (홈 화면 예시)");
        feedback.setOnClickListener(v -> startActivity(
                FeedbackActivity.newIntent(this, "3007", "203000161", 1.0)));
        root.addView(feedback);

        setContentView(root);

        if (!Account.isSignedIn()) requireLogin();
        else refresh();
    }

    private void requireLogin() {
        login.launch(new Intent(this, LoginActivity.class));
    }

    private void refresh() {
        status.setText("로그인: " + Account.currentEmail()
                + (BuildConfig.USE_FIREBASE_EMULATOR ? "\n(로컬 Firebase 에뮬레이터)" : ""));
    }
}
