package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;
import org.json.JSONObject;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

/**
 * C의 경로 목록 (출발지 → 도착지). 다른 모듈은 newIntent로 노선 · 출발지 · 도착지를 지정한다.
 * 모양은 팀 앱 A 홈 · B 대안과 같음 (흰 상단 바 · 노선 칩 · 흰 카드 · 빨간 선택 테두리).
 */
public final class RouteChoicesActivity extends Activity {
    private RouteData data;
    private RouteUi ui;
    private RoutePreview preview;
    private LinearLayout cards;
    private ChipGroup routeButtons;
    private TextView subtitle;
    private String route;
    private int choiceIndex;

    /** 출발지 · 도착지는 지도 탭의 마지막 값 (MapServer.setLastTrip) */
    public static Intent newIntent(Context context, String route) {
        return new Intent(context, RouteChoicesActivity.class).putExtra("route", route);
    }

    /** @param from · to 경로를 계산할 출발지 · 도착지 (extra 6개, TripExtras) */
    public static Intent newIntent(Context context, String route, Place from, Place to) {
        return TripExtras.put(newIntent(context, route), from, to);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new RouteUi(this);
        LinearLayout root = ui.column();
        root.setBackgroundColor(RouteUi.BACKGROUND);
        setContentView(root);
        RouteUi.insets(this, root);
        LinearLayout header = ui.row();
        header.setBackgroundColor(RouteUi.SURFACE);
        header.setMinimumHeight(ui.dp(56));
        header.setPadding(ui.dp(4), 0, ui.dp(16), 0);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.map_ic_back);
        back.setBackgroundResource(android.R.color.transparent);
        back.setContentDescription("지도로 돌아가기");
        back.setOnClickListener(v -> finish());
        header.addView(back, new LinearLayout.LayoutParams(ui.dp(48), ui.dp(48)));
        TextView title = ui.text("경로 여러 개", 18, RouteUi.INK, true);
        title.setPadding(ui.dp(4), 0, 0, 0);
        header.addView(title);
        root.addView(header);
        TextView loading = ui.text(MapServer.isLive() ? "서버에서 경로를 불러오는 중…" : "경로를 불러오는 중…",
                15, RouteUi.MUTED, false);
        loading.setPadding(ui.dp(16), ui.dp(24), ui.dp(16), 0);
        root.addView(loading);
        // 13주차: 서버(또는 예시 JSON)에서 백그라운드로 읽은 뒤 화면을 만든다
        Bundle extras = getIntent().getExtras();
        Place from = TripExtras.from(extras);
        Place to = TripExtras.to(extras);
        if (to == null) {
            to = MapServer.lastTo();
            from = MapServer.lastFrom();
        }
        if (to == null || from == null || !from.hasCoords()) {
            loading.setText("출발지 · 도착지가 없어요. 지도 화면에서 열어 주세요.");
            return;
        }
        RouteData.load(this, from, to, loaded -> {
            root.removeView(loading);
            data = loaded;
            show(root, state);
        }, error -> {
            // 503 route_unavailable 등 서버 문장 그대로 + 다시 시도
            loading.setText(error == null || error.getMessage() == null
                    ? "경로 데이터를 불러오지 못했습니다." : error.getMessage());
            loading.setTextColor(RouteUi.BODY);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.setMargins(ui.dp(16), ui.dp(12), ui.dp(16), 0);
            root.addView(ui.primaryButton(getString(R.string.map_retry), this::recreate), params);
        });
    }

    private void show(LinearLayout root, Bundle state) {
        if (data.choices.isEmpty()) {
            TextView empty = ui.text("표시할 경로가 없습니다.", 15, RouteUi.MUTED, false);
            empty.setPadding(ui.dp(16), ui.dp(24), ui.dp(16), 0);
            root.addView(empty);
            return;
        }
        route = state == null ? getIntent().getStringExtra("route") : state.getString("route");
        choiceIndex = state == null ? 0 : state.getInt("choice");
        if (!data.choices.containsKey(route)) route = data.choices.keySet().iterator().next();
        choiceIndex = Math.max(0, Math.min(choiceIndex, data.choices.get(route).size() - 1));

        HorizontalScrollView routes = new HorizontalScrollView(this);
        routes.setHorizontalScrollBarEnabled(false);
        routes.setBackgroundColor(RouteUi.SURFACE);
        routeButtons = new ChipGroup(this);
        routeButtons.setSingleLine(true);
        routeButtons.setSingleSelection(true);
        routeButtons.setChipSpacingHorizontal(ui.dp(8));
        routeButtons.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(8));
        routes.addView(routeButtons);
        root.addView(routes);
        subtitle = ui.text("", 12, RouteUi.MUTED, false);
        subtitle.setBackgroundColor(RouteUi.SURFACE);
        subtitle.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(10));
        root.addView(subtitle);
        preview = new RoutePreview(this, data, MapActivity.readNativeAppKey(this));
        int previewHeight = Math.min(200, getResources().getConfiguration().screenHeightDp / 4);
        root.addView(preview, new LinearLayout.LayoutParams(-1, ui.dp(previewHeight)));
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        cards = ui.column();
        cards.setPadding(ui.dp(12), ui.dp(4), ui.dp(12), ui.dp(12));
        scroll.addView(cards);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        render();
    }

    private void render() {
        routeButtons.removeAllViews();
        for (String number : data.choices.keySet()) {
            Chip chip = ui.choiceChip(RouteUi.routeName(number));
            chip.setId(View.generateViewId());
            chip.setChecked(number.equals(route));
            chip.setOnClickListener(v -> {
                route = number;
                choiceIndex = 0;
                render();
                ((ScrollView) cards.getParent()).scrollTo(0, 0);
            });
            routeButtons.addView(chip);
        }
        List<RouteData.Choice> choices = data.choices.get(route);
        subtitle.setText(RouteUi.routeName(route) + " · " + data.time + (data.live ? " 출발 기준 실시간 · 경로 " : " 출발 기준 예시 · 경로 ") + choices.size() + "개");
        cards.removeAllViews();
        JSONObject boarding = data.best(route);
        for (int i = 0; i < choices.size(); i++) {
            final int index = i;
            RouteData.Choice choice = choices.get(i);
            int color = RouteUi.COLORS[i % RouteUi.COLORS.length];
            boolean selected = i == choiceIndex;
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
            ui.card(card, selected);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = ui.dp(8);
            cards.addView(card, params);
            LinearLayout tags = ui.row();
            ui.badge(tags, String.valueOf(i + 1), color);
            for (String tag : choice.tags().split(" · ")) {
                if (!tag.isEmpty()) ui.badge(tags, tag, color);
            }
            card.addView(tags);
            String arrival = data.eta(boarding, choice);
            String timing = !arrival.isEmpty() ? arrival + " 도착"
                    : data.trip ? "약 " + choice.minutes() + "분" : "서울 구간 " + choice.minutes() + "분";
            TextView summary = ui.text(timing + " · 환승 " + choice.transfers()
                    + "회 · 걷기 " + choice.walk() + "분", 15, RouteUi.INK, true);
            summary.setPadding(0, ui.dp(8), 0, ui.dp(6));
            card.addView(summary);
            card.addView(ui.text(data.label(choice), 13, RouteUi.BODY, false));
            card.setSelected(selected);
            card.setFocusable(true);
            card.setContentDescription("경로 " + (i + 1) + (selected ? " 선택됨. " : ". ")
                    + choice.tags() + ". " + summary.getText() + ". " + data.label(choice));
            card.setOnClickListener(v -> {
                choiceIndex = index;
                render();
            });
        }
        preview.select(route, choices.get(choiceIndex));
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("route", route);
        state.putInt("choice", choiceIndex);
    }

    @Override protected void onResume() {
        super.onResume();
        if (preview != null) preview.resume();
    }

    @Override protected void onPause() {
        if (preview != null) preview.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        if (preview != null) preview.close();
        super.onDestroy();
    }
}
