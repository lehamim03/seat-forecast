package kr.ac.kookmin.seatforecast.alternatives.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import kr.ac.kookmin.seatforecast.alternatives.model.Actual;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/** 재생 중 자동 갱신 · GPS 꺼짐, actual 표시 종류 */
public class AltReplayFormatTest {

    private static final long MINUTE = 60_000L;

    @Test
    public void replayTurnsOffAutoRefresh() {
        long interval = AltFormat.refreshInterval("2026-11-11T08:20", MINUTE);
        assertEquals(0, interval);
        // 마지막 갱신이 오래전이어도 다음 갱신을 잡지 않음
        assertEquals(-1, AltFormat.nextRefreshDelay(1_000, 10 * MINUTE, interval));
        assertFalse(AltFormat.usesGps("2026-11-11T08:20"));
    }

    @Test
    public void liveKeepsAutoRefresh() {
        long interval = AltFormat.refreshInterval(null, MINUTE);
        assertEquals(MINUTE, interval);
        assertEquals(0, AltFormat.nextRefreshDelay(1_000, 10 * MINUTE, interval));
        assertTrue(AltFormat.usesGps(null));
    }

    @Test
    public void actualKinds() {
        Option o = new Option();
        assertEquals(AltFormat.ActualKind.NONE, AltFormat.actualKind(o));
        o.actual = new Actual();
        assertEquals(AltFormat.ActualKind.NOT_BOARDED, AltFormat.actualKind(o));
        o.actual.boardedAt = "08:31";
        assertEquals(AltFormat.ActualKind.BOARDED, AltFormat.actualKind(o));
    }

    @Test
    public void durationLikeHome() {
        assertEquals("1시간 24분", AltFormat.duration(84));
        assertEquals("45분", AltFormat.duration(45));
        assertEquals("2시간", AltFormat.duration(120));
        assertNull(AltFormat.duration(null));
    }
}
