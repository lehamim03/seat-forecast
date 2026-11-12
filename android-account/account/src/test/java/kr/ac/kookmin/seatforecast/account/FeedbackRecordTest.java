package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRecord;

/** 마이페이지 '이번 달 N회 · 탑승 성공 M회' 와 '오늘 탑승 결과' 카드 숨김 */
public class FeedbackRecordTest {

    private static FeedbackRecord rec(String date, String route, boolean boarded) {
        FeedbackRecord r = new FeedbackRecord();
        r.date = date;
        r.route = route;
        r.boarded = boarded;
        return r;
    }

    private final List<FeedbackRecord> records = Arrays.asList(
            rec("2026-11-09", "3007", true),
            rec("2026-11-08", "8800", false),
            rec("2026-11-03", "3007", true),
            rec("2026-10-31", "3007", true));

    @Test
    public void monthStatsCountsOnlyThisMonth() {
        assertArrayEquals(new int[]{3, 2}, FeedbackRecord.monthStats(records, "2026-11"));
        assertArrayEquals(new int[]{1, 1}, FeedbackRecord.monthStats(records, "2026-10"));
        assertArrayEquals(new int[]{0, 0}, FeedbackRecord.monthStats(records, "2026-12"));
    }

    @Test
    public void hasRecordMatchesDateAndRoute() {
        assertTrue(FeedbackRecord.hasRecord(records, "2026-11-09", "3007"));
        assertFalse(FeedbackRecord.hasRecord(records, "2026-11-09", "8800"));
        assertFalse(FeedbackRecord.hasRecord(records, "2026-11-10", "3007"));
    }
}
