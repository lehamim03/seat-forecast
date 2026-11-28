package kr.ac.kookmin.seatforecast.app;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 탑승 피드백에 함께 보내는 "앱이 보여 준 탑승 확률" (predicted_prob).
 * 좌석제 버스(seat_limited=true)이고 좌석 수를 알 때(seat_known=true)만 그 board_prob — 그 밖에는 null.
 * 좌석 정보가 없을 때의 값은 평소 탑승률(93% · 98%)이라 예측이 아니므로, 보내면 field_test.py 채점을 흐립니다.
 */
final class FeedbackProb {

    private FeedbackProb() {
    }

    @Nullable
    static Double of(@Nullable Option option) {
        if (option == null) return null;
        if (!Boolean.TRUE.equals(option.seatLimited) || !Boolean.TRUE.equals(option.seatKnown)) return null;
        double p = option.boardProb;
        return Double.isNaN(p) || p < 0 || p > 1 ? null : p;
    }
}
