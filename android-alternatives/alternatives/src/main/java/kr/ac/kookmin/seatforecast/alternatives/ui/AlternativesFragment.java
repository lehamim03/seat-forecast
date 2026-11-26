package kr.ac.kookmin.seatforecast.alternatives.ui;

import android.Manifest;
import android.location.Location;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import kr.ac.kookmin.seatforecast.alternatives.Place;
import kr.ac.kookmin.seatforecast.alternatives.R;
import kr.ac.kookmin.seatforecast.alternatives.TripExtras;
import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;
import kr.ac.kookmin.seatforecast.alternatives.model.Subway;

/**
 * ③ 실시간 대안 — 기다리기 · 걸어가기 · 다른 버스 + 지하철 비교 (출발지 · 도착지 모드).
 * 순서: 로그인 확인 → 도착지(extra, 없으면 AlternativesHost.loadLastDestination) → 출발지(extra 의 검색한 곳, 없거나 "현재 위치"면 GPS)
 * → ID 토큰 → {@link AlternativesSource#load} (백그라운드) → 화면.
 * 출발지가 "현재 위치"면 1분 자동 갱신 때마다 GPS 를 다시 읽습니다 (못 읽으면 마지막 좌표).
 *
 * <p>12주차 1분 자동 갱신: 화면이 보이는 동안({@link #onStart} ~ {@link #onStop})
 * {@link Alternatives#refreshIntervalMs()} 마다 다시 불러옵니다. 이미 화면이 있으면 덮지 않고 조용히 바꾸며,
 * 실패하면 이전 정보를 그대로 두고 '갱신하지 못했어요' 를 표시합니다. 다른 화면에 갔다 오면 간격이 지났을 때 바로 갱신합니다.
 */
public class AlternativesFragment extends Fragment {

    private static final String[] LOCATION_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable tick = this::autoRefresh;
    /** 지난 출근길 재생 시각 (null = 실시간) */
    @Nullable
    private String replayAt;
    private View replayBar;
    private TextView replayTitle;
    private TextView replayMessage;
    private TextView replayNote;
    /** 새 요청이 시작되면 이전 요청 결과는 버림 */
    private int requestId;
    private boolean loaded;
    private boolean started;
    /** 마지막으로 불러오기를 끝낸 때 (성공 · 실패 모두, SystemClock.elapsedRealtime) — 다음 자동 갱신 기준 */
    private long lastAttemptAt;
    /** 마지막으로 화면을 바꾼 시각 (벽시계) — '07:41:05 갱신' 표시용 */
    private long lastUpdatedWall;
    /** 지금 보이는 값이 예시 응답인지 (실시간 계산이 아니면 '예시 데이터' 표시) */
    private boolean example;
    /** route_source=saved_nearby — ODsay 대신 근처 저장 경로로 계산 */
    private boolean savedNearby;

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
    private TextView subwayTitle;

    /** 출발지 — null 이면 현재 위치(GPS) */
    @Nullable
    private Place fromPlace;
    /** 도착지 — null 이면 아직 모름 (마지막 도착지를 물어봄) */
    @Nullable
    private Place to;
    /** 현재 위치로 마지막으로 받은 좌표 (extra 로 받은 "현재 위치" 좌표로 시작) */
    @Nullable
    private Place gpsFix;
    /** 지금 보이는 대안을 계산한 출발지 · 도착지 — 카드를 누르면 넘김 */
    @Nullable
    private Place shownFrom;
    @Nullable
    private Place shownTo;
    private boolean askedPermission;
    private boolean pendingSilent;
    private AltLocation location;
    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                if (getView() != null) reload(pendingSilent);
            });
    private View state;
    private View progress;
    private TextView stateMessage;
    private MaterialButton stateAction;

    /**
     * @param from 출발지 — null 이거나 이름이 "현재 위치"면 GPS (그 좌표는 GPS 를 못 읽을 때 대신 씀)
     * @param to   도착지 — null 이면 AlternativesHost.loadLastDestination
     */
    public static AlternativesFragment newInstance(@Nullable Place from, @Nullable Place to) {
        return newInstance(from, to, null);
    }

    /** @param replayAt 지난 출근길 재생 시각 (예: "2026-11-11T08:20") — 있으면 replay 로 요청, 자동 갱신 · GPS 꺼짐 */
    public static AlternativesFragment newInstance(@Nullable Place from, @Nullable Place to, @Nullable String replayAt) {
        AlternativesFragment f = new AlternativesFragment();
        Bundle args = new Bundle();
        TripExtras.put(args, from, to);
        if (replayAt != null) args.putString(TripExtras.REPLAY, replayAt);
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
        subwayTitle = v.findViewById(R.id.alt_subway_title);
        location = new AltLocation(requireContext());
        replayAt = TripExtras.replay(getArguments());
        replayBar = v.findViewById(R.id.alt_replay_bar);
        replayTitle = v.findViewById(R.id.alt_replay_title);
        replayMessage = v.findViewById(R.id.alt_replay_message);
        replayNote = v.findViewById(R.id.alt_replay_note);
        Place argFrom = TripExtras.from(getArguments());
        if (argFrom == null || argFrom.isCurrentLocation()) {
            fromPlace = null;
            gpsFix = argFrom != null && argFrom.hasCoords() ? argFrom : null;
        } else {
            fromPlace = argFrom;
        }
        Place argTo = TripExtras.to(getArguments());
        to = argTo != null && argTo.hasCoords() ? argTo : null;
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
        location.stop();
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
        if (!AltFormat.usesGps(replayAt)) {   // 재생: 출발지 · 도착지 · GPS 없이 replay 만
            if (!silent) {
                showLoading();
                stateMessage.setText(R.string.alt_replay_loading);
            }
            host.getIdToken(new Callback<String>() {
                @Override
                public void onSuccess(String token) {
                    if (!stale(id)) fetchReplay(id, token, silent);
                }

                @Override
                public void onError(String message) {
                    if (!stale(id)) fetchReplay(id, null, silent);
                }
            });
            return;
        }
        if (silent) {
            updated.setText(R.string.alt_refreshing);
            updated.setTextColor(color(R.color.alt_muted));
            refresh.setEnabled(false);
        } else {
            showLoading();
        }
        if (to == null) {
            host.loadLastDestination(new Callback<Place>() {
                @Override
                public void onSuccess(Place result) {
                    if (stale(id)) return;
                    if (result != null && result.hasCoords()) {
                        to = result;
                        findOrigin(id, silent);
                    } else {
                        showState(false, getString(R.string.alt_no_destination), null, null);
                    }
                }

                @Override
                public void onError(String message) {
                    if (!stale(id)) showState(false, getString(R.string.alt_no_destination), null, null);
                }
            });
            return;
        }
        findOrigin(id, silent);
    }

    /** 출발지 — 넘겨받은 곳이면 그대로, 현재 위치면 GPS (자동 갱신 때도 다시 읽음) */
    private void findOrigin(int id, boolean silent) {
        if (fromPlace != null) {
            withToken(id, fromPlace, silent);
            return;
        }
        if (!AltLocation.hasPermission(requireContext())) {
            if (gpsFix != null) {   // 홈이 준 "현재 위치" 좌표로
                withToken(id, gpsFix, silent);
            } else if (!askedPermission) {
                askedPermission = true;
                pendingSilent = silent;
                permissionLauncher.launch(LOCATION_PERMISSIONS);
            } else {
                failed(getString(R.string.alt_location_denied), silent);
            }
            return;
        }
        if (!silent) stateMessage.setText(R.string.alt_locating);
        location.request(new AltLocation.Listener() {
            @Override
            public void onLocation(@NonNull Location l) {
                if (stale(id)) return;
                gpsFix = new Place(Place.CURRENT_LOCATION, l.getLatitude(), l.getLongitude());
                if (!silent) stateMessage.setText(R.string.alt_loading);
                withToken(id, gpsFix, silent);
            }

            @Override
            public void onFailure(int message) {
                if (stale(id)) return;
                if (gpsFix != null) withToken(id, gpsFix, silent);   // 마지막 좌표로
                else failed(getString(message), silent);
            }
        });
    }

    private void withToken(int id, Place from, boolean silent) {
        final Place dest = to;
        if (dest == null) return;
        Alternatives.host().getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!stale(id)) fetch(id, from, dest, token, silent);
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

    private void fetch(int id, Place from, Place dest, @Nullable String token, boolean silent) {
        final AlternativesSource source = Alternatives.source();
        EXECUTOR.execute(() -> {
            AlternativesResult result = null;
            String error = null;
            try {
                result = source.load(from, dest, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;   // 아래에서 기본 문구
            }
            final AlternativesResult r = result;
            final String message = error;
            main.post(() -> {
                if (stale(id)) return;
                if (r != null) {
                    shownFrom = from;
                    shownTo = dest;
                    succeeded(r);
                }
                else failed(message != null ? message : getString(R.string.alt_error_default), silent);
            });
        });
    }

    /** 지난 출근길 재생 요청 — 400 replay_no_data 의 message 는 그대로 보여 줌 */
    private void fetchReplay(int id, @Nullable String token, boolean silent) {
        final AlternativesSource source = Alternatives.source();
        final String at = replayAt;
        EXECUTOR.execute(() -> {
            AlternativesResult result = null;
            String error = null;
            try {
                result = source.loadReplay(at, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;
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
        example = AlternativesResult.SOURCE_EXAMPLE.equals(r.source);
        savedNearby = r.isSavedNearbyRoute();
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
                AltFormat.refreshInterval(replayAt, Alternatives.refreshIntervalMs()));
        if (delay >= 0) main.postDelayed(tick, delay);
    }

    /** '07:41:05 갱신 · 1분마다 자동 갱신' / 실패면 '갱신하지 못했어요 · 07:41:05 정보예요' (빨강) */
    private void bindUpdated(boolean failed) {
        refresh.setEnabled(true);
        if (replayAt != null && !failed) {   // 재생 중에는 갱신 시각 대신 자동 갱신 꺼짐 안내
            String text = getString(R.string.alt_replay_updated);
            if (savedNearby) text += " · " + getString(R.string.alt_saved_nearby);
            updated.setText(text);
            updated.setTextColor(color(R.color.alt_muted));
            return;
        }
        String time = new SimpleDateFormat("HH:mm:ss", Locale.KOREA).format(new Date(lastUpdatedWall));
        if (failed) {
            updated.setText(getString(R.string.alt_stale, time));
            updated.setTextColor(color(R.color.alt_low));
        } else {
            String text = getString(R.string.alt_updated, time, autoText());
            if (example) text = getString(R.string.alt_with_example, text);
            if (savedNearby) text += " · " + getString(R.string.alt_saved_nearby);
            updated.setText(text);
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

        Place o = r.origin != null ? r.origin : shownFrom;
        Place d = r.destination != null ? r.destination : shownTo;
        String fromName = o == null ? getString(R.string.alt_current_location) : o.nameOr(getString(R.string.alt_current_location));
        String toName = d == null ? getString(R.string.alt_destination) : d.nameOr(getString(R.string.alt_destination));
        String time = AltFormat.time(r.generatedAt);
        header.setText(time == null ? getString(R.string.alt_header_no_time, fromName, toName)
                : getString(R.string.alt_header, fromName, toName, time));

        bindReplay(r);
        bindBanner(AltFormat.banner(r));
        setTextOrGone(summary, r.recommendation == null ? null : r.recommendation.message);

        cards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Option opt : options) {
            boolean recommended = AltFormat.isRecommended(opt, r);
            cards.addView(OptionCard.create(inflater, cards, opt, options, recommended,
                    v -> {
                        if (shownFrom != null && shownTo != null) {
                            Alternatives.host().onOptionClicked(requireContext(), opt, shownFrom, shownTo);
                        } else {
                            Alternatives.host().onOptionClicked(requireContext(), opt);
                        }
                    }));
        }
        bindSubway(r.subway, AltFormat.subwayRecommended(r));

        state.setVisibility(View.GONE);
        content.setVisibility(View.VISIBLE);
    }

    /** 지난 출근길 재생 — replay.message 를 맨 위에 (A 홈 재생 띠와 같은 모양) */
    private void bindReplay(AlternativesResult r) {
        if (replayAt == null) {
            replayBar.setVisibility(View.GONE);
            return;
        }
        String day = r.replay != null && r.replay.date != null ? r.replay.date : replayAt.replace('T', ' ');
        String at = AltFormat.time(r.replay == null ? null : r.replay.at);
        replayTitle.setText(getString(R.string.alt_replay_title, at == null ? day : day + " " + at));
        setTextOrGone(replayMessage, r.replay == null ? null : r.replay.message);
        setTextOrGone(replayNote, r.replay == null ? null : r.replay.note);
        replayBar.setVisibility(View.VISIBLE);
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
        if (b.standing) adviceText = R.string.alt_advice_standing;
        if (b.seatUnknown) {
            statusText = R.string.alt_seat_unknown;
            adviceText = R.string.alt_advice_seat_unknown;
        }
        String probText = b.standing ? getString(R.string.alt_standing)
                : b.seatUnknown ? getString(R.string.alt_seat_unknown) : AltFormat.percent(b.prob);
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
            bannerSub.setText(b.standing || b.seatUnknown
                    ? getString(R.string.alt_banner_sub_plain, OptionCard.seats(requireContext(), bus), probText)
                    : getString(R.string.alt_banner_sub, OptionCard.seats(requireContext(), bus), probText));
            bannerSub.setVisibility(View.VISIBLE);
        } else {
            bannerTitle.setText(getString(R.string.alt_banner_title_none, route));
            bannerSub.setVisibility(View.GONE);
        }
        bannerProb.setVisibility(known && b.status != AltFormat.Status.SKIPPED ? View.VISIBLE : View.GONE);
        bannerProb.setText(probText);
        bannerProb.setBackgroundResource(AltStyle.pill(b.status));
        bannerProb.setTextColor(c);
    }

    /** @param recommended recommendation.kind 가 "지하철"이면 '★ 추천' */
    private void bindSubway(@Nullable Subway s, boolean recommended) {
        if (s == null || s.arriveAt == null) {
            subway.setVisibility(View.GONE);
            return;
        }
        subwayTitle.setText(recommended ? R.string.alt_subway_recommended : R.string.alt_subway_title);
        subwayTitle.setTextColor(color(recommended ? R.color.alt_high : R.color.alt_muted));
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
