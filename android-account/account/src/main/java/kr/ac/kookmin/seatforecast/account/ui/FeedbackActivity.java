package kr.ac.kookmin.seatforecast.account.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;


import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;

/**
 * 탑승 피드백 "탔어요 / 못 탔어요" — 어떤 노선 · 정류장이든 (출발지 · 도착지 모드).
 * 홈 · 실시간 대안 화면에서 그때 보여준 노선 · 정류장 · 확률을 넘겨 엽니다:
 * <pre>startActivity(FeedbackActivity.newIntent(this, "9401", "206000299", "이매촌한신.서현역.AK프라자", 0.98));</pre>
 * 노선 · 정류장 없이 열면 안내 후 닫습니다 (평소 정류장 · 노선 기본값 없음).
 */
public class FeedbackActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE = "kr.ac.kookmin.seatforecast.account.ROUTE";
    public static final String EXTRA_STOP_ID = "kr.ac.kookmin.seatforecast.account.STOP_ID";
    public static final String EXTRA_STOP_NAME = "kr.ac.kookmin.seatforecast.account.STOP_NAME";
    public static final String EXTRA_PREDICTED_PROB = "kr.ac.kookmin.seatforecast.account.PREDICTED_PROB";

    /** predictedProb 는 0.0~1.0, 모르면 null */
    public static Intent newIntent(Context context, String route, String stopId, @Nullable Double predictedProb) {
        return newIntent(context, route, stopId, null, predictedProb);
    }

    /** @param stopName 화면 · 기록에 보여 줄 정류장 이름 (서버 API 이름 그대로), 모르면 null */
    public static Intent newIntent(Context context, String route, String stopId, @Nullable String stopName,
                                   @Nullable Double predictedProb) {
        Intent i = new Intent(context, FeedbackActivity.class);
        i.putExtra(EXTRA_ROUTE, route);
        i.putExtra(EXTRA_STOP_ID, stopId);
        if (stopName != null) i.putExtra(EXTRA_STOP_NAME, stopName);
        if (predictedProb != null) i.putExtra(EXTRA_PREDICTED_PROB, predictedProb.doubleValue());
        return i;
    }

    private final FeedbackRepository feedback = new FeedbackRepository();

    private TextView question;
    private TextView probView;
    private TextInputEditText memo;
    private ProgressBar progress;
    private View boardedButton;
    private View notBoardedButton;

    private Double predictedProb;
    private String route;
    private String stopId;
    private String stopName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Account.isSignedIn()) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        Intent intent = getIntent();
        route = intent.getStringExtra(EXTRA_ROUTE);
        stopId = intent.getStringExtra(EXTRA_STOP_ID);
        stopName = intent.getStringExtra(EXTRA_STOP_NAME);
        if (route == null || route.isEmpty() || stopId == null || stopId.isEmpty()) {
            Toast.makeText(this, R.string.account_feedback_need_option, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        if (stopName == null || stopName.isEmpty()) stopName = Stops.nameOf(stopId);
        setContentView(R.layout.account_activity_feedback);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        question = findViewById(R.id.account_question);
        probView = findViewById(R.id.account_prob);
        memo = findViewById(R.id.account_memo);
        progress = findViewById(R.id.account_progress);
        boardedButton = findViewById(R.id.account_boarded);
        notBoardedButton = findViewById(R.id.account_not_boarded);

        // 넘겨받은 노선 · 정류장을 그대로 보여 줌 (고르는 목록 대신)
        Spinner stopSpinner = findViewById(R.id.account_stop);
        Spinner routeSpinner = findViewById(R.id.account_route);
        stopSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Collections.singletonList(stopName)));
        routeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                java.util.Collections.singletonList(route)));
        stopSpinner.setEnabled(false);
        routeSpinner.setEnabled(false);
        question.setText(getString(R.string.account_feedback_question, route));

        if (intent.hasExtra(EXTRA_PREDICTED_PROB)) {
            predictedProb = intent.getDoubleExtra(EXTRA_PREDICTED_PROB, 0);
            probView.setText(getString(R.string.account_feedback_prob, Math.round(predictedProb * 100)));
            probView.setVisibility(View.VISIBLE);
        }

        boardedButton.setOnClickListener(v -> submit(true));
        notBoardedButton.setOnClickListener(v -> submit(false));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void submit(boolean boarded) {
        String memoText = memo.getText() == null ? "" : memo.getText().toString().trim();
        FeedbackRequest request = new FeedbackRequest(Account.currentUid(), FeedbackRequest.today(),
                route, stopId, boarded, predictedProb, memoText);
        request.stopName = stopName;

        setLoading(true);
        feedback.submit(request, new Callback<FeedbackRepository.SubmitResult>() {
            @Override
            public void onSuccess(FeedbackRepository.SubmitResult result) {
                setLoading(false);
                Toast.makeText(FeedbackActivity.this, result.sentToServer
                        ? R.string.account_feedback_thanks : R.string.account_feedback_pending,
                        Toast.LENGTH_LONG).show();
                setResult(RESULT_OK);
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(boardedButton, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        boardedButton.setEnabled(!loading);
        notBoardedButton.setEnabled(!loading);
    }
}
