package kr.ac.kookmin.seatforecast.home.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.Home;
import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.R;
import kr.ac.kookmin.seatforecast.home.data.PlaceSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;

/**
 * 출발지 · 도착지 검색 — {@code GET /api/v1/places?q=} 결과 목록에서 고르면 결과로 돌려줍니다.
 * 입력 전에는 "현재 위치"(출발지일 때) · 즐겨찾는 곳(HomeHost.loadFavorites)을 보여 줍니다.
 * 결과: {@link #resultPlace(Intent)} (좌표 · 이름) 또는 {@link #isCurrentLocation(Intent)} (GPS 를 쓰라는 뜻).
 */
public class PlaceSearchActivity extends AppCompatActivity {

    static final String EXTRA_ORIGIN = "home_search_origin";
    static final String EXTRA_CURRENT = "home_search_current";
    static final String EXTRA_NAME = "home_search_name";
    static final String EXTRA_ADDRESS = "home_search_address";
    static final String EXTRA_LAT = "home_search_lat";
    static final String EXTRA_LNG = "home_search_lng";

    /** 입력을 멈추고 이만큼 지나면 검색 (글자마다 서버를 부르지 않도록) */
    static final long DEBOUNCE_MS = 350;

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();

    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable searchLater = this::search;
    private final List<Row> favorites = new ArrayList<>();
    private boolean origin;
    private int requestId;

    private EditText input;
    private View progress;
    private TextView message;
    private RowAdapter adapter;

    /** @param origin true = 출발지 검색 (맨 위에 "현재 위치"), false = 도착지 */
    public static Intent newIntent(Context context, boolean origin) {
        return new Intent(context, PlaceSearchActivity.class).putExtra(EXTRA_ORIGIN, origin);
    }

    /** 고른 곳 — "현재 위치" 를 골랐거나 결과가 없으면 null */
    @Nullable
    public static Place resultPlace(@Nullable Intent data) {
        if (data == null || data.getBooleanExtra(EXTRA_CURRENT, false) || !data.hasExtra(EXTRA_LAT)) return null;
        return new Place(data.getStringExtra(EXTRA_NAME), data.getStringExtra(EXTRA_ADDRESS),
                data.getDoubleExtra(EXTRA_LAT, Double.NaN), data.getDoubleExtra(EXTRA_LNG, Double.NaN));
    }

    /** 출발지로 "현재 위치"(GPS)를 골랐는지 */
    public static boolean isCurrentLocation(@Nullable Intent data) {
        return data != null && data.getBooleanExtra(EXTRA_CURRENT, false);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home_activity_place_search);
        origin = getIntent().getBooleanExtra(EXTRA_ORIGIN, false);

        ((TextView) findViewById(R.id.home_search_title))
                .setText(origin ? R.string.home_search_title_from : R.string.home_search_title_to);
        findViewById(R.id.home_search_back).setOnClickListener(v -> finish());
        input = findViewById(R.id.home_search_input);
        progress = findViewById(R.id.home_search_progress);
        message = findViewById(R.id.home_search_message);
        RecyclerView list = findViewById(R.id.home_search_list);
        adapter = new RowAdapter(this::choose);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                main.removeCallbacks(searchLater);
                if (s.toString().trim().isEmpty()) showStart();
                else main.postDelayed(searchLater, DEBOUNCE_MS);
            }
        });
        input.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEARCH) return false;
            main.removeCallbacks(searchLater);
            search();
            return true;
        });
        input.requestFocus();

        showStart();
        Home.host().loadFavorites(new Callback<List<Place>>() {
            @Override
            public void onSuccess(List<Place> result) {
                favorites.clear();
                if (result != null) {
                    for (Place p : result) {
                        if (p != null && p.hasCoords()) favorites.add(Row.place(p, R.drawable.home_ic_star_muted));
                    }
                }
                if (!isFinishing() && query().isEmpty()) showStart();
            }

            @Override
            public void onError(String m) {
                // 즐겨찾기를 못 읽어도 검색은 됨
            }
        });
    }

    private String query() {
        return input.getText().toString().trim();
    }

    /** 입력 전: 현재 위치(출발지) · 즐겨찾는 곳 */
    private void showStart() {
        requestId++;
        progress.setVisibility(View.GONE);
        List<Row> rows = new ArrayList<>();
        if (origin) rows.add(Row.current(getString(R.string.home_search_current_sub)));
        if (!favorites.isEmpty()) {
            rows.add(Row.header(getString(R.string.home_search_favorites)));
            rows.addAll(favorites);
        }
        adapter.submit(rows);
        showMessage(favorites.isEmpty() ? getString(R.string.home_search_start) : null);
    }

    private void search() {
        String q = query();
        if (q.isEmpty()) {
            showStart();
            return;
        }
        PlaceSource places = Home.places();
        if (places == null) {
            showMessage(getString(R.string.home_search_unavailable));
            return;
        }
        final int id = ++requestId;
        progress.setVisibility(View.VISIBLE);
        Home.host().getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                if (id == requestId) run(id, places, q, token);
            }

            @Override
            public void onError(String m) {
                if (id == requestId) run(id, places, q, null);   // 토큰 없이 (서버가 막으면 그 문장이 나옴)
            }
        });
    }

    private void run(int id, PlaceSource places, String q, @Nullable String token) {
        EXECUTOR.execute(() -> {
            List<Place> result = null;
            String error = null;
            try {
                result = places.search(q, token);
            } catch (SourceException e) {
                error = e.getMessage();
            } catch (RuntimeException e) {
                error = getString(R.string.home_search_failed);
            }
            final List<Place> r = result;
            final String err = error;
            main.post(() -> {
                if (id != requestId || isFinishing()) return;
                progress.setVisibility(View.GONE);
                if (r == null) {
                    adapter.submit(new ArrayList<>());
                    showMessage(err == null ? getString(R.string.home_search_failed) : err);
                    return;
                }
                List<Row> rows = new ArrayList<>();
                for (Place p : r) rows.add(Row.place(p, R.drawable.home_ic_place));
                adapter.submit(rows);
                showMessage(rows.isEmpty() ? getString(R.string.home_search_empty, q) : null);
            });
        });
    }

    private void showMessage(@Nullable String text) {
        message.setText(text);
        message.setVisibility(text == null ? View.GONE : View.VISIBLE);
    }

    private void choose(Row row) {
        Intent data = new Intent();
        if (row.current) {
            data.putExtra(EXTRA_CURRENT, true);
        } else if (row.place != null) {
            data.putExtra(EXTRA_NAME, row.place.name)
                    .putExtra(EXTRA_ADDRESS, row.place.address)
                    .putExtra(EXTRA_LAT, row.place.lat)
                    .putExtra(EXTRA_LNG, row.place.lng);
        } else {
            return;
        }
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        requestId++;
        main.removeCallbacks(searchLater);
        super.onDestroy();
    }

    /** 목록 한 줄: 현재 위치 · 머리글 · 장소 */
    static final class Row {
        @Nullable final Place place;
        final boolean current;
        @Nullable final String header;
        @Nullable final String sub;
        @DrawableRes final int icon;

        private Row(@Nullable Place place, boolean current, @Nullable String header, @Nullable String sub, int icon) {
            this.place = place;
            this.current = current;
            this.header = header;
            this.sub = sub;
            this.icon = icon;
        }

        static Row current(String sub) {
            return new Row(null, true, null, sub, R.drawable.home_ic_my_location);
        }

        static Row header(String text) {
            return new Row(null, false, text, null, 0);
        }

        static Row place(Place p, @DrawableRes int icon) {
            return new Row(p, false, null, p.address, icon);
        }
    }

    interface OnRow {
        void onRow(Row row);
    }

    static final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
        private final List<Row> rows = new ArrayList<>();
        private final OnRow onRow;

        RowAdapter(OnRow onRow) {
            this.onRow = onRow;
        }

        void submit(List<Row> next) {
            rows.clear();
            rows.addAll(next);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.home_item_place, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            Row r = rows.get(position);
            Context c = h.itemView.getContext();
            if (r.header != null) {
                h.icon.setVisibility(View.GONE);
                h.name.setText(r.header);
                h.name.setTextSize(13);
                h.name.setTextColor(c.getColor(R.color.home_muted));
                h.address.setVisibility(View.GONE);
                h.itemView.setOnClickListener(null);
                h.itemView.setClickable(false);
                return;
            }
            h.icon.setVisibility(View.VISIBLE);
            h.icon.setImageResource(r.icon);
            h.name.setTextSize(16);
            h.name.setTextColor(c.getColor(R.color.home_text));
            h.name.setText(r.current ? c.getString(R.string.home_current_location) : r.place.name);
            h.address.setText(r.sub);
            h.address.setVisibility(r.sub == null || r.sub.isEmpty() ? View.GONE : View.VISIBLE);
            h.itemView.setOnClickListener(v -> onRow.onRow(r));
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        static final class Holder extends RecyclerView.ViewHolder {
            final ImageView icon;
            final TextView name;
            final TextView address;

            Holder(View v) {
                super(v);
                icon = v.findViewById(R.id.home_place_icon);
                name = v.findViewById(R.id.home_place_name);
                address = v.findViewById(R.id.home_place_address);
            }
        }
    }
}
