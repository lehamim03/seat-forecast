package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import kr.ac.kookmin.seatforecast.account.feedback.FeedbackResponse;

/** 13주차 서버 연결: 어떤 응답이면 다시 보내지 않을지 (server-bd 오류 코드 기준) */
public class FeedbackRejectionTest {

    @Test
    public void badRequestsAreNotRetried() {
        assertTrue("bad_date · unknown_route · 형식 오류", FeedbackResponse.isPermanentRejection(400));
        assertTrue("uid_mismatch", FeedbackResponse.isPermanentRejection(403));
        assertTrue(FeedbackResponse.isPermanentRejection(404));
        assertTrue(FeedbackResponse.isPermanentRejection(422));
    }

    @Test
    public void temporaryFailuresAreRetried() {
        assertFalse("로그인 만료 → 새 토큰으로 다시", FeedbackResponse.isPermanentRejection(401));
        assertFalse(FeedbackResponse.isPermanentRejection(408));
        assertFalse(FeedbackResponse.isPermanentRejection(429));
        assertFalse(FeedbackResponse.isPermanentRejection(500));
        assertFalse(FeedbackResponse.isPermanentRejection(503));
        assertFalse("성공은 거절 아님", FeedbackResponse.isPermanentRejection(201));
    }
}
