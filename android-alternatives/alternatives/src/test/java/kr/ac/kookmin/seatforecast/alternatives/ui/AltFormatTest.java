package kr.ac.kookmin.seatforecast.alternatives.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;

import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesJson;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/** 실시간 대안 화면 규칙 — 평소 버스 상태 · 추천 카드 · 도착 비교 · 차량별 좌석 */
public class AltFormatTest {

    private static AlternativesResult load(String path) throws Exception {
        try (InputStream in = new FileInputStream(new File(path))) {
            return AlternativesJson.parse(in);
        }
    }

    private static AlternativesResult docs() throws Exception {
        return load("../../docs/examples/alternatives.json");
    }

    private static AlternativesResult cases() throws Exception {
        return load("../demo/src/main/assets/alt_cases.json");
    }

    @Test
    public void levelBoundariesSameAsHomeBadge() {
        assertEquals(AltFormat.Level.HIGH, AltFormat.level(0.7));
        assertEquals(AltFormat.Level.MID, AltFormat.level(0.6999));
        assertEquals(AltFormat.Level.MID, AltFormat.level(0.4));
        assertEquals(AltFormat.Level.LOW, AltFormat.level(0.3999));
        assertEquals("2%", AltFormat.percent(0.02));
        assertEquals("100%", AltFormat.percent(1.0));
        assertEquals("0%", AltFormat.percent(-0.3));
        assertEquals("100%", AltFormat.percent(1.7));
        assertEquals("0%", AltFormat.percent(Double.NaN));
    }

    @Test
    public void timeFromGeneratedAt() {
        assertEquals("07:40", AltFormat.time("2026-10-07T07:40:00+09:00"));
        assertNull(AltFormat.time("07:40"));
        assertNull(AltFormat.time(null));
    }

    /** docs 예시: 평소 8800 · 6분 후 · 0석 · 2% → 만석 */
    @Test
    public void docsExampleIsFull() throws Exception {
        AltFormat.Banner b = AltFormat.banner(docs());
        assertEquals(AltFormat.Status.FULL, b.status);
        assertEquals("8800", b.route);
        assertEquals(6, b.bus.etaMin);
        assertEquals(Integer.valueOf(0), b.bus.seatsAtStop);
        assertEquals(0.02, b.prob, 1e-9);
    }

    @Test
    public void statusByUsualBusProbability() throws Exception {
        AlternativesResult r = docs();
        r.usual().boardProb = 0.92;
        assertEquals(AltFormat.Status.RELAXED, AltFormat.banner(r).status);
        r.usual().boardProb = 0.55;
        assertEquals(AltFormat.Status.RISK, AltFormat.banner(r).status);
        r.usual().skipped = true;
        assertEquals(AltFormat.Status.SKIPPED, AltFormat.banner(r).status);
        r.alternatives.wait = null;
        AltFormat.Banner unknown = AltFormat.banner(r);
        assertEquals(AltFormat.Status.UNKNOWN, unknown.status);
        assertEquals("평소 버스가 없어도 노선은 my_route 로", "8800", unknown.route);
        assertNull(unknown.bus);
    }

    @Test
    public void numbersFollowPlanSlide() throws Exception {
        List<Option> options = docs().options();
        assertEquals(1, AltFormat.number(options.get(0)));
        assertEquals(2, AltFormat.number(options.get(1)));
        assertEquals(3, AltFormat.number(options.get(2)));
        // 걸어가기가 빠져도 다른 버스는 ③ 그대로
        AlternativesResult r = docs();
        r.alternatives.walk = null;
        assertEquals(3, AltFormat.number(r.options().get(1)));
    }

    @Test
    public void recommendedMatchesRecommendationKind() throws Exception {
        AlternativesResult r = docs();
        assertFalse(AltFormat.isRecommended(r.alternatives.wait, r));
        assertFalse(AltFormat.isRecommended(r.alternatives.walk, r));
        assertTrue(AltFormat.isRecommended(r.alternatives.otherBus, r));
        r.alternatives.otherBus.skipped = true;
        assertFalse("탈 수 없는 정류장에는 추천 표시 안 함", AltFormat.isRecommended(r.alternatives.otherBus, r));
        r.recommendation = null;
        assertFalse(AltFormat.isRecommended(r.alternatives.wait, r));
    }

    /** docs: 기다리기 176분 · 걸어가기 177분 · 다른 버스 148분 */
    @Test
    public void comparedWithFastest() throws Exception {
        List<Option> all = docs().options();
        assertEquals(Integer.valueOf(28), AltFormat.slowerThanFastest(all.get(0), all));
        assertEquals(Integer.valueOf(29), AltFormat.slowerThanFastest(all.get(1), all));
        assertEquals(Integer.valueOf(0), AltFormat.slowerThanFastest(all.get(2), all));

        // 건너뛰는 대안은 비교에서 빠짐
        List<Option> c = cases().options();
        assertEquals(Integer.valueOf(0), AltFormat.slowerThanFastest(c.get(0), c));
        assertEquals(Integer.valueOf(13), AltFormat.slowerThanFastest(c.get(1), c));
        assertNull(AltFormat.slowerThanFastest(c.get(2), c));
    }

    /** 차량별 좌석: 걸어가는 동안 지나가는 첫 차는 흐리게, 다음 차에 '탈 차' */
    @Test
    public void busRowsMarkMissedAndFirstReachable() throws Exception {
        Option walk = docs().alternatives.walk;
        List<AltFormat.BusRow> rows = AltFormat.busRows(walk);
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).missed);
        assertFalse(rows.get(0).firstReachable);
        assertEquals(AltFormat.Seats.FULL, rows.get(0).seats);
        assertFalse(rows.get(1).missed);
        assertTrue(rows.get(1).firstReachable);
        assertSame(walk.buses.get(1), AltFormat.firstReachable(walk));

        Option other = docs().alternatives.otherBus;
        AltFormat.BusRow seats = AltFormat.busRows(other).get(0);
        assertEquals(AltFormat.Seats.SOME, seats.seats);
        assertEquals(AltFormat.Level.HIGH, seats.level);

        Option walkCase = cases().alternatives.walk;
        assertEquals(AltFormat.Seats.UNKNOWN, AltFormat.busRows(walkCase).get(1).seats);
        assertTrue("buses: [] → 목록 없음(배차간격으로 예상)", AltFormat.busRows(cases().alternatives.otherBus).isEmpty());
    }

    /** 기획서 11쪽 '첫 차 보내고 다음 8800 타기' */
    @Test
    public void skipFirstBusWhenNextHasSeats() throws Exception {
        Bus next = AltFormat.nextBetterBus(cases().alternatives.wait);
        assertEquals(2, next.order);
        assertEquals(12, next.etaMin);
        assertEquals(Integer.valueOf(18), next.seatsAtStop);
        // docs 예시는 다음 차도 0석 → 안내 안 함
        assertNull(AltFormat.nextBetterBus(docs().alternatives.wait));
        // 첫 차가 탈 만하면 안내 안 함
        assertNull(AltFormat.nextBetterBus(docs().alternatives.otherBus));
    }
}
