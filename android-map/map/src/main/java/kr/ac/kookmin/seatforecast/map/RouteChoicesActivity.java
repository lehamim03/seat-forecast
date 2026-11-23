package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import java.util.List;
import org.json.JSONObject;

/** C의 서울 → 국민대 경로 목록. 다른 모듈은 newIntent로 노선을 지정한다. */
public final class RouteChoicesActivity extends Activity {
    private RouteData data;
    private RouteUi ui;
    private RoutePreview preview;
    private LinearLayout cards;
    private LinearLayout routeButtons;
    private TextView subtitle;
    private String route;
    private int choiceIndex;

    public static Intent newIntent(Context context, String route) {
        return new Intent(context, RouteChoicesActivity.class).putExtra("route", route);
    }

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        ui = new RouteUi(this);
        LinearLayout root = ui.column();
        root.setBackgroundColor(0xffffffff);
        setContentView(root);
        RouteUi.insets(this, root);
        LinearLayout header = ui.row();
        header.addView(ui.action("‹", this::finish), new LinearLayout.LayoutParams(ui.dp(48), ui.dp(52)));
        header.getChildAt(0).setContentDescription("지도로 돌아가기");
        header.addView(ui.text("서울 → 국민대 · 경로 여러 개", 19, RouteUi.INK, true));
        root.addView(header);
        try {
            data = new RouteData(this);
        } catch (Exception error) {
            root.addView(ui.text("경로 데이터를 불러오지 못했습니다.", 16, RouteUi.MUTED, false));
            root.addView(ui.action("다시 시도", this::recreate));
            return;
        }
        if (data.choices.isEmpty()) {
            root.addView(ui.text("표시할 경로가 없습니다.", 16, RouteUi.MUTED, false));
            return;
        }
        route = state == null ? getIntent().getStringExtra("route") : state.getString("route");
        choiceIndex = state == null ? 0 : state.getInt("choice");
        if (!data.choices.containsKey(route)) route = data.choices.keySet().iterator().next();
        choiceIndex = Math.max(0, Math.min(choiceIndex, data.choices.get(route).size() - 1));

        HorizontalScrollView routes = new HorizontalScrollView(this);
        routes.setHorizontalScrollBarEnabled(false);
        routeButtons = ui.row();
        routes.addView(routeButtons);
        root.addView(routes);
        subtitle = ui.text("", 12, RouteUi.MUTED, false);
        subtitle.setPadding(ui.dp(16), 0, ui.dp(16), ui.dp(8));
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
            TextView button = ui.action(number + "번", () -> {
                route = number;
                choiceIndex = 0;
                render();
                ((ScrollView) cards.getParent()).scrollTo(0, 0);
            });
            button.setSelected(number.equals(route));
            button.setTextColor(number.equals(route) ? RouteUi.RED : RouteUi.MUTED);
            button.setBackground(ui.background(number.equals(route) ? 0xffffeef1 : 0xffffffff, 0, 8));
            routeButtons.addView(button, new LinearLayout.LayoutParams(ui.dp(80), ui.dp(48)));
        }
        List<RouteData.Choice> choices = data.choices.get(route);
        subtitle.setText(route + "번 · " + data.time + " 출발 기준 예시 · 경로 " + choices.size() + "개");
        cards.removeAllViews();
        JSONObject boarding = data.best(route);
        for (int i = 0; i < choices.size(); i++) {
            final int index = i;
            RouteData.Choice choice = choices.get(i);
            int color = RouteUi.COLORS[i % RouteUi.COLORS.length];
            boolean selected = i == choiceIndex;
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12));
            card.setBackground(ui.background(selected ? 0xfff7f4ff : RouteUi.SURFACE,
                    selected ? color : RouteUi.BORDER, 12));
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
            String timing = arrival.isEmpty() ? "서울 구간 " + choice.minutes() + "분" : arrival + " 도착";
            TextView summary = ui.text(timing + " · 환승 " + choice.transfers()
                    + "회 · 걷기 " + choice.walk() + "분", 15, RouteUi.INK, true);
            summary.setPadding(0, ui.dp(8), 0, ui.dp(6));
            card.addView(summary);
            card.addView(ui.text(choice.key(), 13, RouteUi.MUTED, false));
            card.setSelected(selected);
            card.setFocusable(true);
            card.setContentDescription("경로 " + (i + 1) + (selected ? " 선택됨. " : ". ")
                    + choice.tags() + ". " + summary.getText() + ". " + choice.key());
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
