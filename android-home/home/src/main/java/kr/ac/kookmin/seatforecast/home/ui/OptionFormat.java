package kr.ac.kookmin.seatforecast.home.ui;

import androidx.annotation.Nullable;

import java.util.List;

import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;

/**
 * 추천 카드에 무엇을 보여 줄지 정하는 규칙 (안드로이드 없이 단위 테스트 가능).
 * 문구 · 색은 {@link OptionAdapter} 가 리소스에서 고릅니다.
 */
final class OptionFormat {

    /** 탑승 확률 배지 단계 — Figma ProbBadge: 초록 70% 이상 · 노랑 40~70% · 빨강 40% 미만 */
    enum Level { HIGH, MID, LOW }

    static final double HIGH_FROM = 0.7;
    static final double MID_FROM = 0.4;

    /** 셋째 줄(다가오는 차)에 보여 줄 내용 */
    enum BusLineKind {
        /** {@code buses[]} 가 비어 있음 — 실시간 차량 없이 배차간격으로 예상한 값 */
        NO_INFO,
        /** 다가오는 차가 모두 걸어가는 동안 지나감 */
        NONE_REACHABLE,
        /** 탈 수 있는 첫 차 ({@link BusLine#bus}) */
        BUS
    }

    static final class BusLine {
        final BusLineKind kind;
        /** {@link BusLineKind#BUS} 일 때만 */
        @Nullable
        final Bus bus;
        /** 그 앞에 걸어가는 동안 지나가는 차가 있었는지 */
        final boolean missedEarlier;

        BusLine(BusLineKind kind, @Nullable Bus bus, boolean missedEarlier) {
            this.kind = kind;
            this.bus = bus;
            this.missedEarlier = missedEarlier;
        }
    }

    private OptionFormat() {
    }

    static Level level(double prob) {
        if (prob >= HIGH_FROM) return Level.HIGH;
        if (prob >= MID_FROM) return Level.MID;
        return Level.LOW;
    }

    /** 0.95 → "95%" (0~100 으로 자름) */
    static String percent(double prob) {
        if (Double.isNaN(prob)) prob = 0;
        long p = Math.round(Math.max(0, Math.min(1, prob)) * 100);
        return p + "%";
    }

    /**
     * {@code board_prob} 은 "걸어가서 탈 수 있는 첫 차" 기준이므로, 카드에도 그 차를 보여 줍니다.
     * 첫 차가 {@code reachable: false} 면 건너뛰고 다음 차를 보여 주며 {@code missedEarlier} 를 켭니다.
     */
    static BusLine busLine(Option o) {
        List<Bus> buses = o.buses;
        if (buses == null || buses.isEmpty()) return new BusLine(BusLineKind.NO_INFO, null, false);
        boolean missed = false;
        for (Bus b : buses) {
            if (b == null) continue;
            if (b.reachable) return new BusLine(BusLineKind.BUS, b, missed);
            missed = true;
        }
        return new BusLine(BusLineKind.NONE_REACHABLE, null, missed);
    }

    /** 우회로 건너뛰는 정류장은 1순위 · 배지를 보이지 않음 */
    static boolean showBadge(Option o) {
        return !o.skipped;
    }

    /** ranking 첫 항목이고 탈 수 있을 때만 '1순위' */
    static boolean isFirst(Option o, int position) {
        return position == 0 && !o.skipped;
    }
}
