package kr.ac.kookmin.seatforecast.home.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Menu;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.UsualTrip;
import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * ② 홈 — 정류장 × 노선 추천. 팀 앱 하단 탭의 '홈'에 이 Fragment 를 그대로 넣으면 됩니다.
 * 순서: 로그인 확인 → 평소 정류장 · 노선 → ID 토큰 → {@link RecommendationSource#load} (백그라운드) → 화면.
 *
 * <p>12주차 — 새로고침: 헤더 새로고침 버튼 · 목록 당기기 · 화면이 보이는 동안 {@link LiveTime#AUTO_REFRESH_MIN}분마다 자동.
 * 새로고침 중에도 지금 목록은 그대로 두고, 실패하면 이전 목록을 남긴 채 "갱신 실패" 를 표시합니다.
 * 다가오는 차의 남은 분은 응답을 받은 뒤 지난 시간만큼 줄여 보여 줍니다 ({@link LiveTime#TICK_MS} 마다).
 */
public class HomeFragment extends Fragment {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    /** 고른 정렬 기준은 앱을 다시 켜도 유지 */
    private static final String PREFS = "home_prefs";
    private static final String KEY_SORT = "sort";
    private static final float DISABLED_ALPHA = 0.4f;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::onTick;
    /** 새 요청이 시작되면 이전 요청 결과는 버림 */
    private int requestId;
    /** 목록을 보여 주는 중 (상태 화면이 아님) */
    private boolean loaded;
    /** 목록을 둔 채 다시 불러오는 중 (새로고침 버튼 · 당기기 · 자동) */
    private boolean refreshing;
    /** 마지막 새로고침이 실패해 이전 목록을 보여 주는 중 */
    private boolean refreshFailed;
    private boolean resumed;
    /** 응답을 받은 때 / 마지막으로 불러오기를 시작한 때 ({@link SystemClock#elapsedRealtime}) */
    private long receivedAt;
    private long lastAttemptAt;
    /** 지금 보여 주는 응답 — '지금 HH:MM' 칩 · 갱신 시각 */
    @Nullable
    private Recommendations current;

    private TextView usualStop;
    private TextView dateChip;
    private TextView dayLabel;
    private TextView nowChip;
    private TextView summary;
    private RecyclerView list;
    private View state;
    private View progress;
    private TextView stateMessage;
    private MaterialButton stateAction;
    private MaterialButton departureAlarm;
    private TextView sortButton;
    private TextView updated;
    private SwipeRefreshLayout swipe;
    private ImageButton refreshButton;
    private OptionAdapter adapter;
    private OptionSort sort = OptionSort.ARRIVAL;

    /** 서버 ranking 그대로 (정렬을 바꿀 때 다시 씀) */
    private List<Option> ranking = new ArrayList<>();
    /** 서버 ranking 1순위 — '1순위' 태그 · 출발 알림 기준 (정렬과 무관) */
    @Nullable
    private Option first;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.home_fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View v, @Nullable Bundle savedInstanceState) {
        usualStop = v.findViewById(R.id.home_usual_stop);
        dateChip = v.findViewById(R.id.home_chip_date);
        dayLabel = v.findViewById(R.id.home_day_label);
        nowChip = v.findViewById(R.id.home_chip_now);
        summary = v.findViewById(R.id.home_summary);
        list = v.findViewById(R.id.home_list);
        state = v.findViewById(R.id.home_state);
        progress = v.findViewById(R.id.home_progress);
        stateMessage = v.findViewById(R.id.home_state_message);
        stateAction = v.findViewById(R.id.home_state_action);
        departureAlarm = v.findViewById(R.id.home_departure_alarm);
        sortButton = v.findViewById(R.id.home_sort);
        updated = v.findViewById(R.id.home_updated);
        swipe = v.findViewById(R.id.home_swipe);
        refreshButton = v.findViewById(R.id.home_refresh);

        adapter = new OptionAdapter(o -> Home.host().onOptionClicked(requireContext(), o));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        v.findViewById(R.id.home_trip).setOnClickListener(t -> Home.host().onTripClicked(requireContext()));
        departureAlarm.setOnClickListener(b -> {
            if (first != null) Home.host().onDepartureAlarmClicked(requireContext(), first);
        });
        sort = loadSort();
        showSortLabel();
        sortButton.setOnClickListener(this::openSortMenu);

        swipe.setColorSchemeResources(R.color.home_primary);
        // 목록 · 상태 화면이 FrameLayout 안에 있어서, 목록이 맨 위일 때만 당기기가 되도록 직접 알려 줌
        swipe.setOnChildScrollUpCallback((parent, child) ->
                list.getVisibility() == View.VISIBLE && list.canScrollVertically(-1));
        swipe.setOnRefreshListener(() -> refresh(true));
        refreshButton.setOnClickListener(b -> refresh(true));

        loaded = false;
        refreshing = false;
        refreshFailed = false;
        current = null;
    }

    /** 정렬 메뉴: 도착 빠른 순 · 환승 적은 순 · 가까운 정류장 순 · 탑승 확률 높은 순 */
    private void openSortMenu(View anchor) {
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        OptionSort[] all = OptionSort.values();
        for (int i = 0; i < all.length; i++) {
            menu.getMenu().add(Menu.NONE, i, i, label(all[i])).setCheckable(true).setChecked(all[i] == sort);
        }
        menu.getMenu().setGroupCheckable(Menu.NONE, true, true);
        menu.setOnMenuItemClickListener(item -> {
            sort = all[item.getItemId()];
            saveSort();
            showSortLabel();
            adapter.submit(sort.apply(ranking), first, elapsedMin());
            list.scrollToPosition(0);
            return true;
        });
        menu.show();
    }

    @StringRes
    private static int label(OptionSort s) {
        switch (s) {
            case FEWEST_TRANSFERS: return R.string.home_sort_transfers;
            case NEAREST_STOP: return R.string.home_sort_nearest;
            case BOARD_PROB: return R.string.home_sort_prob;
            case ARRIVAL:
            default: return R.string.home_sort_arrival;
        }
    }

    private void showSortLabel() {
        String text = getString(label(sort));
        sortButton.setText(text);
        sortButton.setContentDescription(getString(R.string.home_sort_desc, text));
    }

    private SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private OptionSort loadSort() {
        try {
            return OptionSort.valueOf(prefs().getString(KEY_SORT, OptionSort.ARRIVAL.name()));
        } catch (IllegalArgumentException e) {
            return OptionSort.ARRIVAL;
        }
    }

    private void saveSort() {
        prefs().edit().putString(KEY_SORT, sort.name()).apply();
    }

    @Override
    public void onStart() {
        super.onStart();
        // 처음 열 때, 그리고 로그인 화면에서 돌아왔을 때
        if (!loaded && !refreshing) reload();
    }

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;
        onTick();   // 다른 화면 · 백그라운드에서 돌아오면 남은 분을 바로 고치고, 오래됐으면 새로고침
    }

    @Override
    public void onPause() {
        resumed = false;
        main.removeCallbacks(tick);
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        requestId++;   // 진행 중인 요청 결과 버림
        main.removeCallbacks(tick);
        super.onDestroyView();
    }

    /** 추천을 처음부터 다시 불러옵니다 — 로딩 화면 (팀 앱에서 설정이 바뀐 뒤 부를 수 있음). */
    public void reload() {
        start(false, false);
    }

    /**
     * 지금 목록은 그대로 두고 다시 불러옵니다 (헤더 새로고침 버튼 · 목록 당기기 · 자동).
     * 보여 주는 목록이 없으면(오류 화면 등) {@link #reload()} 와 같고, 이미 불러오는 중이면 무시합니다.
     *
     * @param byUser 사용자가 직접 했으면 true — 실패하면 Snackbar 로 알림 (자동이면 갱신 시각 줄에만 표시)
     */
    public void refresh(boolean byUser) {
        if (getView() == null) return;
        if (refreshing) return;
        if (!loaded) {
            swipe.setRefreshing(false);
            reload();
            return;
        }
        start(true, byUser);
    }

    /**
     * @param keepList true = 새로고침 (목록을 둔 채), false = 처음 불러오기 (로딩 화면)
     */
    private void start(boolean keepList, boolean byUser) {
        if (getView() == null) return;
        final int id = ++requestId;
        final HomeHost host = Home.host();
        lastAttemptAt = SystemClock.elapsedRealtime();
        if (!host.isSignedIn()) {
            showSignedOut();
            return;
        }
        if (keepList) setRefreshing(true);
        else showLoading();
        host.loadUsualTrip(new Callback<UsualTrip>() {
            @Override
            public void onSuccess(UsualTrip trip) {
                if (stale(id)) return;
                host.getIdToken(new Callback<String>() {
                    @Override
                    public void onSuccess(String token) {
                        if (!stale(id)) fetch(id, trip, token, keepList, byUser);
                    }

                    @Override
                    public void onError(String message) {
                        if (!stale(id)) failed(message, keepList, byUser);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!stale(id)) failed(message, keepList, byUser);
            }
        });
    }

    private boolean stale(int id) {
        return id != requestId || getView() == null;
    }

    private void fetch(int id, UsualTrip trip, @Nullable String token, boolean keepList, boolean byUser) {
        fetch(id, trip, token, keepList, byUser, false);
    }

    /**
     * @param retried 401 뒤 토큰을 새로 받아 다시 보낸 요청이면 true (13주차 — 한 번만 다시 시도)
     */
    private void fetch(int id, UsualTrip trip, @Nullable String token, boolean keepList, boolean byUser,
                       boolean retried) {
        final RecommendationSource source = Home.source();
        EXECUTOR.execute(() -> {
            Recommendations result = null;
            SourceException error = null;
            try {
                result = source.load(trip.homeStopId, trip.route, token);
            } catch (SourceException e) {
                error = e;
            } catch (RuntimeException e) {
                error = null;   // 아래에서 기본 문구
            }
            final Recommendations r = result;
            final SourceException err = error;
            main.post(() -> {
                if (stale(id)) return;
                if (r != null) {
                    show(r);
                } else if (err != null && err.isUnauthorized()) {
                    unauthorized(id, trip, keepList, byUser, retried, err.getMessage());
                } else {
                    String message = err != null && err.getMessage() != null ? err.getMessage()
                            : getString(R.string.home_error_default);
                    failed(message, keepList, byUser);
                }
            });
        });
    }

    /** 13주차: 서버 401 — 토큰을 새로 받아 한 번 더. 그래도 401 이면 다시 로그인 화면 */
    private void unauthorized(int id, UsualTrip trip, boolean keepList, boolean byUser, boolean retried,
                              String message) {
        if (retried) {
            showSessionExpired();
            return;
        }
        Home.host().getIdToken(true, new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!stale(id)) fetch(id, trip, token, keepList, byUser, true);
            }

            @Override
            public void onError(String m) {
                if (!stale(id)) failed(message, keepList, byUser);
            }
        });
    }

    /** 불러오기 실패 — 새로고침이면 이전 목록을 남기고, 처음 불러오기면 오류 화면 */
    private void failed(String message, boolean keepList, boolean byUser) {
        if (!keepList || !loaded) {
            showError(message);
            return;
        }
        refreshFailed = true;
        setRefreshing(false);
        if (byUser) {
            Snackbar.make(requireView(), getString(R.string.home_refresh_failed, message), Snackbar.LENGTH_LONG)
                    .setAction(R.string.home_retry, b -> refresh(true))
                    .show();
        }
    }

    private void show(Recommendations r) {
        current = r;
        loaded = true;
        refreshFailed = false;
        receivedAt = SystemClock.elapsedRealtime();
        setRefreshing(false);
        swipe.setEnabled(true);

        if (r.homeStop != null && r.homeStop.name != null) {
            usualStop.setText(getString(R.string.home_usual_stop, r.homeStop.name));
        }
        // 칩 3개: 날짜 · 평일 출퇴근(day.kind) · 지금 HH:MM (generated_at + 받은 뒤 지난 분)
        setTextOrGone(dateChip, TripFormat.dateChip(r.generatedAt));
        setTextOrGone(dayLabel, TripFormat.dayChip(r.day));
        showNow();
        setTextOrGone(summary, r.recommendation == null ? null : r.recommendation.message);

        List<Option> options = r.rankingOrEmpty();
        ranking = new ArrayList<>(options);
        first = options.isEmpty() ? null : options.get(0);
        // 새로고침해도 보던 자리는 그대로 (정렬을 바꿀 때만 맨 위로)
        adapter.submit(sort.apply(ranking), first, 0);
        departureAlarm.setEnabled(first != null);

        if (options.isEmpty()) {
            showState(false, getString(R.string.home_empty), null, null);   // 당기기 · 버튼으로 다시 확인 가능
        } else {
            state.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
            showUpdated();
            scheduleTick();
        }
    }

    private void setRefreshing(boolean on) {
        refreshing = on;
        if (swipe.isRefreshing() != on) swipe.setRefreshing(on);
        setRefreshButtonEnabled(!on);
        showUpdated();
    }

    private void setRefreshButtonEnabled(boolean enabled) {
        refreshButton.setEnabled(enabled);
        refreshButton.setAlpha(enabled ? 1f : DISABLED_ALPHA);
    }

    // ── 12주차: 남은 분 · 갱신 시각 · 자동 새로고침 ──

    private int elapsedMin() {
        return loaded ? LiveTime.elapsedMin(receivedAt, SystemClock.elapsedRealtime()) : 0;
    }

    /** {@link LiveTime#TICK_MS} 마다: 남은 분 · '몇 분 전' 다시 그리기, 오래됐으면 자동 새로고침 */
    private void onTick() {
        main.removeCallbacks(tick);
        if (getView() == null || !loaded) return;
        adapter.setElapsedMin(elapsedMin());
        showNow();
        showUpdated();
        if (!refreshing && LiveTime.shouldAutoRefresh(receivedAt, lastAttemptAt, SystemClock.elapsedRealtime())) {
            refresh(false);
        }
        scheduleTick();
    }

    private void scheduleTick() {
        main.removeCallbacks(tick);
        if (resumed && loaded) main.postDelayed(tick, LiveTime.TICK_MS);
    }

    /** '지금 HH:MM' 칩 — generated_at 에 받은 뒤 지난 분을 더함 */
    private void showNow() {
        if (current == null) return;
        String time = LiveTime.plusMinutes(TripFormat.time(current.generatedAt), elapsedMin());
        setTextOrGone(nowChip, time == null ? null : getString(R.string.home_chip_now, time));
    }

    /** "07:40 기준 · 3분 전 갱신" / "새로 불러오는 중…" / "갱신 실패 · 07:40 기준" */
    private void showUpdated() {
        if (current == null || !loaded) {
            updated.setVisibility(View.GONE);
            return;
        }
        String base = TripFormat.time(current.generatedAt);
        String text;
        @ColorRes int color = R.color.home_muted;
        if (refreshing) {
            text = getString(R.string.home_updating);
        } else if (refreshFailed) {
            text = getString(R.string.home_update_failed, base == null ? "—" : base);
            color = R.color.home_badge_mid;
        } else if (base == null) {
            text = null;
        } else {
            int ago = elapsedMin();
            text = ago <= 0 ? getString(R.string.home_updated_now, base)
                    : getString(R.string.home_updated_ago, base, ago);
        }
        updated.setTextColor(ContextCompat.getColor(requireContext(), color));
        setTextOrGone(updated, text);
    }

    // ── 상태 화면 ──

    private void showLoading() {
        showState(true, getString(R.string.home_loading), null, null);
    }

    private void showError(String message) {
        showState(false, message, getString(R.string.home_retry), v -> reload());
    }

    private void showSignedOut() {
        showState(false, getString(R.string.home_signed_out), getString(R.string.home_sign_in),
                v -> Home.host().requestSignIn(requireContext()));
    }

    /** 13주차: 토큰을 새로 받아도 서버가 401 — 목록이 있어도 지우고 다시 로그인하도록 (오래된 목록을 믿지 않게) */
    private void showSessionExpired() {
        showState(false, getString(R.string.home_session_expired), getString(R.string.home_sign_in),
                v -> Home.host().requestSignIn(requireContext()));
        swipe.setEnabled(false);
        setRefreshButtonEnabled(false);
    }

    /** 상태 화면 = 보여 줄 목록이 없음 → 남은 분 · 자동 새로고침도 멈춤 */
    private void showState(boolean loading, String message, @Nullable String action,
                           @Nullable View.OnClickListener onAction) {
        list.setVisibility(View.GONE);
        state.setVisibility(View.VISIBLE);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        stateMessage.setText(message);
        stateAction.setVisibility(action == null ? View.GONE : View.VISIBLE);
        stateAction.setText(action);
        stateAction.setOnClickListener(onAction);
        if (!loading) departureAlarm.setEnabled(false);

        loaded = false;
        refreshing = false;
        refreshFailed = false;
        main.removeCallbacks(tick);
        updated.setVisibility(View.GONE);
        swipe.setRefreshing(false);
        boolean signedIn = Home.host().isSignedIn();
        // 로딩 중 · 로그인 필요 화면에서는 새로고침을 막고, 오류 화면에서는 당기기 · 버튼으로 다시 시도
        swipe.setEnabled(!loading && signedIn);
        setRefreshButtonEnabled(!loading && signedIn);
    }

    private static void setTextOrGone(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
