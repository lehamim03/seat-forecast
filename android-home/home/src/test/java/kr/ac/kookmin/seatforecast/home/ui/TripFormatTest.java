package kr.ac.kookmin.seatforecast.home.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import kr.ac.kookmin.seatforecast.home.model.Day;

public class TripFormatTest {

    @Test
    public void dateChipFromGeneratedAt() {
        assertEquals("10/7(수)", TripFormat.dateChip("2026-10-07T07:40:00+09:00"));   // docs 예시
        assertEquals("10/12(월)", TripFormat.dateChip("2026-10-12T07:16:00+09:00"));  // Figma 값
        assertEquals("11/16(월)", TripFormat.dateChip("2026-11-16T07:16:00+09:00"));
        assertEquals("1/1(금)", TripFormat.dateChip("2027-01-01T00:00:00Z"));
    }

    @Test
    public void timeFromGeneratedAtKeepsServerClock() {
        assertEquals("07:40", TripFormat.time("2026-10-07T07:40:00+09:00"));
        assertEquals("23:05", TripFormat.time("2026-10-07T23:05"));
    }

    @Test
    public void badGeneratedAtHidesChips() {
        assertNull(TripFormat.dateChip(null));
        assertNull(TripFormat.dateChip(""));
        assertNull(TripFormat.dateChip("07:40"));
        assertNull(TripFormat.dateChip("2026-13-07T07:40:00+09:00"));
        assertNull(TripFormat.time(null));
        assertNull(TripFormat.time("어제"));
    }

    @Test
    public void dayChipPrefersKind() {
        Day d = new Day();
        d.kind = "평일 출퇴근";
        d.label = "평일 출퇴근 시간대";
        assertEquals("평일 출퇴근", TripFormat.dayChip(d));
        d.kind = null;
        assertEquals("평일 출퇴근 시간대", TripFormat.dayChip(d));
        assertNull(TripFormat.dayChip(null));
    }
}
