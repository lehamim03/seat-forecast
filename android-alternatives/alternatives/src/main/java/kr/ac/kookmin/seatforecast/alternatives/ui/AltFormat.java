package kr.ac.kookmin.seatforecast.alternatives.ui;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 실시간 대안 화면에 무엇을 보여 줄지 정하는 규칙 (안드로이드 없이 단위 테스트 가능).
 * 문구 · 색은 {@link AlternativesFragment} · {@link OptionCard} 가 리소스에서 고릅니다.
 */
final class AltFormat {

    /** 탑승 확률 단계 — A 홈 배지와 같음: 초록 70% 이상 · 노랑 40~70% · 빨강 40% 미만 */
    enum Level { HIGH, MID, LOW }

    static final double HIGH_FROM = 0.7;
    static final double MID_FROM = 0.4;

    /** 맨 위 '평소 버스' 상태 (10주차 상태별 화면 설계) */
    enum Status {
        /** 여유 — 평소 버스를 그대로 타도 됨 (70% 이상) */
        RELAXED,
        /** 만석 위험 — 40~70% */
        RISK,
        /** 만석 — 40% 미만, 대안을 먼저 보여 줌 */
        FULL,
        /** 우회로 평소 정류장을 지나감 */
        SKIPPED,
        /** 평소 버스('기다리기') 정보가 없음 */
        UNKNOWN
    }

    /** 맨 위 상태 카드에 보여 줄 값 */
    static final class Banner {
        final Status status;
        /** 평소 노선 */
        @Nullable
        final String route;
        /** 평소 정류장에 걸어서 탈 수 있는 첫 차, 모르면 null */
        @Nullable
        final Bus bus;
        /** 평소 버스 탑승 확률 ({@link Status#UNKNOWN} 이면 의미 없음) */
        final double prob;
        /** seat_limited=false — 서서 탈 수 있음 (확률 대신 "입석 가능") */
        final boolean standing;
        /** seat_known=false — 좌석 수를 모름 (확률 대신 "좌석 정보 없음") */
        final boolean seatUnknown;

        Banner(Status status, @Nullable String route, @Nullable Bus bus, double prob) {
            this(status, route, bus, prob, false, false);
        }

        Banner(Status status, @Nullable String route, @Nullable Bus bus, double prob, boolean standing,
               boolean seatUnknown) {
            this.status = status;
            this.route = route;
            this.bus = bus;
            this.prob = prob;
            this.standing = standing;
            this.seatUnknown = seatUnknown;
        }
    }

    /** 차량 한 대의 좌석 표시 */
    enum Seats { UNKNOWN, FULL, SOME }

    /** '다가오는 차' 목록 한 줄 (11주차 차량별 좌석) */
    static final class BusRow {
        final Bus bus;
        /** 걸어가는 동안 지나가서 못 타는 차 */
        final boolean missed;
        /** 걸어가서 탈 수 있는 첫 차 (탑승 확률 = 카드 배지 값) */
        final boolean firstReachable;
        final Seats seats;
        final Level level;

        BusRow(Bus bus, boolean missed, boolean firstReachable, Seats seats, Level level) {
            this.bus = bus;
            this.missed = missed;
            this.firstReachable = firstReachable;
            this.seats = seats;
            this.level = level;
        }
    }

    private static final Pattern TIME = Pattern.compile("T(\\d{2}:\\d{2})");

    private AltFormat() {
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

    /** "2026-10-07T07:40:00+09:00" → "07:40", 형식이 다르면 null */
    @Nullable
    static String time(@Nullable String generatedAt) {
        if (generatedAt == null) return null;
        Matcher m = TIME.matcher(generatedAt);
        return m.find() ? m.group(1) : null;
    }

    /** 평소 버스 = '기다리기' 선택지로 맨 위 상태를 정함 */
    static Banner banner(AlternativesResult r) {
        Option usual = r.usual();
        String route = r.myRoute != null ? r.myRoute : usual == null ? null : usual.route;
        if (usual == null) return new Banner(Status.UNKNOWN, route, null, 0);
        Bus bus = firstReachable(usual);
        if (usual.skipped) return new Banner(Status.SKIPPED, route, bus, usual.boardProb);
        // 출발지 · 도착지 모드: 서서 탈 수 있으면 여유, 좌석 수를 모르면 정보 없음 (확률을 믿을 수 없음)
        if (usual.isStandingOk()) return new Banner(Status.RELAXED, route, bus, usual.boardProb, true, false);
        if (usual.isSeatUnknown()) return new Banner(Status.UNKNOWN, route, bus, usual.boardProb, false, true);
        Status status;
        switch (level(usual.boardProb)) {
            case HIGH:
                status = Status.RELAXED;
                break;
            case MID:
                status = Status.RISK;
                break;
            default:
                status = Status.FULL;
        }
        return new Banner(status, route, bus, usual.boardProb);
    }

    /** 걸어가서 탈 수 있는 첫 차 ({@code board_prob} 의 기준), 없으면 null */
    @Nullable
    static Bus firstReachable(Option o) {
        if (o.buses == null) return null;
        for (Bus b : o.buses) {
            if (b != null && b.reachable) return b;
        }
        return null;
    }

    /** 화면 번호 — 기획서 그대로 ① 기다리기 · ② 걸어가기 · ③ 다른 버스 (빠진 대안이 있어도 번호는 그대로) */
    static int number(Option o) {
        if (Option.KIND_WAIT.equals(o.kind)) return 1;
        if (Option.KIND_WALK.equals(o.kind)) return 2;
        if (Option.KIND_OTHER_BUS.equals(o.kind)) return 3;
        return 0;
    }

    /** 확률 대신 보여 줄 글자: 입석 가능 · 좌석 정보 없음, 확률을 보여 주면 null */
    enum ProbLabel { PROBABILITY, STANDING, SEAT_UNKNOWN }

    static ProbLabel probLabel(Option o) {
        if (o.isStandingOk()) return ProbLabel.STANDING;
        if (o.isSeatUnknown()) return ProbLabel.SEAT_UNKNOWN;
        return ProbLabel.PROBABILITY;
    }

    /** recommendation.kind 가 "지하철"이면 지하철 비교 칸에 '추천' */
    static boolean subwayRecommended(AlternativesResult r) {
        return r.recommendation != null && Option.KIND_SUBWAY.equals(r.recommendation.kind);
    }

    /** {@code recommendation.kind} 와 같은 카드에 '추천' (탈 수 없는 정류장은 제외) */
    static boolean isRecommended(Option o, AlternativesResult r) {
        return !o.skipped && r.recommendation != null && o.kind != null && o.kind.equals(r.recommendation.kind);
    }

    /**
     * 가장 빨리 도착하는 대안보다 몇 분 더 걸리는지 ({@code total_min} 기준).
     * 0 = 가장 빠름, null = 비교할 수 없음 (건너뜀 · 시간 모름).
     */
    @Nullable
    static Integer slowerThanFastest(Option o, List<Option> all) {
        if (o.skipped || o.totalMin == null) return null;
        int fastest = Integer.MAX_VALUE;
        for (Option other : all) {
            if (other == null || other.skipped || other.totalMin == null) continue;
            fastest = Math.min(fastest, other.totalMin);
        }
        return fastest == Integer.MAX_VALUE ? null : o.totalMin - fastest;
    }

    /** 다가오는 차 목록 (11주차 차량별 좌석). 빈 목록이면 '정보 없음 · 배차간격으로 예상' */
    static List<BusRow> busRows(Option o) {
        List<BusRow> rows = new ArrayList<>();
        if (o.buses == null) return rows;
        boolean foundReachable = false;
        for (Bus b : o.buses) {
            if (b == null) continue;
            boolean first = b.reachable && !foundReachable;
            if (b.reachable) foundReachable = true;
            rows.add(new BusRow(b, !b.reachable, first, seats(b), level(b.boardProb)));
        }
        return rows;
    }

    /**
     * 12주차 상태 구분: {@code 0} = 만석, {@code null} 또는 음수 = 좌석 정보 없음.
     * 경기도 버스 API 는 좌석을 모를 때 {@code -1} 을 주므로 만석(0)과 구분합니다.
     */
    static Seats seats(Bus b) {
        if (b.seatsAtStop == null || b.seatsAtStop < 0) return Seats.UNKNOWN;
        return b.seatsAtStop == 0 ? Seats.FULL : Seats.SOME;
    }

    /**
     * 다음 자동 갱신까지 남은 시간(밀리초). 마지막 갱신에서 interval 이 지났으면 0 (바로),
     * interval 이 0 이하면 -1 (자동 갱신 끔).
     */
    static long nextRefreshDelay(long lastUpdatedAt, long now, long interval) {
        if (interval <= 0) return -1;
        if (lastUpdatedAt <= 0) return 0;
        long elapsed = now - lastUpdatedAt;
        if (elapsed < 0) return interval;   // 시계가 뒤로 감 → 한 번 기다림
        return Math.max(0, interval - elapsed);
    }

    /**
     * '첫 차 보내고 다음 차 타기' (기획서 11쪽 ① 기다리기) — 탈 수 있는 첫 차가 만석(빨강)인데
     * 뒤에 오는 차는 탈 만하면(노랑 이상) 그 차를, 아니면 null.
     */
    @Nullable
    static Bus nextBetterBus(Option o) {
        Bus first = firstReachable(o);
        if (first == null || level(first.boardProb) != Level.LOW) return null;
        boolean afterFirst = false;
        for (Bus b : o.buses) {
            if (b == null) continue;
            if (b == first) {
                afterFirst = true;
                continue;
            }
            if (afterFirst && b.reachable && level(b.boardProb) != Level.LOW) return b;
        }
        return null;
    }
}
