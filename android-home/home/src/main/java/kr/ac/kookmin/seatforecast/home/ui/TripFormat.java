package kr.ac.kookmin.seatforecast.home.ui;

import androidx.annotation.Nullable;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.ac.kookmin.seatforecast.home.model.Day;

/**
 * '우리집 → 국민대' 카드의 칩 3개 (Figma: "10/12(월)" · "평일 출퇴근" · "지금 07:16").
 * generated_at("2026-10-07T07:40:00+09:00")의 날짜 · 시각을 서버 기준 그대로 씁니다 (기기 시간대로 바꾸지 않음).
 */
final class TripFormat {

    private static final Pattern ISO =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})T(\\d{2}):(\\d{2})");
    private static final String[] WEEKDAYS = {"일", "월", "화", "수", "목", "금", "토"};

    private TripFormat() {
    }

    /** "10/7(수)" — 형식이 다르면 null */
    @Nullable
    static String dateChip(@Nullable String generatedAt) {
        Matcher m = match(generatedAt);
        if (m == null) return null;
        int y = Integer.parseInt(m.group(1));
        int mo = Integer.parseInt(m.group(2));
        int d = Integer.parseInt(m.group(3));
        if (mo < 1 || mo > 12 || d < 1 || d > 31) return null;
        Calendar c = new GregorianCalendar(y, mo - 1, d);
        return mo + "/" + d + "(" + WEEKDAYS[c.get(Calendar.DAY_OF_WEEK) - 1] + ")";
    }

    /** "07:40" — 형식이 다르면 null */
    @Nullable
    static String time(@Nullable String generatedAt) {
        Matcher m = match(generatedAt);
        return m == null ? null : m.group(4) + ":" + m.group(5);
    }

    /** 가운데 칩: day.kind("평일 출퇴근"), 없으면 day.label */
    @Nullable
    static String dayChip(@Nullable Day day) {
        if (day == null) return null;
        if (day.kind != null && !day.kind.isEmpty()) return day.kind;
        return day.label;
    }

    @Nullable
    private static Matcher match(@Nullable String s) {
        if (s == null) return null;
        Matcher m = ISO.matcher(s);
        return m.find() ? m : null;
    }
}
