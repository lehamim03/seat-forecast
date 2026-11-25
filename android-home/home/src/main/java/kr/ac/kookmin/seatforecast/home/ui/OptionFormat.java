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
        /** 지하철로 시작 — 좌석 걱정 없음 (다가오는 차 정보가 없음) */
        SUBWAY,
        /** 다가오는 차가 모두 걸어가는 동안 지나감 */
        NONE_REACHABLE,
        /** 탈 수 있는 첫 차 ({@link BusLine#bus}) */
        BUS,
        /** 받은 뒤 시간이 지나 목록의 차가 모두 정류장을 지나감 — 새로고침이 필요 (12주차) */
        PASSED
    }

    static final class BusLine {
        final BusLineKind kind;
        /** {@link BusLineKind#BUS} 일 때만 */
        @Nullable
        final Bus bus;
        /** 그 앞에 걸어가는 동안 지나가는 차가 있었는지 */
        final boolean missedEarlier;
        /** {@link BusLineKind#BUS} 일 때 지금 기준 남은 분 ({@code eta_min} − 받은 뒤 지난 분, 0 이상) */
        final int etaMin;

        BusLine(BusLineKind kind, @Nullable Bus bus, boolean missedEarlier) {
            this(kind, bus, missedEarlier, bus == null ? 0 : bus.etaMin);
        }

        BusLine(BusLineKind kind, @Nullable Bus bus, boolean missedEarlier, int etaMin) {
            this.kind = kind;
            this.bus = bus;
            this.missedEarlier = missedEarlier;
            this.etaMin = etaMin;
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

    /** 받은 그대로 (지난 시간 0분) — {@link #busLine(Option, int)} */
    static BusLine busLine(Option o) {
        return busLine(o, 0);
    }

    /**
     * {@code board_prob} 은 "걸어가서 탈 수 있는 첫 차" 기준이므로, 카드에도 그 차를 보여 줍니다.
     * 첫 차가 {@code reachable: false} 면 건너뛰고 다음 차를 보여 주며 {@code missedEarlier} 를 켭니다.
     *
     * <p>12주차: 응답을 받은 뒤 {@code elapsedMin} 분이 지났으면 남은 시간을 줄여서 보여 줍니다.
     * <ul>
     *   <li>남은 시간이 0 보다 작으면 이미 정류장을 지나간 차 → 목록에서 뺌 (놓친 차로 세지 않음)</li>
     *   <li>남은 시간이 도보 시간보다 짧아지면 이제 걸어가서 못 탐 → 놓친 차 (서버 {@code reachable} 규칙: eta ≥ 도보)</li>
     *   <li>차가 모두 지나갔으면 {@link BusLineKind#PASSED}</li>
     * </ul>
     */
    static BusLine busLine(Option o, int elapsedMin) {
        List<Bus> buses = o.buses;
        if (buses == null || buses.isEmpty()) {
            return new BusLine(o.isSubway() ? BusLineKind.SUBWAY : BusLineKind.NO_INFO, null, false);
        }
        int elapsed = Math.max(0, elapsedMin);
        boolean missed = false;
        boolean anyLeft = false;
        for (Bus b : buses) {
            if (b == null) continue;
            int left = b.etaMin - elapsed;
            if (elapsed > 0 && left < 0) continue;   // 이미 지나간 차
            anyLeft = true;
            boolean reachable = b.reachable && (elapsed == 0 || left >= o.walkMin);
            if (reachable) return new BusLine(BusLineKind.BUS, b, missed, Math.max(0, left));
            missed = true;
        }
        if (!anyLeft) return new BusLine(BusLineKind.PASSED, null, false);
        return new BusLine(BusLineKind.NONE_REACHABLE, null, missed);
    }

    /** 오른쪽 위 배지: 탑승 확률 · "입석 가능" · "좌석 정보 없음" · 없음 */
    enum Badge { PROBABILITY, STANDING, SEAT_UNKNOWN, NONE }

    /**
     * 우회로 건너뛰는 정류장은 배지 없음. seat_limited=false(서서 탈 수 있음 · 지하철)면 "입석 가능",
     * seat_known=false(서울 등록 좌석버스 등)면 "좌석 정보 없음", 나머지는 탑승 확률.
     */
    static Badge badge(Option o) {
        if (o.skipped) return Badge.NONE;
        if (o.isStandingOk()) return Badge.STANDING;
        if (o.isSeatUnknown()) return Badge.SEAT_UNKNOWN;
        return Badge.PROBABILITY;
    }

    /** 우회로 건너뛰는 정류장은 1순위 · 배지를 보이지 않음 */
    static boolean showBadge(Option o) {
        return badge(o) != Badge.NONE;
    }

    /** 둘째 줄 끝에 보여 줄 종류 (기다리기 · 걸어가기 · 다른 버스 · 지하철 · 모르는 값은 그대로), 없으면 null */
    @Nullable
    static String kindLabel(Option o) {
        return o.kind == null || o.kind.trim().isEmpty() ? null : o.kind.trim();
    }

    /** 지난 출근길 재생: 그날 실제로 — 없음(실시간) · 탔음 · 모두 만석이라 못 탐 */
    enum ActualKind { NONE, BOARDED, NOT_BOARDED }

    static ActualKind actualKind(Option o) {
        if (o.actual == null) return ActualKind.NONE;
        return o.actual.boardedAt == null || o.actual.boardedAt.isEmpty() ? ActualKind.NOT_BOARDED : ActualKind.BOARDED;
    }

    /** 넷째 줄: 출발지 → 도착지 경로 한 줄 (summary), 없으면 null */
    @Nullable
    static String summary(Option o) {
        return o.summary == null || o.summary.trim().isEmpty() ? null : o.summary.trim();
    }

    /** 서버 ranking 첫 항목이고 탈 수 있을 때만 '1순위' — 정렬 메뉴를 바꿔도 같은 카드에 붙음 */
    static boolean isFirst(Option o, @Nullable Option recommended) {
        return o == recommended && !o.skipped;
    }
}
