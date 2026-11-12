package kr.ac.kookmin.seatforecast.account.ui;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;

/** 마이페이지 > 출발 알림 설정: 출발 알림 · 돌발 알림 · 몇 분 전 (settings.json 의 notify) */
public class NotifySettingsActivity extends AppCompatActivity {

    private static final List<Integer> MINUTE_CHOICES = Arrays.asList(0, 5, 10, 15, 20, 30, 45, 60);

    private final UserRepository users = new UserRepository();

    private String uid;
    private UserSettings settings = UserSettings.defaults();

    private MaterialSwitch notifyDeparture;
    private MaterialSwitch notifyIncident;
    private Spinner minutesBefore;
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
        setContentView(R.layout.account_activity_notify_settings);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        notifyDeparture = findViewById(R.id.account_notify_departure);
        notifyIncident = findViewById(R.id.account_notify_incident);
        minutesBefore = findViewById(R.id.account_minutes_before);
        save = findViewById(R.id.account_save);
        progress = findViewById(R.id.account_progress);
        ((TextView) findViewById(R.id.account_notify_hint)).setText(R.string.account_notify_hint_text);

        List<String> labels = new ArrayList<>();
        for (int m : MINUTE_CHOICES) labels.add(getString(R.string.account_minutes_format, m));
        minutesBefore.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels));
        notifyDeparture.setOnCheckedChangeListener((b, checked) -> minutesBefore.setEnabled(checked));
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
        notifyDeparture.setChecked(s.notifyDeparture);
        notifyIncident.setChecked(s.notifyIncident);
        int index = MINUTE_CHOICES.indexOf(s.minutesBefore);
        minutesBefore.setSelection(index >= 0 ? index : MINUTE_CHOICES.indexOf(10));
        minutesBefore.setEnabled(s.notifyDeparture);
    }

    private void saveSettings() {
        UserSettings s = UserSettings.fromMap(settings.toMap());   // 경로 설정 유지
        s.notifyDeparture = notifyDeparture.isChecked();
        s.notifyIncident = notifyIncident.isChecked();
        s.minutesBefore = MINUTE_CHOICES.get(minutesBefore.getSelectedItemPosition());

        setLoading(true);
        users.saveSettings(uid, s, new Callback<Void>() {
            @Override
            public void onSuccess(Void result) {
                setLoading(false);
                Toast.makeText(NotifySettingsActivity.this, R.string.account_saved, Toast.LENGTH_SHORT).show();
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
