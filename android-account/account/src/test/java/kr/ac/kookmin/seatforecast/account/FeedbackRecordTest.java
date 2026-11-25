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

    /** 출발지 · 도착지 모드: 예전 목록에 없는 노선 · 정류장도 그대로, 정류장 이름은 기록에서 */
    @org.junit.Test
    public void anyRouteAndStopName() {
        kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest req =
                new kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest("u", "2026-12-01", "9401",
                        "206000299", true, 0.98, "");
        req.stopName = "이매촌한신.서현역.AK프라자";
        org.junit.Assert.assertNull(req.validate());
        org.junit.Assert.assertEquals("이매촌한신.서현역.AK프라자", req.toMap().get("stop_name"));
        // 서버 요청 본문(JSON)에는 stop_name 이 없음
        String json = new com.google.gson.Gson().toJson(req);
        org.junit.Assert.assertFalse(json.contains("stop_name"));
        org.junit.Assert.assertTrue(json.contains("\"route\":\"9401\""));

        FeedbackRecord r = new FeedbackRecord();
        r.date = "2026-12-01";
        r.route = "9401";
        r.stopId = "206000299";
        r.stopName = "이매촌한신.서현역.AK프라자";
        r.sent = true;
        org.junit.Assert.assertTrue(r.summary().contains("이매촌한신.서현역.AK프라자"));
        r.stopName = null;
        org.junit.Assert.assertTrue(r.summary().contains("206000299"));   // 예전 목록에 없으면 ID
    }
}
