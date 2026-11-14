package kr.ac.kookmin.seatforecast.account.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.AuthRepository;
import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.data.UserProfile;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRecord;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;

/**
 * ⑦ 마이페이지 (Figma '07 마이페이지'): 회원 정보, 오늘 탑승 결과(탔어요 / 못 탔어요),
 * 즐겨찾기 경로 · 출발 알림 설정 · 탑승 피드백 기록 · 계정 관리 메뉴, 주변 정류장.
 *
 * 홈 화면에서 그날 추천했던 버스로 열면 '오늘 탑승 결과' 카드가 그 버스를 묻습니다:
 * <pre>startActivity(MyPageActivity.newIntent(this, "3007", "203000162", 0.95));</pre>
 * 값 없이 열면 평소 노선 · 정류장을 묻습니다.
 * 로그아웃하면 {@link #RESULT_SIGNED_OUT} 으로 닫힙니다.
 */
public class MyPageActivity extends AppCompatActivity {

    public static final int RESULT_SIGNED_OUT = RESULT_FIRST_USER + 1;

    private static final int HISTORY_LIMIT = 100;

    public static Intent newIntent(Context context, String route, String stopId, @Nullable Double predictedProb) {
        Intent i = new Intent(context, MyPageActivity.class);
        i.putExtra(FeedbackActivity.EXTRA_ROUTE, route);
        i.putExtra(FeedbackActivity.EXTRA_STOP_ID, stopId);
        if (predictedProb != null) i.putExtra(FeedbackActivity.EXTRA_PREDICTED_PROB, predictedProb.doubleValue());
        return i;
    }

    private final UserRepository users = new UserRepository();
    private final FeedbackRepository feedback = new FeedbackRepository();

    private String uid;
    private UserSettings settings;
    private List<FeedbackRecord> history;

    private TextView nicknameView;
    private TextView emailView;
    private TextView joinedView;
    private View feedbackCard;
    private TextView feedbackQuestion;
    private TextView feedbackDesc;
    private View boardedButton;
    private View notBoardedButton;
    private ProgressBar progress;
    private View rowFavorite;
    private View rowNotify;
    private View rowHistory;
    private View rowAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = Account.currentUid();
        if (uid == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.account_activity_my_page);

        nicknameView = findViewById(R.id.account_nickname);
        emailView = findViewById(R.id.account_email);
        joinedView = findViewById(R.id.account_joined);
        feedbackCard = findViewById(R.id.account_feedback_card);
        feedbackQuestion = findViewById(R.id.account_feedback_question);
        feedbackDesc = findViewById(R.id.account_feedback_desc);
        boardedButton = findViewById(R.id.account_boarded);
        notBoardedButton = findViewById(R.id.account_not_boarded);
        progress = findViewById(R.id.account_progress);
        emailView.setText(Account.currentEmail());

        rowFavorite = setUpRow(R.id.account_row_favorite, R.drawable.account_ic_bookmark, R.string.account_menu_favorite,
                v -> startActivity(new Intent(this, RouteSettingsActivity.class)));
        rowNotify = setUpRow(R.id.account_row_notify, R.drawable.account_ic_bell, R.string.account_menu_notify,
                v -> startActivity(new Intent(this, NotifySettingsActivity.class)));
        rowHistory = setUpRow(R.id.account_row_history, R.drawable.account_ic_chat, R.string.account_menu_history,
                v -> startActivity(new Intent(this, FeedbackHistoryActivity.class)));
        rowAccount = setUpRow(R.id.account_row_account, R.drawable.account_ic_person, R.string.account_menu_account,
                v -> showAccountMenu());
        setRowSub(rowAccount, getString(R.string.account_menu_account_sub));
        findViewById(R.id.account_profile).setOnClickListener(v -> showAccountMenu());

        fillStops();
        boardedButton.setOnClickListener(v -> submitToday(true));
        notBoardedButton.setOnClickListener(v -> submitToday(false));

        loadProfile();
        // 서버에 못 보낸 피드백이 있으면 다시 보냄
        feedback.retryPending(new Callback<Integer>() {
            @Override
            public void onSuccess(Integer sent) {
                if (sent > 0) Snackbar.make(rowHistory, getString(R.string.account_retried, sent),
                        Snackbar.LENGTH_SHORT).show();
                loadHistory();
            }

            @Override
            public void onError(String message) {
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (uid == null) return;
        loadSettings();   // 하위 화면에서 바꾼 설정 반영
        loadHistory();
    }

    // ---------- 화면 구성 ----------

    private View setUpRow(int id, int icon, int title, View.OnClickListener click) {
        View row = findViewById(id);
        ((ImageView) row.findViewById(R.id.account_row_icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.account_row_title)).setText(title);
        row.setOnClickListener(click);
        return row;
    }

    private static void setRowSub(View row, String text) {
        ((TextView) row.findViewById(R.id.account_row_sub)).setText(text);
    }

    private void fillStops() {
        LinearLayout list = findViewById(R.id.account_stop_list);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Stops.Stop stop : Stops.ALL) {
            View row = inflater.inflate(R.layout.account_item_stop_row, list, false);
            ((TextView) row.findViewById(R.id.account_stop_name)).setText(stop.name);
            ((TextView) row.findViewById(R.id.account_stop_sub)).setText(
                    stop.mobileNo + " · " + TextUtils.join(" · ", stop.routes));
            ((TextView) row.findViewById(R.id.account_stop_walk)).setText(
                    getString(R.string.account_walk_format, stop.walkMin));
            list.addView(row);
        }
    }

    private void bindSettings(UserSettings s) {
        setRowSub(rowFavorite, getString(R.string.account_menu_favorite_sub,
                Stops.nameOf(s.homeStopId), s.destinationName, s.arriveBy));
        String incident = getString(s.notifyIncident ? R.string.account_on : R.string.account_off);
        setRowSub(rowNotify, s.notifyDeparture
                ? getString(R.string.account_menu_notify_on, s.minutesBefore, incident)
                : getString(R.string.account_menu_notify_off, incident));
    }

    private void bindHistory(List<FeedbackRecord> records) {
        int[] stats = FeedbackRecord.monthStats(records, FeedbackRequest.today().substring(0, 7));
        setRowSub(rowHistory, getString(R.string.account_menu_history_sub, stats[0], stats[1]));
    }

    /** '오늘 탑승 결과' 카드: 묻는 버스 = 홈 화면이 넘긴 값, 없으면 평소 노선 · 정류장. 오늘 이미 남겼으면 숨김. */
    private void bindTodayCard() {
        if (settings == null || history == null) return;
        String route = todayRoute();
        if (FeedbackRecord.hasRecord(history, FeedbackRequest.today(), route)) {
            feedbackCard.setVisibility(View.GONE);
            return;
        }
        feedbackQuestion.setText(getString(R.string.account_today_question, route));
        feedbackDesc.setText(getString(R.string.account_today_desc, Stops.nameOf(todayStopId()), route));
        feedbackCard.setVisibility(View.VISIBLE);
    }

    private String todayRoute() {
        String r = getIntent().getStringExtra(FeedbackActivity.EXTRA_ROUTE);
        return r != null ? r : settings.myRoute;
    }

    private String todayStopId() {
        String s = getIntent().getStringExtra(FeedbackActivity.EXTRA_STOP_ID);
        return s != null ? s : settings.homeStopId;
    }

    @Nullable
    private Double todayProb() {
        Intent i = getIntent();
        return i.hasExtra(FeedbackActivity.EXTRA_PREDICTED_PROB)
                ? i.getDoubleExtra(FeedbackActivity.EXTRA_PREDICTED_PROB, 0) : null;
    }

    // ---------- 데이터 ----------

    private void loadProfile() {
        users.loadProfile(uid, new Callback<UserProfile>() {
            @Override
            public void onSuccess(UserProfile p) {
                if (p.nickname != null) nicknameView.setText(getString(R.string.account_nickname_format, p.nickname));
                if (p.email != null) emailView.setText(p.email);
                if (p.createdAt != null) {
                    SimpleDateFormat f = new SimpleDateFormat("yyyy.MM.dd", Locale.KOREA);
                    f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
                    joinedView.setText(getString(R.string.account_joined, f.format(p.createdAt.toDate())));
                    joinedView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String message) {
                Snackbar.make(rowHistory, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void loadSettings() {
        users.loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                settings = s;
                bindSettings(s);
                bindTodayCard();
            }

            @Override
            public void onError(String message) {
                Snackbar.make(rowHistory, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void loadHistory() {
        feedback.loadHistory(HISTORY_LIMIT, new Callback<List<FeedbackRecord>>() {
            @Override
            public void onSuccess(List<FeedbackRecord> records) {
                history = records;
                bindHistory(records);
                bindTodayCard();
            }

            @Override
            public void onError(String message) {
                setRowSub(rowHistory, message);
            }
        });
    }

    private void submitToday(boolean boarded) {
        FeedbackRequest request = new FeedbackRequest(uid, FeedbackRequest.today(),
                todayRoute(), todayStopId(), boarded, todayProb(), "");
        setLoading(true);
        feedback.submit(request, new Callback<FeedbackRepository.SubmitResult>() {
            @Override
            public void onSuccess(FeedbackRepository.SubmitResult result) {
                setLoading(false);
                Snackbar.make(rowHistory, result.sentToServer
                        ? R.string.account_feedback_thanks : R.string.account_feedback_pending,
                        Snackbar.LENGTH_LONG).show();
                loadHistory();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(rowHistory, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    // ---------- 계정 관리 ----------

    private void showAccountMenu() {
        String[] items = {getString(R.string.account_reset_password), getString(R.string.account_sign_out)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.account_menu_account)
                .setItems(items, (d, which) -> {
                    if (which == 0) sendPasswordReset();
                    else confirmSignOut();
                })
                .show();
    }

    private void sendPasswordReset() {
        String email = Account.currentEmail();
        if (email == null) return;
        new AuthRepository().sendPasswordReset(email, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                Snackbar.make(rowHistory, R.string.account_reset_sent, Snackbar.LENGTH_LONG).show();
            }

            @Override
            public void onError(String message) {
                Snackbar.make(rowHistory, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void confirmSignOut() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.account_sign_out_confirm)
                .setPositiveButton(R.string.account_sign_out, (d, w) -> {
                    Account.signOut();
                    setResult(RESULT_SIGNED_OUT);
                    finish();
                })
                .setNegativeButton(R.string.account_cancel, null)
                .show();
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        boardedButton.setEnabled(!loading);
        notBoardedButton.setEnabled(!loading);
    }
}
