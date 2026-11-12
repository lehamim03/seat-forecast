package kr.ac.kookmin.seatforecast.account.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;

/**
 * 탑승 피드백 "탔어요 / 못 탔어요".
 * 홈 · 실시간 대안 화면에서는 그때 보여준 노선 · 정류장 · 확률을 넘겨 엽니다:
 * <pre>startActivity(FeedbackActivity.newIntent(this, "3007", "203000161", 1.0));</pre>
 * 값 없이 열면 마이페이지의 평소 정류장 · 노선으로 채웁니다.
 */
public class FeedbackActivity extends AppCompatActivity {

    public static final String EXTRA_ROUTE = "kr.ac.kookmin.seatforecast.account.ROUTE";
    public static final String EXTRA_STOP_ID = "kr.ac.kookmin.seatforecast.account.STOP_ID";
    public static final String EXTRA_PREDICTED_PROB = "kr.ac.kookmin.seatforecast.account.PREDICTED_PROB";

    /** predictedProb 는 0.0~1.0, 모르면 null */
    public static Intent newIntent(Context context, String route, String stopId, @Nullable Double predictedProb) {
        Intent i = new Intent(context, FeedbackActivity.class);
        i.putExtra(EXTRA_ROUTE, route);
        i.putExtra(EXTRA_STOP_ID, stopId);
        if (predictedProb != null) i.putExtra(EXTRA_PREDICTED_PROB, predictedProb.doubleValue());
        return i;
    }

    private final FeedbackRepository feedback = new FeedbackRepository();

    private Spinner stopSpinner;
    private Spinner routeSpinner;
    private TextView question;
    private TextView probView;
    private TextInputEditText memo;
    private ProgressBar progress;
    private View boardedButton;
    private View notBoardedButton;

    private Double predictedProb;
    private String pendingRoute;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Account.isSignedIn()) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.account_activity_feedback);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        stopSpinner = findViewById(R.id.account_stop);
        routeSpinner = findViewById(R.id.account_route);
        question = findViewById(R.id.account_question);
        probView = findViewById(R.id.account_prob);
        memo = findViewById(R.id.account_memo);
        progress = findViewById(R.id.account_progress);
        boardedButton = findViewById(R.id.account_boarded);
        notBoardedButton = findViewById(R.id.account_not_boarded);

        ArrayAdapter<Stops.Stop> stopAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, Stops.ALL);
        stopSpinner.setAdapter(stopAdapter);
        stopSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fillRoutes(Stops.ALL.get(position).id);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        routeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                updateQuestion();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        Intent intent = getIntent();
        if (intent.hasExtra(EXTRA_PREDICTED_PROB)) {
            predictedProb = intent.getDoubleExtra(EXTRA_PREDICTED_PROB, 0);
            probView.setText(getString(R.string.account_feedback_prob, Math.round(predictedProb * 100)));
            probView.setVisibility(View.VISIBLE);
        }
        String route = intent.getStringExtra(EXTRA_ROUTE);
        String stopId = intent.getStringExtra(EXTRA_STOP_ID);
        if (route != null && stopId != null) {
            select(stopId, route);
        } else {
            // 값 없이 열면 평소 정류장 · 노선으로
            select(Stops.ALL.get(0).id, Stops.ROUTES.get(0));
            new UserRepository().loadSettings(Account.currentUid(), new Callback<UserSettings>() {
                @Override
                public void onSuccess(UserSettings s) {
                    select(s.homeStopId, s.myRoute);
                }

                @Override
                public void onError(String message) {
                }
            });
        }

        boardedButton.setOnClickListener(v -> submit(true));
        notBoardedButton.setOnClickListener(v -> submit(false));
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void select(String stopId, String route) {
        pendingRoute = route;
        int index = 0;
        for (int i = 0; i < Stops.ALL.size(); i++) {
            if (Stops.ALL.get(i).id.equals(stopId)) index = i;
        }
        if (stopSpinner.getSelectedItemPosition() == index) {
            fillRoutes(stopId);   // 같은 위치면 리스너가 다시 불리지 않음
        } else {
            stopSpinner.setSelection(index);
        }
    }

    private void fillRoutes(String stopId) {
        List<String> routes = Stops.routesAt(stopId);
        routeSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, routes));
        if (pendingRoute != null && routes.contains(pendingRoute)) {
            routeSpinner.setSelection(routes.indexOf(pendingRoute));
        }
        pendingRoute = null;
        updateQuestion();
    }

    private String selectedRoute() {
        Object r = routeSpinner.getSelectedItem();
        return r == null ? null : r.toString();
    }

    private void updateQuestion() {
        String route = selectedRoute();
        question.setText(getString(R.string.account_feedback_question, route == null ? "" : route));
    }

    private void submit(boolean boarded) {
        Stops.Stop stop = (Stops.Stop) stopSpinner.getSelectedItem();
        String memoText = memo.getText() == null ? "" : memo.getText().toString().trim();
        FeedbackRequest request = new FeedbackRequest(Account.currentUid(), FeedbackRequest.today(),
                selectedRoute(), stop == null ? null : stop.id, boarded, predictedProb, memoText);

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
