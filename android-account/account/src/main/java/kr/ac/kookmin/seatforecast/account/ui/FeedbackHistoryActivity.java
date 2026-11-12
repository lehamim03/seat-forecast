package kr.ac.kookmin.seatforecast.account.ui;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.ImageViewCompat;

import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRecord;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;

/** 마이페이지 > 탑승 피드백 기록 (Firestore users/{uid}/feedback, 최신순) */
public class FeedbackHistoryActivity extends AppCompatActivity {

    private static final int HISTORY_LIMIT = 100;

    private final FeedbackRepository feedback = new FeedbackRepository();

    private TextView summary;
    private LinearLayout list;
    private ProgressBar progress;

    private final ActivityResultLauncher<Intent> write = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> load());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!Account.isSignedIn()) {
            finish();
            return;
        }
        setContentView(R.layout.account_activity_feedback_history);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        summary = findViewById(R.id.account_history_summary);
        list = findViewById(R.id.account_feedback_list);
        progress = findViewById(R.id.account_progress);
        // 값 없이 열면 FeedbackActivity 가 평소 노선 · 정류장으로 채움
        findViewById(R.id.account_feedback_write).setOnClickListener(
                v -> write.launch(new Intent(this, FeedbackActivity.class)));
        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        feedback.loadHistory(HISTORY_LIMIT, new Callback<List<FeedbackRecord>>() {
            @Override
            public void onSuccess(List<FeedbackRecord> records) {
                progress.setVisibility(View.GONE);
                int[] stats = FeedbackRecord.monthStats(records, FeedbackRequest.today().substring(0, 7));
                summary.setText(getString(R.string.account_menu_history_sub, stats[0], stats[1]));
                list.removeAllViews();
                if (records.isEmpty()) {
                    addEmpty();
                    return;
                }
                LayoutInflater inflater = LayoutInflater.from(FeedbackHistoryActivity.this);
                for (FeedbackRecord r : records) list.addView(row(inflater, r));
            }

            @Override
            public void onError(String message) {
                progress.setVisibility(View.GONE);
                summary.setText(message);
            }
        });
    }

    private View row(LayoutInflater inflater, FeedbackRecord r) {
        View row = inflater.inflate(R.layout.account_item_feedback_row, list, false);
        int color = getColor(r.boarded ? R.color.account_boarded : R.color.account_not_boarded);

        ImageView icon = row.findViewById(R.id.account_feedback_icon);
        icon.setImageResource(r.boarded ? R.drawable.account_ic_check : R.drawable.account_ic_close);
        ImageViewCompat.setImageTintList(icon, ColorStateList.valueOf(color));

        ((TextView) row.findViewById(R.id.account_feedback_title)).setText(
                r.route + " · " + Stops.nameOf(r.stopId));
        StringBuilder sub = new StringBuilder(r.date == null ? "" : r.date);
        if (r.predictedProb != null) {
            sub.append(" · ").append(getString(R.string.account_feedback_prob_tag, Math.round(r.predictedProb * 100)));
        }
        if (!r.sent) sub.append(" · ").append(getString(R.string.account_feedback_pending_tag));
        if (r.memo != null && !r.memo.isEmpty()) sub.append(" · ").append(r.memo);
        ((TextView) row.findViewById(R.id.account_feedback_sub)).setText(sub);

        TextView result = row.findViewById(R.id.account_feedback_result);
        result.setText(r.boarded ? R.string.account_boarded : R.string.account_not_boarded);
        result.setTextColor(color);
        return row;
    }

    private void addEmpty() {
        TextView t = new TextView(this);
        t.setText(R.string.account_feedback_empty);
        t.setTextColor(getColor(R.color.account_muted));
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        t.setPadding(pad, pad, pad, pad);
        list.addView(t);
    }
}
