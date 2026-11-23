package kr.ac.kookmin.seatforecast.home.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** 12주차 — 지난 분 · 자동 새로고침 시점 · 걸리는 시간 · '지금 HH:MM' */
public class LiveTimeTest {

    private static final long MIN = 60_000L;

    @Test
    public void elapsedMinutesRoundDown() {
        assertEquals(0, LiveTime.elapsedMin(1_000, 1_000));
        assertEquals(0, LiveTime.elapsedMin(1_000, 1_000 + MIN - 1));
        assertEquals(1, LiveTime.elapsedMin(1_000, 1_000 + MIN));
        assertEquals(3, LiveTime.elapsedMin(1_000, 1_000 + 3 * MIN + 59_000));
        assertEquals(0, LiveTime.elapsedMin(5_000, 1_000));   // 시계가 거꾸로 가도 음수 없음
    }

    @Test
    public void autoRefreshAfterTwoMinutes() {
        long received = 10 * MIN;
        assertFalse(LiveTime.shouldAutoRefresh(received, received, received + MIN));
        assertFalse(LiveTime.shouldAutoRefresh(received, received, received + 2 * MIN - 1));
        assertTrue(LiveTime.shouldAutoRefresh(received, received, received + 2 * MIN));
    }

    @Test
    public void autoRefreshWaitsAfterFailedAttempt() {
        long received = 10 * MIN;
        long failedAt = received + 2 * MIN;   // 자동 새로고침했는데 실패
        assertFalse(LiveTime.shouldAutoRefresh(received, failedAt, failedAt + MIN));
        assertTrue(LiveTime.shouldAutoRefresh(received, failedAt, failedAt + 2 * MIN));
    }

    @Test
    public void durationText() {
        assertEquals("0분", LiveTime.duration(0));
        assertEquals("25분", LiveTime.duration(25));
        assertEquals("1시간", LiveTime.duration(60));
        assertEquals("1시간 25분", LiveTime.duration(85));
        assertEquals("2시간 28분", LiveTime.duration(148));
        assertNull(LiveTime.duration(null));
        assertNull(LiveTime.duration(-1));
    }

    @Test
    public void plusMinutes() {
        assertEquals("07:40", LiveTime.plusMinutes("07:40", 0));
        assertEquals("07:43", LiveTime.plusMinutes("07:40", 3));
        assertEquals("08:05", LiveTime.plusMinutes("07:40", 25));
        assertEquals("00:02", LiveTime.plusMinutes("23:58", 4));
        assertEquals("07:40", LiveTime.plusMinutes("07:40", -5));
        assertNull(LiveTime.plusMinutes(null, 1));
        assertNull(LiveTime.plusMinutes("7시 40분", 1));
        assertNull(LiveTime.plusMinutes("25:00", 1));
    }

    @Test
    public void nowChipFollowsGeneratedAt() {
        String base = TripFormat.time("2026-10-07T07:40:00+09:00");
        assertEquals("07:42", LiveTime.plusMinutes(base, 2));
    }
}
