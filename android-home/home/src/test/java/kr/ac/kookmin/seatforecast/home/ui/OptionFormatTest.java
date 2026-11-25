package kr.ac.kookmin.seatforecast.home.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.data.RecommendationJson;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import kr.ac.kookmin.seatforecast.home.ui.OptionFormat.BusLine;
import kr.ac.kookmin.seatforecast.home.ui.OptionFormat.BusLineKind;
import kr.ac.kookmin.seatforecast.home.ui.OptionFormat.Level;

public class OptionFormatTest {

    private static Recommendations read(String path) throws Exception {
        String json = new String(Files.readAllBytes(new File(path).toPath()), StandardCharsets.UTF_8);
        return RecommendationJson.parse(new StringReader(json));
    }

    private static Recommendations docsExample() throws Exception {
        return read("../../docs/examples/recommendations.json");
    }

    private static Recommendations demoCases() throws Exception {
        return read("../demo/src/main/assets/home_cases.json");
    }

    private static Bus bus(int order, int eta, Integer seats, double prob, boolean reachable) {
        Bus b = new Bus();
        b.order = order;
        b.etaMin = eta;
        b.seatsAtStop = seats;
        b.boardProb = prob;
        b.reachable = reachable;
        return b;
    }

    private static Option option(Bus... buses) {
        Option o = new Option();
        o.buses = new ArrayList<>(Arrays.asList(buses));
        return o;
    }

    // ── 배지 단계: 70% 이상 초록 · 40~70% 노랑 · 40% 미만 빨강 ──

    @Test
    public void levelBoundaries() {
        assertEquals(Level.HIGH, OptionFormat.level(1.0));
        assertEquals(Level.HIGH, OptionFormat.level(0.7));
        assertEquals(Level.MID, OptionFormat.level(0.6999));
        assertEquals(Level.MID, OptionFormat.level(0.4));
        assertEquals(Level.LOW, OptionFormat.level(0.3999));
        assertEquals(Level.LOW, OptionFormat.level(0.0));
    }

    @Test
    public void percentRoundsAndClamps() {
        assertEquals("100%", OptionFormat.percent(1.0));
        assertEquals("95%", OptionFormat.percent(0.95));
        assertEquals("2%", OptionFormat.percent(0.02));
        assertEquals("1%", OptionFormat.percent(0.005));   // 반올림
        assertEquals("0%", OptionFormat.percent(0.0));
        assertEquals("0%", OptionFormat.percent(-0.1));
        assertEquals("100%", OptionFormat.percent(1.2));
        assertEquals("0%", OptionFormat.percent(Double.NaN));
    }

    @Test
    public void docsExampleBadges() throws Exception {
        List<Option> r = docsExample().ranking;
        // 3007 · 자유총연맹 1.0 → 초록 100%
        assertEquals(Level.HIGH, OptionFormat.level(r.get(0).boardProb));
        assertEquals("100%", OptionFormat.percent(r.get(0).boardProb));
        // 7001 · 삼성1차아파트 0.5 → 노랑 50%
        assertEquals(Level.MID, OptionFormat.level(r.get(2).boardProb));
        assertEquals("50%", OptionFormat.percent(r.get(2).boardProb));
        // 기다리기 8800 0.02 → 빨강 2%
        assertEquals("8800", r.get(8).route);
        assertEquals(Level.LOW, OptionFormat.level(r.get(8).boardProb));
        assertEquals("2%", OptionFormat.percent(r.get(8).boardProb));
    }

    // ── 셋째 줄: 탈 수 있는 첫 차 ──

    @Test
    public void busLineShowsFirstReachableBus() {
        Bus first = bus(1, 4, 21, 1.0, true);
        BusLine line = OptionFormat.busLine(option(first, bus(2, 12, 30, 1.0, true)));
        assertEquals(BusLineKind.BUS, line.kind);
        assertSame(first, line.bus);
        assertFalse(line.missedEarlier);
    }

    @Test
    public void busLineSkipsBusThatPassesWhileWalking() {
        // 걸어가기 8800 · 매탄: 첫 차는 걷는 동안 지나가고, board_prob 은 다음 차 기준
        Bus second = bus(2, 10, 0, 0.01, true);
        BusLine line = OptionFormat.busLine(option(bus(1, 4, 0, 0.06, false), second));
        assertEquals(BusLineKind.BUS, line.kind);
        assertSame(second, line.bus);
        assertTrue(line.missedEarlier);
    }

    @Test
    public void busLineWhenNoBusIsReachable() {
        BusLine line = OptionFormat.busLine(option(bus(1, 2, 23, 1.0, false)));
        assertEquals(BusLineKind.NONE_REACHABLE, line.kind);
        assertNull(line.bus);
    }

    @Test
    public void busLineWithoutBuses() {
        assertEquals(BusLineKind.NO_INFO, OptionFormat.busLine(option()).kind);
        Option o = new Option();
        o.buses = null;
        assertEquals(BusLineKind.NO_INFO, OptionFormat.busLine(o).kind);
    }

    @Test
    public void docsExampleBusLines() throws Exception {
        List<Option> r = docsExample().ranking;
        int noInfo = 0;
        for (Option o : r) if (OptionFormat.busLine(o).kind == BusLineKind.NO_INFO) noInfo++;
        assertEquals(6, noInfo);   // 11개 중 6개가 buses: []

        assertEquals(BusLineKind.BUS, OptionFormat.busLine(r.get(0)).kind);
        assertEquals("걸어가기", r.get(9).kind);
        assertTrue(OptionFormat.busLine(r.get(9)).missedEarlier);
        assertEquals("매탄위브하늘채.경기아트센터", r.get(10).stop.name);
        assertEquals(BusLineKind.NONE_REACHABLE, OptionFormat.busLine(r.get(10)).kind);
    }

    // ── 12주차: 받은 뒤 시간이 지나면 남은 분을 줄임 ──

    private static Option walking(int walkMin, Bus... buses) {
        Option o = option(buses);
        o.walkMin = walkMin;
        return o;
    }

    @Test
    public void busLineCountsDownAfterReceiving() {
        Option o = walking(3, bus(1, 6, 21, 0.95, true));
        assertEquals(6, OptionFormat.busLine(o, 0).etaMin);
        BusLine later = OptionFormat.busLine(o, 2);
        assertEquals(BusLineKind.BUS, later.kind);
        assertEquals(4, later.etaMin);
        assertFalse(later.missedEarlier);
        assertEquals(6, later.bus.etaMin);   // 받은 값은 그대로
    }

    @Test
    public void busLineBecomesUnreachableWhenLeftIsShorterThanWalk() {
        // 도보 3분: 6분 → 4분 지나 2분 남음 → 이제 걸어가도 못 탐 → 다음 차
        Option o = walking(3, bus(1, 6, 21, 0.95, true), bus(2, 14, 30, 0.9, true));
        BusLine l = OptionFormat.busLine(o, 4);
        assertEquals(BusLineKind.BUS, l.kind);
        assertTrue(l.missedEarlier);
        assertEquals(2, l.bus.order);
        assertEquals(10, l.etaMin);
    }

    @Test
    public void busLineDropsBusesThatAlreadyPassed() {
        // 도보 0분: 첫 차 2분 → 3분 지나 이미 지나감 (놓친 차로 세지 않음)
        Option o = walking(0, bus(1, 2, 5, 0.3, true), bus(2, 8, 20, 0.9, true));
        BusLine l = OptionFormat.busLine(o, 3);
        assertEquals(2, l.bus.order);
        assertFalse(l.missedEarlier);
        assertEquals(5, l.etaMin);
    }

    @Test
    public void busLineWhenEveryBusPassed() {
        Option o = walking(0, bus(1, 2, 5, 0.3, true), bus(2, 4, 20, 0.9, true));
        assertEquals(BusLineKind.PASSED, OptionFormat.busLine(o, 5).kind);
        // 정보가 처음부터 없으면 계속 NO_INFO
        assertEquals(BusLineKind.NO_INFO, OptionFormat.busLine(walking(0), 30).kind);
    }

    @Test
    public void busLineAtArrivalShowsZero() {
        Option o = walking(0, bus(1, 3, 5, 0.3, true));
        BusLine l = OptionFormat.busLine(o, 3);
        assertEquals(BusLineKind.BUS, l.kind);
        assertEquals(0, l.etaMin);   // '곧 도착'
    }

    @Test
    public void elapsedZeroKeepsServerReachable() {
        // 지난 시간이 0이면 서버 reachable 그대로 (11주차 동작과 같음)
        Option o = walking(7, bus(1, 4, 5, 0.9, false), bus(2, 12, 20, 0.55, true));
        BusLine l = OptionFormat.busLine(o, 0);
        assertTrue(l.missedEarlier);
        assertEquals(12, l.etaMin);
        assertEquals(BusLineKind.NONE_REACHABLE, OptionFormat.busLine(o, 6).kind);   // 12−6=6 < 도보 7
    }

    // ── 1순위 · 건너뜀 ──

    @Test
    public void firstTagOnlyForFirstBoardableOption() {
        Option o = new Option();
        Option other = new Option();
        assertTrue(OptionFormat.isFirst(o, o));
        assertFalse(OptionFormat.isFirst(other, o));
        assertFalse(OptionFormat.isFirst(o, null));
        o.skipped = true;
        assertFalse(OptionFormat.isFirst(o, o));
        assertFalse(OptionFormat.showBadge(o));
    }

    @Test
    public void demoCasesCoverEveryCardState() throws Exception {
        List<Option> r = demoCases().ranking;
        assertEquals(6, r.size());
        assertEquals(12, r.get(0).delayMin);
        assertEquals(Level.HIGH, OptionFormat.level(r.get(0).boardProb));
        assertTrue(OptionFormat.busLine(r.get(1)).missedEarlier);
        assertEquals(Level.MID, OptionFormat.level(r.get(1).boardProb));
        assertEquals(BusLineKind.NO_INFO, OptionFormat.busLine(r.get(2)).kind);
        BusLine unknownSeats = OptionFormat.busLine(r.get(3));
        assertNull(unknownSeats.bus.seatsAtStop);
        assertEquals(0, unknownSeats.bus.etaMin);
        assertEquals(Level.LOW, OptionFormat.level(r.get(3).boardProb));
        assertEquals(BusLineKind.NONE_REACHABLE, OptionFormat.busLine(r.get(4)).kind);
        assertTrue(r.get(5).skipped);
        assertNull(r.get(5).arriveAt);
        assertFalse(OptionFormat.showBadge(r.get(5)));
    }

    // ── 출발지 · 도착지 모드: 배지 · 지하철 · 종류 · 경로 한 줄 ──

    private static Option trip(Boolean seatLimited, Boolean seatKnown, String firstMode, String kind) {
        Option o = new Option();
        o.seatLimited = seatLimited;
        o.seatKnown = seatKnown;
        o.firstMode = firstMode;
        o.kind = kind;
        o.boardProb = 0.73;
        return o;
    }

    @Test
    public void badgeByTripFields() {
        assertEquals(OptionFormat.Badge.PROBABILITY, OptionFormat.badge(trip(true, true, "bus", "기다리기")));
        assertEquals(OptionFormat.Badge.STANDING, OptionFormat.badge(trip(false, false, "subway", "지하철")));
        assertEquals(OptionFormat.Badge.STANDING, OptionFormat.badge(trip(false, null, "bus", "기다리기")));
        assertEquals(OptionFormat.Badge.SEAT_UNKNOWN, OptionFormat.badge(trip(true, false, "bus", "기다리기")));
        // 예전 응답 (필드 없음) → 확률
        assertEquals(OptionFormat.Badge.PROBABILITY, OptionFormat.badge(trip(null, null, null, "다른 버스")));
        // 건너뛰는 정류장은 배지 없음
        Option skipped = trip(false, null, "bus", "기다리기");
        skipped.skipped = true;
        assertEquals(OptionFormat.Badge.NONE, OptionFormat.badge(skipped));
        assertFalse(OptionFormat.showBadge(skipped));
    }

    @Test
    public void subwayLine() {
        assertEquals(BusLineKind.SUBWAY, OptionFormat.busLine(trip(false, false, "subway", "지하철")).kind);
        // 버스인데 차량 정보가 없으면 예전처럼
        assertEquals(BusLineKind.NO_INFO, OptionFormat.busLine(trip(true, true, "bus", "기다리기")).kind);
    }

    @Test
    public void kindAndSummary() {
        Option o = trip(false, false, "subway", " 지하철 ");
        assertEquals("지하철", OptionFormat.kindLabel(o));
        assertEquals("버스", OptionFormat.kindLabel(trip(true, true, "bus", "버스")));   // 모르는 값은 그대로
        assertNull(OptionFormat.kindLabel(trip(true, true, "bus", null)));
        assertNull(OptionFormat.summary(o));
        o.summary = "  도보 3분 → 수인분당선(서현→선릉)  ";
        assertEquals("도보 3분 → 수인분당선(서현→선릉)", OptionFormat.summary(o));
    }

    @Test
    public void tripExampleCards() throws Exception {
        Recommendations r = read("../demo/src/main/assets/trip_recommendations.json");
        assertEquals(OptionFormat.Badge.STANDING, OptionFormat.badge(r.ranking.get(0)));
        assertEquals(BusLineKind.SUBWAY, OptionFormat.busLine(r.ranking.get(0)).kind);
        assertEquals(OptionFormat.Badge.PROBABILITY, OptionFormat.badge(r.ranking.get(1)));
        assertEquals(BusLineKind.BUS, OptionFormat.busLine(r.ranking.get(1)).kind);
    }
}
