package kr.ac.kookmin.seatforecast.alternatives.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.ac.kookmin.seatforecast.alternatives.Alternatives;
import kr.ac.kookmin.seatforecast.alternatives.AlternativesHost;
import kr.ac.kookmin.seatforecast.alternatives.Callback;
import kr.ac.kookmin.seatforecast.alternatives.R;
import kr.ac.kookmin.seatforecast.alternatives.UsualTrip;
import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;
import kr.ac.kookmin.seatforecast.alternatives.model.Subway;

/**
 * ③ 실시간 대안 — 평소 버스가 만석일 때 기다리기 · 걸어가기 · 다른 버스 비교.
 * 순서: 로그인 확인 → 평소 정류장 · 노선(인자로 받았으면 그 값) → ID 토큰 → {@link AlternativesSource#load} (백그라운드) → 화면.
 *
 * <p>12주차 1분 자동 갱신: 화면이 보이는 동안({@link #onStart} ~ {@link #onStop})
 * {@link Alternatives#refreshIntervalMs()} 마다 다시 불러옵니다. 이미 화면이 있으면 덮지 않고 조용히 바꾸며,
 * 실패하면 이전 정보를 그대로 두고 '갱신하지 못했어요' 를 표시합니다. 다른 화면에 갔다 오면 간격이 지났을 때 바로 갱신합니다.
 */
public class AlternativesFragment extends Fragment {

    static final String ARG_HOME_STOP = "home_stop";
    static final String ARG_ROUTE = "route";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::autoRefresh;
    /** 새 요청이 시작되면 이전 요청 결과는 버림 */
    private int requestId;
    private boolean loaded;
    private boolean started;
    /** 마지막으로 불러오기를 끝낸 때 (성공 · 실패 모두, SystemClock.elapsedRealtime) — 다음 자동 갱신 기준 */
    private long lastAttemptAt;
    /** 마지막으로 화면을 바꾼 시각 (벽시계) — '07:41:05 갱신' 표시용 */
    private long lastUpdatedWall;

    private View content;
    private TextView header;
    private TextView updated;
    private TextView refresh;
    private View banner;
    private TextView status;
    private TextView bannerProb;
    private TextView bannerTitle;
    private TextView bannerSub;
    private TextView advice;
    private TextView summary;
    private TextView sectionTitle;
    private LinearLayout cards;
    private View subway;
    private TextView subwayLine;
    private TextView subwaySummary;
    private View state;
    private View progress;
    private TextView stateMessage;
    private MaterialButton stateAction;

    /** 평소 정류장 · 노선 대신 이 값으로 (예: E 돌발 알림 푸시의 data.route). 둘 다 null 이면 평소 값. */
    public static AlternativesFragment newInstance(@Nullable String homeStopId, @Nullable String route) {
        AlternativesFragment f = new AlternativesFragment();
        Bundle args = new Bundle();
        args.putString(ARG_HOME_STOP, homeStopId);
        args.putString(ARG_ROUTE, route);
        f.setArguments(args);
        return f;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.alt_fragment_alternatives, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        content = v.findViewById(R.id.alt_content);
        header = v.findViewById(R.id.alt_header);
        updated = v.findViewById(R.id.alt_updated);
        refresh = v.findViewById(R.id.alt_refresh);
        banner = v.findViewById(R.id.alt_banner);
        status = v.findViewById(R.id.alt_status);
        bannerProb = v.findViewById(R.id.alt_banner_prob);
        bannerTitle = v.findViewById(R.id.alt_banner_title);
        bannerSub = v.findViewById(R.id.alt_banner_sub);
        advice = v.findViewById(R.id.alt_advice);
        summary = v.findViewById(R.id.alt_summary);
        sectionTitle = v.findViewById(R.id.alt_section_title);
        cards = v.findViewById(R.id.alt_cards);
        subway = v.findViewById(R.id.alt_subway);
        subwayLine = v.findViewById(R.id.alt_subway_line);
        subwaySummary = v.findViewById(R.id.alt_subway_summary);
        state = v.findViewById(R.id.alt_state);
        progress = v.findViewById(R.id.alt_progress);
        stateMessage = v.findViewById(R.id.alt_state_message);
        stateAction = v.findViewById(R.id.alt_state_action);
        refresh.setOnClickListener(b -> reload());
        loaded = false;
        lastAttemptAt = 0;
    }

    @Override
    public void onStart() {
        super.onStart();
        started = true;
        // 처음 열 때, 그리고 로그인 화면에서 돌아왔을 때
        if (!loaded) reload();
        else scheduleNext();   // 다른 화면에 갔다 온 동안 1분이 지났으면 바로 갱신
    }

    @Override
    public void onStop() {
        started = false;
        main.removeCallbacks(tick);   // 안 보이는 동안은 갱신하지 않음
        super.onStop();
    }

    @Override
    public void onDestroyView() {
        requestId++;   // 진행 중인 요청 결과 버림
        main.removeCallbacks(tick);
        super.onDestroyView();
    }

    /** 대안을 다시 불러옵니다 (새로고침 · 자동 갱신). 화면이 있으면 덮지 않고 조용히 바꿉니다. */
    public void reload() {
        if (getView() == null) return;
        reload(content.getVisibility() == View.VISIBLE);
    }

    private void reload(boolean silent) {
        final int id = ++requestId;
        main.removeCallbacks(tick);
        final AlternativesHost host = Alternatives.host();
        if (!host.isSignedIn()) {
            showSignedOut();   // 로그인 전에는 자동 갱신하지 않음
            return;
        }
        if (silent) {
            updated.setText(R.string.alt_refreshing);
            updated.setTextColor(color(R.color.alt_muted));
            refresh.setEnabled(false);
        } else {
            showLoading();
        }
        Bundle args = getArguments();
        String stopArg = args == null ? null : args.getString(ARG_HOME_STOP);
        String routeArg = args == null ? null : args.getString(ARG_ROUTE);
        host.loadUsualTrip(new Callback<UsualTrip>() {
            @Override
            public void onSuccess(UsualTrip usual) {
                if (stale(id)) return;
                UsualTrip trip = new UsualTrip(stopArg != null ? stopArg : usual.homeStopId,
                        routeArg != null ? routeArg : usual.route);
                host.getIdToken(new Callback<String>() {
                    @Override
                    public void onSuccess(String token) {
                        if (!stale(id)) fetch(id, trip, token, silent);
                    }

                    @Override
                    public void onError(String message) {
                        if (!stale(id)) failed(message, silent);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!stale(id)) failed(message, silent);
            }
        });
    }

    private boolean stale(int id) {
        return id != requestId || getView() == null;
    }

    private void fetch(int id, UsualTrip trip, @Nullable String token, boolean silent) {
        final AlternativesSource source = Alternatives.source();
        EXECUTOR.execute(() -> {
            AlternativesResult result = null;
            String error = null;
            try {
                result = source.load(trip.homeStopId, trip.route, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;   // 아래에서 기본 문구
            }
            final AlternativesResult r = result;
            final String message = error;
            main.post(() -> {
                if (stale(id)) return;
                if (r != null) succeeded(r);
                else failed(message != null ? message : getString(R.string.alt_error_default), silent);
            });
        });
    }

    private void succeeded(AlternativesResult r) {
        loaded = true;
        lastAttemptAt = SystemClock.elapsedRealtime();
        lastUpdatedWall = System.currentTimeMillis();
        show(r);
        bindUpdated(false);
        scheduleNext();
    }

    /** 이미 화면이 있으면 이전 정보를 두고 '갱신하지 못했어요', 처음이면 오류 화면 */
    private void failed(String message, boolean silent) {
        lastAttemptAt = SystemClock.elapsedRealtime();
        if (silent && content.getVisibility() == View.VISIBLE) bindUpdated(true);
        else showError(message);
        scheduleNext();   // 오류여도 1분 뒤 다시 시도
    }

    private void autoRefresh() {
        if (!started || getView() == null) return;
        reload();
    }

    private void scheduleNext() {
        main.removeCallbacks(tick);
        if (!started) return;
        long delay = AltFormat.nextRefreshDelay(lastAttemptAt, SystemClock.elapsedRealtime(),
                Alternatives.refreshIntervalMs());
        if (delay >= 0) main.postDelayed(tick, delay);
    }

    /** '07:41:05 갱신 · 1분마다 자동 갱신' / 실패면 '갱신하지 못했어요 · 07:41:05 정보예요' (빨강) */
    private void bindUpdated(boolean failed) {
        refresh.setEnabled(true);
        String time = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date(lastUpdatedWall));
        if (failed) {
            updated.setText(getString(R.string.alt_stale, time));
            updated.setTextColor(color(R.color.alt_low));
        } else {
            updated.setText(getString(R.string.alt_updated, time, autoText()));
            updated.setTextColor(color(R.color.alt_muted));
        }
    }

    private String autoText() {
        long ms = Alternatives.refreshIntervalMs();
        if (ms <= 0) return getString(R.string.alt_auto_off);
        if (ms % 60_000L == 0) return getString(R.string.alt_auto_every_min, (int) (ms / 60_000L));
        return getString(R.string.alt_auto_every_sec, (int) Math.max(1, ms / 1000L));
    }

    private void show(AlternativesResult r) {
        List<Option> options = r.options();
        if (options.isEmpty()) {
            showState(false, getString(R.string.alt_empty), getString(R.string.alt_retry), v -> reload(false));
            return;
        }

        String stopName = r.homeStop == null ? "" : r.homeStop.name;
        String route = r.myRoute == null ? "" : r.myRoute;
        String time = AltFormat.time(r.generatedAt);
        header.setText(time == null ? getString(R.string.alt_header_no_time, stopName, route)
                : getString(R.string.alt_header, stopName, route, time));

        bindBanner(AltFormat.banner(r));
        setTextOrGone(summary, r.recommendation == null ? null : r.recommendation.message);

        cards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Option o : options) {
            boolean recommended = AltFormat.isRecommended(o, r);
            cards.addView(OptionCard.create(inflater, cards, o, options, recommended,
                    v -> Alternatives.host().onOptionClicked(requireContext(), o)));
        }
        bindSubway(r.subway);

        state.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    /** 맨 위 평소 버스 상태 (여유 · 만석 위험 · 만석 · 우회 · 정보 없음) */
    private void bindBanner(AltFormat.Banner b) {
        int statusText;
        int adviceText;
        switch (b.status) {
            case RELAXED:
                statusText = R.string.alt_status_relaxed;
                adviceText = R.string.alt_advice_relaxed;
                break;
            case RISK:
                statusText = R.string.alt_status_risk;
                adviceText = R.string.alt_advice_risk;
                break;
            case FULL:
                statusText = R.string.alt_status_full;
                adviceText = R.string.alt_advice_full;
                break;
            case SKIPPED:
                statusText = R.string.alt_status_skipped;
                adviceText = R.string.alt_advice_skipped;
                break;
            default:
                statusText = R.string.alt_status_unknown;
                adviceText = R.string.alt_advice_unknown;
        }
        int c = color(AltStyle.color(b.status));
        banner.setBackgroundResource(AltStyle.banner(b.status));
        status.setText(statusText);
        status.setBackgroundResource(AltStyle.pill(b.status));
        status.setTextColor(c);
        advice.setText(adviceText);
        advice.setTextColor(c);
        sectionTitle.setText(b.status == AltFormat.Status.RELAXED ? R.string.alt_section_others : R.string.alt_section_try);

        String route = b.route == null ? "" : b.route;
        Bus bus = b.bus;
        boolean known = b.status != AltFormat.Status.UNKNOWN;
        if (bus != null) {
            bannerTitle.setText(getString(R.string.alt_banner_title, route, OptionCard.eta(requireContext(), bus)));
            bannerSub.setText(getString(R.string.alt_banner_sub, OptionCard.seats(requireContext(), bus),
                    AltFormat.percent(b.prob)));
            bannerSub.setVisibility(View.VISIBLE);
        } else {
            bannerTitle.setText(getString(R.string.alt_banner_title_none, route));
            bannerSub.setVisibility(View.GONE);
        }
        bannerProb.setVisibility(known && b.status != AltFormat.Status.SKIPPED ? View.VISIBLE : View.GONE);
        bannerProb.setText(AltFormat.percent(b.prob));
        bannerProb.setBackgroundResource(AltStyle.pill(b.status));
        bannerProb.setTextColor(c);
    }

    private void bindSubway(@Nullable Subway s) {
        if (s == null || s.arriveAt == null) {
            subway.setVisibility(View.GONE);
            return;
        }
        subwayLine.setText(getString(R.string.alt_subway_line, s.arriveAt, s.transfers, s.walkMin));
        setTextOrGone(subwaySummary, s.summary);
        subway.setVisibility(View.VISIBLE);
    }

    /** 조회 중 (10주차 2차) */
    private void showLoading() {
        showState(true, getString(R.string.alt_loading), null, null);
    }

    /** 오류 — 다시 시도 (10주차 2차) */
    private void showError(String message) {
        showState(false, message, getString(R.string.alt_retry), v -> reload(false));
    }

    private void showSignedOut() {
        showState(false, getString(R.string.alt_signed_out), getString(R.string.alt_sign_in),
                v -> Alternatives.host().requestSignIn(requireContext()));
    }

    private void showState(boolean loading, String message, @Nullable String action,
                           @Nullable View.OnClickListener onAction) {
        content.setVisibility(View.GONE);
        state.setVisibility(View.VISIBLE);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        stateMessage.setText(message);
        stateAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        stateAction.setText(action);
        stateAction.setOnClickListener(onAction);
    }

    private int color(int res) {
        return ContextCompat.getColor(requireContext(), res);
    }

    private static void setTextOrGone(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
