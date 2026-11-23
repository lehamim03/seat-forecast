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

    /**
     * 13주차 2차 '확률 표시 확인': 서버(live_recommend.api_result)는 확률을 소수 둘째 자리로 반올림해 보냄.
     * 화면 % 와 색 단계가 경계에서 어긋나지 않는지.
     */
    @Test
    public void serverProbabilitiesShowConsistently() {
        assertEquals("36%", AltFormat.percent(0.355));
        assertEquals("70%", AltFormat.percent(0.70));
        assertEquals(AltFormat.Level.HIGH, AltFormat.level(0.70));
        assertEquals("69%", AltFormat.percent(0.69));
        assertEquals(AltFormat.Level.MID, AltFormat.level(0.69));
        assertEquals("40%", AltFormat.percent(0.40));
        assertEquals(AltFormat.Level.MID, AltFormat.level(0.40));
        assertEquals("39%", AltFormat.percent(0.39));
        assertEquals(AltFormat.Level.LOW, AltFormat.level(0.39));
        // live_recommend 는 좌석을 모르면 탑승 확률 0.5 로 둠 → 노랑 · 50%
        assertEquals("50%", AltFormat.percent(0.5));
        assertEquals(AltFormat.Level.MID, AltFormat.level(0.5));
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

    /** 12주차 2차: 만석(0) 과 좌석 정보 없음(-1 · null) 을 구분 */
    @Test
    public void fullAndUnknownSeatsAreDifferent() throws Exception {
        Bus b = new Bus();
        b.seatsAtStop = 0;
        assertEquals(AltFormat.Seats.FULL, AltFormat.seats(b));
        b.seatsAtStop = -1;
        assertEquals("경기도 API 는 좌석을 모르면 -1", AltFormat.Seats.UNKNOWN, AltFormat.seats(b));
        b.seatsAtStop = null;
        assertEquals(AltFormat.Seats.UNKNOWN, AltFormat.seats(b));
        b.seatsAtStop = 1;
        assertEquals(AltFormat.Seats.SOME, AltFormat.seats(b));

        List<AltFormat.BusRow> walk = AltFormat.busRows(cases().alternatives.walk);
        assertEquals(AltFormat.Seats.FULL, walk.get(0).seats);
        assertEquals(AltFormat.Seats.UNKNOWN, walk.get(1).seats);
        List<AltFormat.BusRow> wait = AltFormat.busRows(cases().alternatives.wait);
        assertEquals(AltFormat.Seats.FULL, wait.get(0).seats);
        assertEquals(AltFormat.Seats.SOME, wait.get(1).seats);
        assertEquals(AltFormat.Seats.UNKNOWN, wait.get(2).seats);
    }

    /** 12주차 1차: 1분 자동 갱신 — 다음 갱신까지 남은 시간 */
    @Test
    public void nextRefreshDelay() {
        long min = 60_000L;
        assertEquals("아직 한 번도 안 불렀으면 바로", 0, AltFormat.nextRefreshDelay(0, 5_000, min));
        assertEquals(60_000, AltFormat.nextRefreshDelay(100_000, 100_000, min));
        assertEquals(15_000, AltFormat.nextRefreshDelay(100_000, 145_000, min));
        assertEquals("다른 화면에 갔다 온 동안 1분이 지났으면 바로", 0, AltFormat.nextRefreshDelay(100_000, 400_000, min));
        assertEquals("시계가 뒤로 가면 한 번 기다림", 60_000, AltFormat.nextRefreshDelay(100_000, 50_000, min));
        assertEquals("0 이하 = 자동 갱신 끔", -1, AltFormat.nextRefreshDelay(100_000, 200_000, 0));
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
