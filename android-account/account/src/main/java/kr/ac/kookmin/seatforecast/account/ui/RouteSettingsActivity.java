package kr.ac.kookmin.seatforecast.account.ui;

import android.app.TimePickerDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.Stops;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;

/** 마이페이지 > 즐겨찾기 경로: 평소 정류장 · 노선 · 함께 비교할 정류장 · 도착 목표 시각 */
public class RouteSettingsActivity extends AppCompatActivity {

    private final UserRepository users = new UserRepository();

    private String uid;
    private UserSettings settings = UserSettings.defaults();

    private Spinner homeStop;
    private Spinner myRoute;
    private final List<CheckBox> candidateChecks = new ArrayList<>();
    private MaterialButton arriveBy;
    private MaterialButton save;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = Account.currentUid();
        if (uid == null) {
            finish();
            return;
        }
        setContentView(R.layout.account_activity_route_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        homeStop = findViewById(R.id.account_home_stop);
        myRoute = findViewById(R.id.account_my_route);
        arriveBy = findViewById(R.id.account_arrive_by);
        save = findViewById(R.id.account_save);
        progress = findViewById(R.id.account_progress);
        ((TextView) findViewById(R.id.account_destination)).setText(Stops.DESTINATION_NAME);

        homeStop.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, Stops.ALL));
        homeStop.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                fillRoutes(Stops.ALL.get(position).id, selectedRoute());
                lockHomeCandidate(position);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        LinearLayout box = findViewById(R.id.account_candidate_stops);
        for (Stops.Stop stop : Stops.ALL) {
            CheckBox c = new CheckBox(this);
            c.setText(getString(R.string.account_candidate_label, stop.label(), stop.walkMin));
            c.setTag(stop.id);
            c.setButtonTintList(getColorStateList(R.color.account_primary));
            box.addView(c);
            candidateChecks.add(c);
        }

        arriveBy.setOnClickListener(v -> pickTime());
        save.setOnClickListener(v -> saveSettings());

        bind(settings);
        setLoading(true);
        users.loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                setLoading(false);
                settings = s;
                bind(s);
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(save, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void bind(UserSettings s) {
        int stopIndex = 0;
        for (int i = 0; i < Stops.ALL.size(); i++) {
            if (Stops.ALL.get(i).id.equals(s.homeStopId)) stopIndex = i;
        }
        for (CheckBox c : candidateChecks) c.setChecked(s.candidateStopIds.contains((String) c.getTag()));
        homeStop.setSelection(stopIndex, false);
        fillRoutes(s.homeStopId, s.myRoute);
        lockHomeCandidate(stopIndex);
        arriveBy.setText(s.arriveBy);
    }

    /** 평소 정류장은 항상 비교 대상 */
    private void lockHomeCandidate(int homeIndex) {
        for (int i = 0; i < candidateChecks.size(); i++) {
            CheckBox c = candidateChecks.get(i);
            if (i == homeIndex) c.setChecked(true);
            c.setEnabled(i != homeIndex);
        }
    }

    private void fillRoutes(String stopId, String wanted) {
        List<String> routes = Stops.routesAt(stopId);
        myRoute.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, routes));
        if (wanted != null && routes.contains(wanted)) myRoute.setSelection(routes.indexOf(wanted));
    }

    private String selectedRoute() {
        Object r = myRoute.getSelectedItem();
        return r == null ? null : r.toString();
    }

    private void pickTime() {
        String[] hm = arriveBy.getText().toString().split(":");
        int h = 9;
        int m = 0;
        if (hm.length == 2) {
            try {
                h = Integer.parseInt(hm[0]);
                m = Integer.parseInt(hm[1]);
            } catch (NumberFormatException ignored) {
            }
        }
        new TimePickerDialog(this, (view, hour, minute) ->
                arriveBy.setText(String.format(Locale.US, "%02d:%02d", hour, minute)), h, m, true).show();
    }

    private void saveSettings() {
        UserSettings s = UserSettings.fromMap(settings.toMap());   // 알림 설정 등 이 화면에 없는 값 유지
        s.homeStopId = ((Stops.Stop) homeStop.getSelectedItem()).id;
        s.myRoute = selectedRoute();
        s.candidateStopIds = new ArrayList<>();
        for (CheckBox c : candidateChecks) {
            if (c.isChecked()) s.candidateStopIds.add((String) c.getTag());
        }
        s.arriveBy = arriveBy.getText().toString();

        setLoading(true);
        users.saveSettings(uid, s, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                Toast.makeText(RouteSettingsActivity.this, R.string.account_saved, Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(save, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        save.setEnabled(!loading);
    }
}
