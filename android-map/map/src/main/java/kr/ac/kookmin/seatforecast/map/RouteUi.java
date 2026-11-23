package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 지도와 경로 목록에서 함께 쓰는 카드·배지 스타일. */
final class RouteUi {
    static final int INK = 0xff1e2126;
    static final int MUTED = 0xff616773;
    static final int RED = 0xffb52b3c;
    static final int BORDER = 0xffdfe1e6;
    static final int SURFACE = 0xfff4f5f7;
    static final int[] COLORS = {0xff793fee, 0xffc66a00, 0xff2962ed, 0xff667080};
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

    TextView action(String label, Runnable action) {
        TextView view = text(label, 14, RED, true);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(48));
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    void badge(LinearLayout parent, String label, int color) {
        TextView view = text(label, 11, color, true);
        view.setPadding(dp(7), dp(3), dp(7), dp(3));
        view.setBackground(background(0xffeeeeef, color, 6));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMargins(0, 0, dp(6), 0);
        parent.addView(view, params);
    }

    GradientDrawable background(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != 0) drawable.setStroke(dp(1), stroke);
        return drawable;
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
}
