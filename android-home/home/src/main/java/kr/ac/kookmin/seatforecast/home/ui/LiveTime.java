package kr.ac.kookmin.seatforecast.home.ui;

import androidx.annotation.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 12주차 — 도착 시각 · 새로고침 규칙 (안드로이드 없이 단위 테스트 가능).
 *
 * <p>지난 시간은 기기 시계와 {@code generated_at} 을 비교하지 않고, <b>응답을 받은 순간부터</b>
 * {@code SystemClock.elapsedRealtime()} 으로 잽니다. 기기 시계가 틀리거나 예시 JSON(07:40 고정)이어도 맞게 줄어듭니다.
 */
final class LiveTime {

    /** 남은 시간 · '몇 분 전' 표시를 다시 그리는 간격 */
    static final long TICK_MS = 30_000L;
    /** 받은 지 이만큼 지나면 화면이 보이는 동안 알아서 다시 불러옴 */
    static final int AUTO_REFRESH_MIN = 2;
    /** 자동 새로고침이 실패했을 때 다시 시도하기까지 (서버를 계속 두드리지 않도록) */
    static final long AUTO_RETRY_MS = 2 * 60_000L;

    private static final Pattern HH_MM = Pattern.compile("(\\d{2}):(\\d{2})");

    private LiveTime() {
    }

    /** 받은 뒤 지난 분 (내림, 0 이상) */
    static int elapsedMin(long receivedAtMs, long nowMs) {
        long d = nowMs - receivedAtMs;
        return d <= 0 ? 0 : (int) (d / 60_000L);
    }

    /**
     * 자동 새로고침할 때인지.
     *
     * @param lastAttemptMs 마지막으로 불러오기를 시작한 때 (성공 · 실패 모두)
     */
    static boolean shouldAutoRefresh(long receivedAtMs, long lastAttemptMs, long nowMs) {
        return elapsedMin(receivedAtMs, nowMs) >= AUTO_REFRESH_MIN
                && nowMs - lastAttemptMs >= AUTO_RETRY_MS;
    }

    /** 148 → "2시간 28분", 60 → "1시간", 25 → "25분". null · 음수면 null */
    @Nullable
    static String duration(@Nullable Integer minutes) {
        if (minutes == null || minutes < 0) return null;
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m + "분";
        if (m == 0) return h + "시간";
        return h + "시간 " + m + "분";
    }

    /** "07:40" + 3분 → "07:43" (24시를 넘으면 0시부터). 형식이 다르면 null */
    @Nullable
    static String plusMinutes(@Nullable String hhmm, int minutes) {
        if (hhmm == null) return null;
        Matcher m = HH_MM.matcher(hhmm);
        if (!m.find()) return null;
        int h = Integer.parseInt(m.group(1));
        int mi = Integer.parseInt(m.group(2));
        if (h > 23 || mi > 59) return null;
        int total = ((h * 60 + mi + Math.max(0, minutes)) % (24 * 60));
        return String.format(Locale.ROOT, "%02d:%02d", total / 60, total % 60);
    }
}
