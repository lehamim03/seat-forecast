package kr.ac.kookmin.seatforecast.home.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.location.Location;
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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
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
import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.data.RecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.ReplaySource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * ② 홈 — 출발지 → 도착지 추천. 팀 앱 하단 탭의 '홈'에 이 Fragment 를 그대로 넣으면 됩니다.
 * 순서: 로그인 확인 → 도착지(이 기기에서 마지막으로 고른 곳, 없으면 HomeHost.loadLastDestination, 그래도 없으면 검색 안내)
 * → 출발지("현재 위치"면 GPS, 처음이면 권한 요청 · 거부하면 출발지 검색) → ID 토큰
 * → {@link RecommendationSource#load} (백그라운드) → 화면. 검색할 때마다(자동 새로고침 제외) HomeHost.saveLastTrip.
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
    /** 마지막으로 고른 출발지 · 도착지 (앱을 다시 켜도 유지) — 출발지가 "현재 위치"면 KEY_FROM 없음 */
    private static final String KEY_FROM = "trip_from";
    private static final String KEY_TO = "trip_to";
    private static final String STATE_SEARCHING_ORIGIN = "searching_origin";
    private static final Gson GSON = new Gson();
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

    /** 출발지 — null 이면 "현재 위치"(GPS) */
    @Nullable
    private Place fromPlace;
    /** 도착지 — null 이면 아직 정하지 않음 (검색 안내) */
    @Nullable
    private Place to;
    /** "현재 위치" 로 마지막으로 받은 GPS 좌표 (위치를 다시 못 받으면 새로고침에 씀) */
    @Nullable
    private Place gpsFix;
    /** 지금 보여 주는 추천을 계산한 출발지 · 도착지 — 카드에서 B 화면으로 넘김 */
    @Nullable
    private Place shownFrom;
    @Nullable
    private Place shownTo;
    /** 검색 화면을 출발지로 열었는지 */
    private boolean searchingOrigin;
    /** 위치 권한을 물어보는 동안 기다리는 요청 */
    private boolean pendingKeepList;
    private boolean pendingByUser;
    private HomeLocation location;

    private final ActivityResultLauncher<String[]> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> onPermissionResult());
    private final ActivityResultLauncher<Intent> searchLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), r -> {
                if (r.getResultCode() == Activity.RESULT_OK) onPlaceChosen(searchingOrigin, r.getData());
            });

    private TextView fromName;
    private TextView toName;

    /** 시연용 지난 출근길 재생 중이면 "2026-11-12T07:40", 실시간이면 null (화면을 닫으면 실시간으로) */
    @Nullable
    private String replayAt;
    private View tripInputs;
    private View replayBar;
    private TextView replayTitle;
    private TextView replayMessage;
    private TextView replayNote;
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
        tripInputs = v.findViewById(R.id.home_trip_inputs);
        replayBar = v.findViewById(R.id.home_replay_bar);
        replayTitle = v.findViewById(R.id.home_replay_title);
        replayMessage = v.findViewById(R.id.home_replay_message);
        replayNote = v.findViewById(R.id.home_replay_note);
        View replayButton = v.findViewById(R.id.home_replay);
        replayButton.setVisibility(Home.replays() == null ? View.GONE : View.VISIBLE);
        replayButton.setOnClickListener(b -> pickReplayDay());
        v.findViewById(R.id.home_replay_exit).setOnClickListener(b -> exitReplay());
        fromName = v.findViewById(R.id.home_from_name);
        toName = v.findViewById(R.id.home_to_name);
        location = new HomeLocation(requireContext());
        if (savedInstanceState != null) searchingOrigin = savedInstanceState.getBoolean(STATE_SEARCHING_ORIGIN);
        loadTrip();
        bindTrip();
        v.findViewById(R.id.home_from).setOnClickListener(b -> openSearch(true));
        v.findViewById(R.id.home_to).setOnClickListener(b -> openSearch(false));
        v.findViewById(R.id.home_swap).setOnClickListener(b -> swap());
        v.findViewById(R.id.home_favorite).setOnClickListener(this::openFavorites);
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

        // 카드 → B 실시간 대안 (그 추천을 계산한 출발지 · 도착지를 extra 6개로 — TripExtras)
        adapter = new OptionAdapter(o -> {
            if (replayAt != null) {
                // 재생 중 — B 도 같은 시각으로 재생 (extra "replay")
                Home.host().onReplayOptionClicked(requireContext(), o, shownFrom, shownTo, replayAt);
            } else if (shownFrom != null && shownTo != null) {
                Home.host().onOptionClicked(requireContext(), o, shownFrom, shownTo);
            } else {
                Home.host().onOptionClicked(requireContext(), o);
            }
        });
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
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

    // ── 출발지 · 도착지 ──

    private void bindTrip() {
        String from = fromPlace == null ? getString(R.string.home_current_location)
                : fromPlace.nameOr(getString(R.string.home_current_location));
        fromName.setText(from);
        requireView().findViewById(R.id.home_from).setContentDescription(getString(R.string.home_from_desc, from));
        String dest = to == null ? getString(R.string.home_to_empty) : to.nameOr(getString(R.string.home_to_empty));
        toName.setText(dest);
        toName.setAlpha(to == null ? 0.7f : 1f);
        requireView().findViewById(R.id.home_to).setContentDescription(getString(R.string.home_to_desc, dest));
    }

    private void openSearch(boolean origin) {
        searchingOrigin = origin;
        searchLauncher.launch(PlaceSearchActivity.newIntent(requireContext(), origin));
    }

    /** 검색 화면에서 고른 곳 → 저장하고 다시 계산 */
    private void onPlaceChosen(boolean origin, @Nullable Intent data) {
        if (getView() == null) return;
        Place chosen = PlaceSearchActivity.resultPlace(data);
        if (origin) {
            if (PlaceSearchActivity.isCurrentLocation(data)) fromPlace = null;
            else if (chosen != null) fromPlace = chosen;
            else return;
        } else {
            if (chosen == null) return;
            to = chosen;
        }
        changedTrip();
    }

    /** 바꾸기 — 출발지가 "현재 위치"면 마지막 GPS 좌표를 도착지로 씀 (아직 없으면 안내) */
    private void swap() {
        if (to == null) {
            openSearch(false);
            return;
        }
        Place newTo;
        if (fromPlace != null) {
            newTo = fromPlace;
        } else if (gpsFix != null) {
            newTo = new Place(getString(R.string.home_current_location), gpsFix.lat, gpsFix.lng);
        } else {
            Snackbar.make(requireView(), R.string.home_swap_need_place, Snackbar.LENGTH_LONG)
                    .setAction(R.string.home_search_origin, b -> openSearch(true))
                    .show();
            return;
        }
        fromPlace = to;
        to = newTo;
        changedTrip();
    }

    /** 즐겨찾기 버튼: 즐겨찾는 곳을 도착지로 고르거나, 지금 도착지를 즐겨찾기에 추가 (D 콜백) */
    private void openFavorites(View anchor) {
        Home.host().loadFavorites(new Callback<List<Place>>() {
            @Override
            public void onSuccess(List<Place> result) {
                if (getView() != null) showFavorites(anchor, result);
            }

            @Override
            public void onError(String message) {
                if (getView() != null) showFavorites(anchor, new ArrayList<>());
            }
        });
    }

    private void showFavorites(View anchor, @Nullable List<Place> favorites) {
        List<Place> places = new ArrayList<>();
        if (favorites != null) for (Place p : favorites) if (p != null && p.hasCoords()) places.add(p);
        if (places.isEmpty() && to == null) {
            Snackbar.make(requireView(), R.string.home_favorite_none, Snackbar.LENGTH_LONG).show();
            return;
        }
        PopupMenu menu = new PopupMenu(requireContext(), anchor);
        for (int i = 0; i < places.size(); i++) menu.getMenu().add(Menu.NONE, i, i, places.get(i).name);
        final int add = places.size();
        if (to != null) menu.getMenu().add(Menu.NONE, add, add, getString(R.string.home_favorite_add, to.name));
        menu.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == add) {
                Home.host().addFavorite(requireContext(), to);
            } else {
                to = places.get(item.getItemId());
                changedTrip();
            }
            return true;
        });
        menu.show();
    }

    private void changedTrip() {
        saveTrip();
        bindTrip();
        reload();
    }

    private void loadTrip() {
        fromPlace = readPlace(KEY_FROM);
        to = readPlace(KEY_TO);
    }

    private void saveTrip() {
        SharedPreferences.Editor e = prefs().edit();
        if (fromPlace == null) e.remove(KEY_FROM);
        else e.putString(KEY_FROM, GSON.toJson(fromPlace));
        if (to == null) e.remove(KEY_TO);
        else e.putString(KEY_TO, GSON.toJson(to));
        e.apply();
    }

    @Nullable
    private Place readPlace(String key) {
        String json = prefs().getString(key, null);
        if (json == null) return null;
        try {
            Place p = GSON.fromJson(json, Place.class);
            return p != null && p.hasCoords() ? p : null;
        } catch (JsonParseException e) {
            return null;
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putBoolean(STATE_SEARCHING_ORIGIN, searchingOrigin);
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
        location.stop();
        main.removeCallbacks(tick);
        super.onDestroyView();
    }

    /** 추천을 처음부터 다시 불러옵니다 — 로딩 화면 (팀 앱에서 즐겨찾기 등이 바뀐 뒤 부를 수 있음). */
    public void reload() {
        start(false, false);
    }

    /**
     * 팀 앱이 출발지 · 도착지를 정해 홈을 열 때 (예: 알림 · 다른 화면에서 돌아옴).
     *
     * @param from null 이면 "현재 위치"(GPS)
     */
    public void setTrip(@Nullable Place from, @NonNull Place to) {
        this.fromPlace = from;
        this.to = to;
        if (getView() != null) changedTrip();
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
        if (replayAt != null) {
            startReplay(id, keepList, byUser);
            return;
        }
        if (to == null) {
            findDestination(id, keepList, byUser);
            return;
        }
        if (keepList) setRefreshing(true);
        else showLoading(fromPlace == null ? R.string.home_locating : R.string.home_loading);
        findOrigin(id, keepList, byUser);
    }

    // ── 시연용: 지난 출근길 재생 (replay=…) ──

    /** 날짜 고르기 (GET /api/v1/replay/days, 최근 날부터) → 시각 고르기 */
    private void pickReplayDay() {
        ReplaySource replays = Home.replays();
        if (replays == null) return;
        Snackbar.make(requireView(), R.string.home_replay_loading_days, Snackbar.LENGTH_SHORT).show();
        Home.host().getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                loadReplayDays(replays, token);
            }

            @Override
            public void onError(String message) {
                loadReplayDays(replays, null);
            }
        });
    }

    private void loadReplayDays(ReplaySource replays, @Nullable String token) {
        EXECUTOR.execute(() -> {
            List<String> days = null;
            String error = null;
            try {
                days = replays.replayDays(token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;
            }
            final List<String> d = days;
            final String err = error;
            main.post(() -> {
                if (getView() == null) return;
                if (d == null) {
                    Snackbar.make(requireView(), err == null ? getString(R.string.home_error_default) : err,
                            Snackbar.LENGTH_LONG).show();
                } else if (d.isEmpty()) {
                    Snackbar.make(requireView(), R.string.home_replay_no_days, Snackbar.LENGTH_LONG).show();
                } else {
                    showReplayDays(d);
                }
            });
        });
    }

    private void showReplayDays(List<String> days) {
        List<String> latestFirst = new ArrayList<>(days);
        java.util.Collections.reverse(latestFirst);
        String[] labels = new String[latestFirst.size()];
        for (int i = 0; i < labels.length; i++) {
            String chip = TripFormat.dateChip(latestFirst.get(i) + "T00:00:00+09:00");
            labels[i] = chip == null ? latestFirst.get(i) : chip + "  " + latestFirst.get(i);
        }
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.home_replay_pick_day)
                .setItems(labels, (d, which) -> pickReplayTime(latestFirst.get(which)))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 시각 고르기 — 기본 07:40 (출근 시간) */
    private void pickReplayTime(String day) {
        new android.app.TimePickerDialog(requireContext(), (view, hour, minute) -> {
            replayAt = String.format(java.util.Locale.US, "%sT%02d:%02d", day, hour, minute);
            reload();
        }, 7, 40, true).show();
    }

    private void exitReplay() {
        replayAt = null;
        replayBar.setVisibility(View.GONE);
        tripInputs.setVisibility(View.VISIBLE);
        reload();
    }

    private void startReplay(int id, boolean keepList, boolean byUser) {
        final ReplaySource replays = Home.replays();
        final String at = replayAt;
        if (replays == null || at == null) return;
        tripInputs.setVisibility(View.GONE);
        if (keepList) setRefreshing(true);
        else showLoading(R.string.home_replay_loading);
        Home.host().getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!stale(id)) fetchReplay(id, replays, at, token, keepList, byUser);
            }

            @Override
            public void onError(String message) {
                if (!stale(id)) fetchReplay(id, replays, at, null, keepList, byUser);
            }
        });
    }

    private void fetchReplay(int id, ReplaySource replays, String at, @Nullable String token, boolean keepList,
                             boolean byUser) {
        EXECUTOR.execute(() -> {
            Recommendations result = null;
            String error = null;
            try {
                result = replays.replay(at, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;
            }
            final Recommendations r = result;
            final String err = error;
            main.post(() -> {
                if (stale(id)) return;
                if (r != null) {
                    shownFrom = null;   // 재생은 집 → 국민대 기록 — 카드는 예전 방식으로 넘김
                    shownTo = null;
                    show(r);
                } else {
                    String message = err != null ? err : getString(R.string.home_error_default);
                    if (keepList && loaded) failed(message, true, byUser);
                    else showState(false, message, getString(R.string.home_replay_exit), v -> exitReplay());
                }
            });
        });
    }

    /** 재생 중이면 재생 시각 · replay.message · note */
    private void bindReplay(Recommendations r) {
        boolean on = replayAt != null && r.replay != null;
        replayBar.setVisibility(on ? View.VISIBLE : View.GONE);
        tripInputs.setVisibility(replayAt != null ? View.GONE : View.VISIBLE);
        if (!on) return;
        String date = TripFormat.dateChip(r.replay.at != null ? r.replay.at : r.generatedAt);
        String time = TripFormat.time(r.replay.at != null ? r.replay.at : r.generatedAt);
        String when = (date == null ? "" : date) + (time == null ? "" : " " + time);
        replayTitle.setText(getString(R.string.home_replay_title, when.trim()));
        setTextOrGone(replayMessage, r.replay.message);
        setTextOrGone(replayNote, r.replay.note);
    }

    /** 이 기기에 도착지가 없으면 D 의 destination · last_trip.to, 그래도 없으면 검색 안내 */
    private void findDestination(int id, boolean keepList, boolean byUser) {
        showLoading(R.string.home_loading);
        Home.host().loadLastDestination(new Callback<Place>() {
            @Override
            public void onSuccess(Place result) {
                if (stale(id)) return;
                if (result != null && result.hasCoords()) {
                    to = result;
                    saveTrip();
                    bindTrip();
                    start(keepList, byUser);
                } else {
                    showNeedDestination();
                }
            }

            @Override
            public void onError(String message) {
                if (!stale(id)) showNeedDestination();
            }
        });
    }

    /** 출발지 — 검색한 곳이면 그대로, "현재 위치"면 GPS (권한이 없으면 먼저 물어봄) */
    private void findOrigin(int id, boolean keepList, boolean byUser) {
        if (fromPlace != null) {
            withToken(id, fromPlace, keepList, byUser);
            return;
        }
        if (!HomeLocation.hasPermission(requireContext())) {
            pendingKeepList = keepList;
            pendingByUser = byUser;
            permissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
            return;
        }
        location.request(new HomeLocation.Listener() {
            @Override
            public void onLocation(@NonNull Location l) {
                if (stale(id)) return;
                gpsFix = new Place(Place.CURRENT_LOCATION, l.getLatitude(), l.getLongitude());
                if (!keepList) stateMessage.setText(R.string.home_loading);
                withToken(id, gpsFix, keepList, byUser);
            }

            @Override
            public void onFailure(int message) {
                if (stale(id)) return;
                // 새로고침인데 위치를 다시 못 받으면 마지막 GPS 좌표로
                if (keepList && gpsFix != null) withToken(id, gpsFix, true, byUser);
                else locationFailed(getString(message), keepList, byUser);
            }
        });
    }

    /** 권한 창이 닫힘 — 허용하면 이어서, 거부하면 출발지 검색을 엶 */
    private void onPermissionResult() {
        if (getView() == null) return;
        if (HomeLocation.hasPermission(requireContext())) {
            start(pendingKeepList, pendingByUser);
            return;
        }
        locationFailed(getString(R.string.home_location_denied), false, false);
        openSearch(true);
    }

    /** 위치를 못 받음 — 목록이 있으면 그대로 두고 알림, 없으면 '출발지 검색' 버튼 */
    private void locationFailed(String message, boolean keepList, boolean byUser) {
        if (keepList && loaded) {
            failed(message, true, byUser);
            return;
        }
        showState(false, message, getString(R.string.home_search_origin), v -> openSearch(true));
    }

    private void withToken(int id, Place from, boolean keepList, boolean byUser) {
        final Place dest = to;
        if (dest == null) return;
        Home.host().getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!stale(id)) fetch(id, from, dest, token, keepList, byUser, false);
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

    /**
     * @param retried 401 뒤 토큰을 새로 받아 다시 보낸 요청이면 true (13주차 — 한 번만 다시 시도)
     */
    private void fetch(int id, Place from, Place dest, @Nullable String token, boolean keepList, boolean byUser,
                       boolean retried) {
        final RecommendationSource source = Home.source();
        // 검색할 때마다 last_trip 저장 (D) — 자동 새로고침 · 401 재시도는 빼고
        if (!retried && (!keepList || byUser)) Home.host().saveLastTrip(from, dest);
        EXECUTOR.execute(() -> {
            Recommendations result = null;
            SourceException error = null;
            try {
                result = source.load(from, dest, token);
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
                    shownFrom = from;
                    shownTo = dest;
                    show(r);
                } else if (err != null && err.isUnauthorized()) {
                    unauthorized(id, from, dest, keepList, byUser, retried, err.getMessage());
                } else {
                    String message = err != null && err.getMessage() != null ? err.getMessage()
                            : getString(R.string.home_error_default);
                    failed(message, keepList, byUser);
                }
            });
        });
    }

    /** 13주차: 서버 401 — 토큰을 새로 받아 한 번 더. 그래도 401 이면 다시 로그인 화면 */
    private void unauthorized(int id, Place from, Place dest, boolean keepList, boolean byUser, boolean retried,
                              String message) {
        if (retried) {
            showSessionExpired();
            return;
        }
        Home.host().getIdToken(true, new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (!stale(id)) fetch(id, from, dest, token, keepList, byUser, true);
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

        // 칩 3개: 날짜 · 평일 출퇴근(day.kind) · 지금 HH:MM (generated_at + 받은 뒤 지난 분)
        setTextOrGone(dateChip, TripFormat.dateChip(r.generatedAt));
        setTextOrGone(dayLabel, TripFormat.dayChip(r.day));
        showNow();
        setTextOrGone(summary, r.recommendation == null ? null : r.recommendation.message);
        bindReplay(r);

        List<Option> options = r.rankingOrEmpty();
        ranking = new ArrayList<>(options);
        first = options.isEmpty() ? null : options.get(0);
        // 새로고침해도 보던 자리는 그대로 (정렬을 바꿀 때만 맨 위로)
        adapter.submit(sort.apply(ranking), first, 0);
        departureAlarm.setEnabled(first != null && replayAt == null);

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
        if (!refreshing && replayAt == null
                && LiveTime.shouldAutoRefresh(receivedAt, lastAttemptAt, SystemClock.elapsedRealtime())) {
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
        String time = LiveTime.plusMinutes(TripFormat.time(current.generatedAt), replayAt == null ? elapsedMin() : 0);
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
        if (replayAt != null && !refreshing) text = null;   // 재생 중에는 갱신 시각이 의미 없음
        // route_source=saved_nearby: ODsay 대신 근처 저장 경로로 계산
        // (갱신 시각이 없어도 이 표시는 항상 보이게)
        if (current != null && current.isSavedNearbyRoute() && !refreshing) {
            String label = getString(R.string.home_saved_nearby);
            text = text == null ? label : text + " · " + label;
        }
        updated.setTextColor(ContextCompat.getColor(requireContext(), color));
        setTextOrGone(updated, text);
    }

    // ── 상태 화면 ──

    private void showLoading(@StringRes int message) {
        showState(true, getString(message), null, null);
    }

    /** 도착지가 아직 없음 — 고정 기본값 없이 검색부터 */
    private void showNeedDestination() {
        showState(false, getString(R.string.home_need_destination), getString(R.string.home_search_destination),
                v -> openSearch(false));
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
        // 이전 추천 요약 · '지금' 칩은 지움 (도착지를 바꾼 뒤 실패하면 예전 경로 문장이 남지 않게)
        setTextOrGone(summary, null);
        setTextOrGone(nowChip, null);
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
