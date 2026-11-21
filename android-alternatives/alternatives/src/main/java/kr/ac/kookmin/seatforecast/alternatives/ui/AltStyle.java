package kr.ac.kookmin.seatforecast.alternatives.ui;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;

import kr.ac.kookmin.seatforecast.alternatives.R;

/** 탑승 확률 단계 · 평소 버스 상태 → 색 · 바탕 리소스. */
final class AltStyle {

    private AltStyle() {
    }

    @ColorRes
    static int color(AltFormat.Level level) {
        switch (level) {
            case HIGH:
                return R.color.alt_high;
            case MID:
                return R.color.alt_mid;
            default:
                return R.color.alt_low;
        }
    }

    @DrawableRes
    static int pill(AltFormat.Level level) {
        switch (level) {
            case HIGH:
                return R.drawable.alt_bg_pill_high;
            case MID:
                return R.drawable.alt_bg_pill_mid;
            default:
                return R.drawable.alt_bg_pill_low;
        }
    }

    @ColorRes
    static int color(AltFormat.Status status) {
        switch (status) {
            case RELAXED:
                return R.color.alt_high;
            case RISK:
                return R.color.alt_mid;
            case FULL:
            case SKIPPED:
                return R.color.alt_low;
            default:
                return R.color.alt_unknown;
        }
    }

    @DrawableRes
    static int pill(AltFormat.Status status) {
        switch (status) {
            case RELAXED:
                return R.drawable.alt_bg_pill_high;
            case RISK:
                return R.drawable.alt_bg_pill_mid;
            case FULL:
            case SKIPPED:
                return R.drawable.alt_bg_pill_low;
            default:
                return R.drawable.alt_bg_pill_unknown;
        }
    }

    @DrawableRes
    static int banner(AltFormat.Status status) {
        switch (status) {
            case RELAXED:
                return R.drawable.alt_bg_banner_high;
            case RISK:
                return R.drawable.alt_bg_banner_mid;
            case FULL:
            case SKIPPED:
                return R.drawable.alt_bg_banner_low;
            default:
                return R.drawable.alt_bg_banner_unknown;
        }
    }
}
