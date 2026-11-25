package kr.ac.kookmin.seatforecast.account.ui;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Locale;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.Place;
import kr.ac.kookmin.seatforecast.account.R;
import kr.ac.kookmin.seatforecast.account.data.PlaceSearch;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;
import kr.ac.kookmin.seatforecast.account.data.Validators;

/**
 * 마이페이지 > 즐겨찾는 곳 — 이름(집 · 학교 등) + 장소 검색(GET /api/v1/places) 좌표로 추가 · 삭제,
 * 기본 도착지 고르기, 도착 목표 시각. Firestore users/{uid}: favorites · destination · arrive_by.
 * (예전 '평소 정류장 · 노선' 설정 화면을 대신함)
 */
public class FavoritesActivity extends AppCompatActivity {

    private final UserRepository users = new UserRepository();
    private String uid;
    private UserSettings settings;

    private LinearLayout list;
    private TextView empty;
    private TextInputEditText arriveBy;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        uid = Account.currentUid();
        if (uid == null) {
            Toast.makeText(this, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        setContentView(R.layout.account_activity_favorites);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        list = findViewById(R.id.account_favorite_list);
        empty = findViewById(R.id.account_favorite_empty);
        arriveBy = findViewById(R.id.account_arrive_by);
        progress = findViewById(R.id.account_progress);
        findViewById(R.id.account_favorite_add).setOnClickListener(v -> askName());
        findViewById(R.id.account_save).setOnClickListener(v -> saveArriveBy());
        load();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private void load() {
        setLoading(true);
        users.loadSettings(uid, new Callback<UserSettings>() {
            @Override
            public void onSuccess(UserSettings s) {
                setLoading(false);
                settings = s;
                arriveBy.setText(s.arriveBy);
                bind();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(list, message, Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void bind() {
        list.removeAllViews();
        empty.setVisibility(settings.favorites.isEmpty() ? View.VISIBLE : View.GONE);
        LayoutInflater inflater = LayoutInflater.from(this);
        for (Place p : settings.favorites) {
            View row = inflater.inflate(R.layout.account_item_favorite_row, list, false);
            boolean isDefault = p.samePlace(settings.destination);
            ((TextView) row.findViewById(R.id.account_favorite_name)).setText(
                    isDefault ? getString(R.string.account_favorite_default_name, p.name) : p.name);
            ((TextView) row.findViewById(R.id.account_favorite_sub)).setText(
                    String.format(Locale.US, "%.5f, %.5f", p.lat, p.lng));
            View makeDefault = row.findViewById(R.id.account_favorite_default);
            makeDefault.setVisibility(isDefault ? View.GONE : View.VISIBLE);
            makeDefault.setOnClickListener(v -> {
                settings.destination = new Place(p.name, p.lat, p.lng);
                save(R.string.account_favorite_default_saved);
            });
            row.findViewById(R.id.account_favorite_delete).setOnClickListener(v -> confirmDelete(p));
            list.addView(row);
        }
    }

    /** 1단계: 이름 · 검색어 (이름을 비우면 검색 결과 이름을 씀) */
    private void askName() {
        if (settings == null) return;
        if (settings.favorites.size() >= UserSettings.MAX_FAVORITES) {
            Snackbar.make(list, getString(R.string.account_favorite_full, UserSettings.MAX_FAVORITES), Snackbar.LENGTH_LONG).show();
            return;
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 2, pad, 0);
        EditText name = new EditText(this);
        name.setHint(R.string.account_favorite_name_hint);
        name.setInputType(InputType.TYPE_CLASS_TEXT);
        EditText query = new EditText(this);
        query.setHint(R.string.account_favorite_query_hint);
        query.setInputType(InputType.TYPE_CLASS_TEXT);
        box.addView(name);
        box.addView(query);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.account_favorite_add)
                .setView(box)
                .setPositiveButton(R.string.account_favorite_search, (d, which) -> {
                    String q = query.getText().toString().trim();
                    if (q.isEmpty()) q = name.getText().toString().trim();
                    if (q.isEmpty()) {
                        Snackbar.make(list, R.string.account_favorite_need_query, Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    search(name.getText().toString().trim(), q);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** 2단계: 검색 결과에서 고르기 */
    private void search(String name, String query) {
        setLoading(true);
        new PlaceSearch().search(query, new Callback<List<Place>>() {
            @Override
            public void onSuccess(List<Place> places) {
                setLoading(false);
                if (places.isEmpty()) {
                    Snackbar.make(list, getString(R.string.account_favorite_no_result, query), Snackbar.LENGTH_LONG).show();
                    return;
                }
                String[] labels = new String[places.size()];
                for (int i = 0; i < labels.length; i++) {
                    Place p = places.get(i);
                    labels[i] = p.address == null || p.address.isEmpty() ? p.name : p.name + "\n" + p.address;
                }
                new MaterialAlertDialogBuilder(FavoritesActivity.this)
                        .setTitle(getString(R.string.account_favorite_pick, query))
                        .setItems(labels, (d, which) -> add(name, places.get(which)))
                        .setNegativeButton(android.R.string.cancel, null)
                        .show();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(list, message == null ? getString(R.string.account_favorite_search_failed) : message,
                        Snackbar.LENGTH_LONG).show();
            }
        });
    }

    private void add(String name, Place found) {
        Place p = new Place(name.isEmpty() ? found.name : name, found.lat, found.lng);
        settings.favorites.add(p);
        if (settings.destination == null) settings.destination = p;   // 첫 즐겨찾기는 기본 도착지로
        save(R.string.account_favorite_added);
    }

    private void confirmDelete(Place p) {
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.account_favorite_delete_confirm, p.name))
                .setPositiveButton(R.string.account_delete, (d, which) -> {
                    settings.favorites.remove(p);
                    if (p.samePlace(settings.destination)) settings.destination = null;
                    save(R.string.account_favorite_deleted);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void saveArriveBy() {
        String time = arriveBy.getText() == null ? "" : arriveBy.getText().toString().trim();
        if (!Validators.isTime(time)) {
            arriveBy.setError(getString(R.string.account_arrive_by_error));
            return;
        }
        settings.arriveBy = time;
        save(R.string.account_saved);
    }

    private void save(int doneMessage) {
        setLoading(true);
        users.saveSettings(uid, settings, new Callback<Void>() {
            @Override
            public void onSuccess(Void v) {
                setLoading(false);
                Toast.makeText(FavoritesActivity.this, doneMessage, Toast.LENGTH_SHORT).show();
                bind();
            }

            @Override
            public void onError(String message) {
                setLoading(false);
                Snackbar.make(list, message, Snackbar.LENGTH_LONG).show();
                load();   // 저장 못 한 변경은 되돌림
            }
        });
    }

    private void setLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }
}
