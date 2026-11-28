package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;

/**
 * 지도와 경로 목록에서 함께 쓰는 카드 · 알약 · 버튼 · 칩 — 팀 앱 A 홈 · B 대안과 같은 색과 모양.
 * (빨강 #C8102E, 바탕 #F2F3F5, 흰 카드 모서리 16dp, 확률 알약 초록 · 노랑 · 빨강)
 */
final class RouteUi {
    static final int INK = 0xff1f2024;
    static final int BODY = 0xff2b2f36;
    static final int MUTED = 0xff8a8d93;
    static final int RED = 0xffc8102e;
    static final int RED_LIGHT = 0xfffdedee;
    static final int BORDER = 0xffe5e7eb;
    static final int SURFACE = 0xffffffff;
    static final int BACKGROUND = 0xfff2f3f5;
    static final int GRAY_LINE = 0xff9aa1ad;
    /** 경로 목록 화면의 경로 순서 색 (1 = 팀 빨강) */
    static final int[] COLORS = {RED, 0xff7c3aed, 0xff2563eb, 0xff667080};

    enum Level { HIGH, MID, LOW, UNKNOWN }

    private final Activity activity;

    RouteUi(Activity activity) { this.activity = activity; }

    int dp(int value) { return Math.round(value * activity.getResources().getDisplayMetrics().density); }

    LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }

    LinearLayout row() {
        LinearLayout view = new LinearLayout(activity);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    /** 빨간 채운 버튼 (A 홈 HomeButton 과 같음) — 다시 시도 등 */
    MaterialButton primaryButton(String label, Runnable action) {
        MaterialButton button = new MaterialButton(activity);
        button.setText(label);
        button.setCornerRadius(dp(10));
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackgroundTintList(ColorStateList.valueOf(RED));
        button.setTextColor(SURFACE);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** 빨간 테두리 버튼 */
    MaterialButton outlineButton(String label, Runnable action) {
        MaterialButton button = new MaterialButton(activity, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setCornerRadius(dp(10));
        button.setMinHeight(dp(48));
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(RED);
        button.setStrokeColor(ColorStateList.valueOf(RED));
        button.setOnClickListener(v -> action.run());
        return button;
    }

    /** 고르는 칩 — 선택되면 연한 빨강 바탕 · 빨간 글씨 · 빨간 테두리 */
    Chip choiceChip(String label) {
        Chip chip = new Chip(activity);
        chip.setText(label);
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        int[][] states = {{android.R.attr.state_checked}, {}};
        chip.setChipBackgroundColor(new ColorStateList(states, new int[]{RED_LIGHT, SURFACE}));
        chip.setChipStrokeColor(new ColorStateList(states, new int[]{RED, BORDER}));
        chip.setChipStrokeWidth(dp(1));
        chip.setTextColor(new ColorStateList(states, new int[]{RED, BODY}));
        chip.setEnsureMinTouchTargetSize(true);
        return chip;
    }

    /** 노선 번호 칩 (빨강 · 흰 글씨, A 홈 HomeRouteChip 과 같음) */
    TextView routeChip(String route) {
        TextView view = text(route, 12, SURFACE, true);
        view.setPadding(dp(6), dp(1), dp(6), dp(1));
        view.setBackground(background(RED, 0, 5));
        return view;
    }

    /** 상태 · 확률 알약 */
    TextView pill(String label, Level level) {
        TextView view = text(label, 12, color(level), true);
        view.setPadding(dp(10), dp(3), dp(10), dp(3));
        view.setBackground(background(bg(level), 0, 999));
        return view;
    }

    void stylePill(TextView view, String label, Level level) {
        view.setText(label);
        view.setTextColor(color(level));
        view.setBackground(background(bg(level), 0, 999));
        view.setVisibility(View.VISIBLE);
    }

    /** 작은 태그 (경로 목록의 1 · 추천 · 최단시간 …) */
    void badge(LinearLayout parent, String label, int color) {
        TextView view = text(label, 11, color, true);
        view.setPadding(dp(8), dp(3), dp(8), dp(3));
        view.setBackground(background(tint(color), 0, 999));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(6), 0);
        parent.addView(view, params);
    }

    /** 경로 카드 바탕 — 흰 바탕 모서리 16dp, 선택이면 빨간 테두리 1.5dp (B 추천 카드와 같음) */
    void card(View view, boolean selected) {
        view.setBackgroundResource(selected ? R.drawable.map_bg_card_selected : R.drawable.map_bg_card);
    }

    GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    static int color(Level level) {
        switch (level) {
            case HIGH: return 0xff1e9e5a;
            case MID: return 0xffb45309;
            case LOW: return RED;
            default: return 0xff6b7280;
        }
    }

    static int bg(Level level) {
        switch (level) {
            case HIGH: return 0xffe6f4ec;
            case MID: return 0xfffef3c7;
            case LOW: return 0xfffbe9ec;
            default: return 0xffeeeff1;
        }
    }

    /** 색을 12% 만 남긴 연한 바탕 */
    static int tint(int color) {
        int r = (color >> 16) & 0xff, g = (color >> 8) & 0xff, b = color & 0xff;
        r = 255 - (255 - r) * 12 / 100;
        g = 255 - (255 - g) * 12 / 100;
        b = 255 - (255 - b) * 12 / 100;
        return 0xff000000 | (r << 16) | (g << 8) | b;
    }

    static void insets(Activity activity, View root) {
        if (Build.VERSION.SDK_INT >= 30) {
            activity.getWindow().setDecorFitsSystemWindows(false);
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars()
                                | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
    }

    /** "8800번" · 지하철은 그대로 ("수인분당선") — 숫자로 시작하지 않는 노선(호선 · 선)에는 "번"을 붙이지 않음 */
    static String routeName(String route) {
        if (route == null || route.isEmpty()) return "";
        if (route.endsWith("선") || route.endsWith("호선") || !Character.isDigit(route.charAt(0))
                && !route.matches("^[A-Za-z]{1,2}\\d.*")) return route;
        return route + "번";
    }

    static String routeName(org.json.JSONObject option) {
        if ("subway".equals(option.optString("first_mode"))) return option.optString("route");
        return routeName(option.optString("route"));
    }

    /** 탑승 확률 — seat_limited=false 면 "입석 가능", seat_known=false 면 "좌석 정보 없음" */
    static String probability(org.json.JSONObject option) {
        if (option.has("seat_limited") && !option.optBoolean("seat_limited", true)) return "입석 가능";
        if (option.has("seat_known") && !option.optBoolean("seat_known", true)) return "좌석 정보 없음";
        double chance = option.optDouble("board_prob", Double.NaN);
        return Double.isNaN(chance) ? "확률 미확인" : "탑승 " + Math.round(chance * 100) + "%";
    }

    /** 확률 알약 색 — 70% 이상 초록 · 40~70% 노랑 · 40% 미만 빨강 (docs/api.md), 입석 가능 = 초록, 모름 = 회색 */
    static Level probabilityLevel(org.json.JSONObject option) {
        if (option.has("seat_limited") && !option.optBoolean("seat_limited", true)) return Level.HIGH;
        if (option.has("seat_known") && !option.optBoolean("seat_known", true)) return Level.UNKNOWN;
        double chance = option.optDouble("board_prob", Double.NaN);
        if (Double.isNaN(chance)) return Level.UNKNOWN;
        return chance >= 0.7 ? Level.HIGH : chance >= 0.4 ? Level.MID : Level.LOW;
    }
}
