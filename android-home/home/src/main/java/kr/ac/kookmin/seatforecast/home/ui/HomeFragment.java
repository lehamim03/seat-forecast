package kr.ac.kookmin.seatforecast.home.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

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
 */
public class HomeFragment extends Fragment {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    /** 새 요청이 시작되면 이전 요청 결과는 버림 */
    private int requestId;
    private boolean loaded;

    private TextView usualStop;
    private TextView dayLabel;
    private TextView summary;
    private RecyclerView list;
    private View state;
    private View progress;
    private TextView stateMessage;
    private MaterialButton stateAction;
    private MaterialButton departureAlarm;
    private OptionAdapter adapter;

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
        dayLabel = v.findViewById(R.id.home_day_label);
        summary = v.findViewById(R.id.home_summary);
        list = v.findViewById(R.id.home_list);
        state = v.findViewById(R.id.home_state);
        progress = v.findViewById(R.id.home_progress);
        stateMessage = v.findViewById(R.id.home_state_message);
        stateAction = v.findViewById(R.id.home_state_action);
        departureAlarm = v.findViewById(R.id.home_departure_alarm);

        adapter = new OptionAdapter(o -> Home.host().onOptionClicked(requireContext(), o));
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        list.setAdapter(adapter);
        departureAlarm.setOnClickListener(b -> {
            if (first != null) Home.host().onDepartureAlarmClicked(requireContext(), first);
        });
        loaded = false;
    }

    @Override
    public void onStart() {
        super.onStart();
        // 처음 열 때, 그리고 로그인 화면에서 돌아왔을 때
        if (!loaded) reload();
    }

    @Override
    public void onDestroyView() {
        requestId++;   // 진행 중인 요청 결과 버림
        super.onDestroyView();
    }

    /** 추천을 다시 불러옵니다 (팀 앱에서 설정이 바뀐 뒤 부를 수 있음). */
    public void reload() {
        if (getView() == null) return;
        final int id = ++requestId;
        final HomeHost host = Home.host();
        if (!host.isSignedIn()) {
            showSignedOut();
            return;
        }
        showLoading();
        host.loadUsualTrip(new Callback<UsualTrip>() {
            @Override
            public void onSuccess(UsualTrip trip) {
                if (stale(id)) return;
                host.getIdToken(new Callback<String>() {
                    @Override
                    public void onSuccess(String token) {
                        if (!stale(id)) fetch(id, trip, token);
                    }

                    @Override
                    public void onError(String message) {
                        if (!stale(id)) showError(message);
                    }
                });
            }

            @Override
            public void onError(String message) {
                if (!stale(id)) showError(message);
            }
        });
    }

    private boolean stale(int id) {
        return id != requestId || getView() == null;
    }

    private void fetch(int id, UsualTrip trip, @Nullable String token) {
        final RecommendationSource source = Home.source();
        EXECUTOR.execute(() -> {
            Recommendations result = null;
            String error = null;
            try {
                result = source.load(trip.homeStopId, trip.route, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = null;   // 아래에서 기본 문구
            }
            final Recommendations r = result;
            final String message = error;
            main.post(() -> {
                if (stale(id)) return;
                if (r != null) show(r);
                else showError(message != null ? message : getString(R.string.home_error_default));
            });
        });
    }

    private void show(Recommendations r) {
        loaded = true;
        if (r.homeStop != null && r.homeStop.name != null) {
            usualStop.setText(getString(R.string.home_usual_stop, r.homeStop.name));
        }
        setTextOrGone(dayLabel, r.day == null ? null : r.day.label);
        setTextOrGone(summary, r.recommendation == null ? null : r.recommendation.message);

        List<Option> options = r.rankingOrEmpty();
        adapter.submit(options);
        first = options.isEmpty() ? null : options.get(0);
        departureAlarm.setEnabled(first != null);

        if (options.isEmpty()) {
            showState(false, getString(R.string.home_empty), null, null);
        } else {
            state.setVisibility(View.GONE);
            list.setVisibility(View.VISIBLE);
        }
    }

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
    }

    private static void setTextOrGone(TextView view, @Nullable String text) {
        view.setText(text);
        view.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }
}
