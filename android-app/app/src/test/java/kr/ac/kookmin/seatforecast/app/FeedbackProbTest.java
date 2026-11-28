package kr.ac.kookmin.seatforecast.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/** 피드백 predicted_prob — 좌석제(seat_limited=true) · 좌석 수 앎(seat_known=true) 일 때만 */
public class FeedbackProbTest {

    private static Option option(Boolean limited, Boolean known, double prob) {
        Option o = new Option();
        o.seatLimited = limited;
        o.seatKnown = known;
        o.boardProb = prob;
        return o;
    }

    @Test
    public void seatLimitedAndKnownSendsProbability() {
        assertEquals(0.42, FeedbackProb.of(option(true, true, 0.42)), 1e-9);
    }

    @Test
    public void seatUnknownIsUsualRateNotPrediction() {
        assertNull(FeedbackProb.of(option(true, false, 0.93)));
    }

    @Test
    public void standingOkOrSubwayIsNull() {
        assertNull(FeedbackProb.of(option(false, null, 1.0)));
        assertNull(FeedbackProb.of(option(false, true, 1.0)));
    }

    @Test
    public void missingFieldsAreNull() {
        assertNull(FeedbackProb.of(option(null, null, 0.8)));   // 예전 응답 (출발지 · 도착지 모드 아님)
        assertNull(FeedbackProb.of(option(true, null, 0.8)));
        assertNull(FeedbackProb.of(null));
    }
}
