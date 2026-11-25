package kr.ac.kookmin.seatforecast.alternatives;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.util.List;

import kr.ac.kookmin.seatforecast.alternatives.data.AlternativesJson;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Bus;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/** docs/examples/alternatives.json (docs/api.md ③) 을 그대로 읽는지 */
public class AlternativesParseTest {

    static final File DOCS_EXAMPLE = new File("../../docs/examples/alternatives.json");
    static final File DEMO_ASSET = new File("../demo/src/main/assets/alternatives.json");
    static final File DEMO_CASES = new File("../demo/src/main/assets/alt_cases.json");

    static AlternativesResult load(File f) throws IOException, SourceException {
        try (InputStream in = new FileInputStream(f)) {
            return AlternativesJson.parse(in);
        }
    }

    @Test
    public void readsDocsExample() throws Exception {
        AlternativesResult r = load(DOCS_EXAMPLE);
        assertEquals("2026-10-07T07:40:00+09:00", r.generatedAt);
        assertEquals("8800", r.myRoute);
        assertEquals("203000063", r.homeStop.id);
        assertEquals("삼성1차아파트", r.homeStop.name);
        assertNotNull(r.day);
        assertEquals("평일 출퇴근", r.day.kind);
        assertNotNull(r.recommendation);
        assertEquals(Option.KIND_OTHER_BUS, r.recommendation.kind);
        assertEquals("3007", r.recommendation.route);
        assertNull("예시에는 subway 가 없음 → null", r.subway);
    }

    @Test
    public void optionsInScreenOrder() throws Exception {
        List<Option> options = load(DOCS_EXAMPLE).options();
        assertEquals(3, options.size());
        assertEquals(Option.KIND_WAIT, options.get(0).kind);
        assertEquals(Option.KIND_WALK, options.get(1).kind);
        assertEquals(Option.KIND_OTHER_BUS, options.get(2).kind);
    }

    @Test
    public void optionFieldsMatchDocs() throws Exception {
        AlternativesResult r = load(DOCS_EXAMPLE);
        Option walk = r.alternatives.walk;
        assertEquals("8800", walk.route);
        // 정류장 이름은 API 이름 그대로
        assertEquals("매탄위브하늘채.경기아트센터", walk.stop.name);
        assertEquals("04235", walk.stop.mobileNo);
        assertEquals(7, walk.walkMin);
        assertEquals(0.01, walk.boardProb, 1e-9);
        assertEquals("10:36", walk.arriveAt);
        assertEquals(Integer.valueOf(177), walk.totalMin);
        assertFalse(walk.skipped);
        assertEquals("서울시청", walk.alightStop);

        Bus first = walk.buses.get(0);
        assertEquals(1, first.order);
        assertEquals(4, first.etaMin);
        assertEquals(Integer.valueOf(0), first.seatsAtStop);
        assertFalse("도보 7분이라 4분 뒤 오는 첫 차는 못 탐", first.reachable);

        Option other = r.alternatives.otherBus;
        assertEquals("양재역.서초문화예술회관(중)", other.alightStop);
        // 11/18 예시 갱신: 다른 버스 = 3007 · 삼성1차아파트(집 앞) · 도착 때 약 19석
        assertEquals("203000063", other.stop.id);
        assertEquals(0, other.walkMin);
        assertEquals(Integer.valueOf(19), other.buses.get(0).seatsAtStop);
        assertEquals(4, other.routeChoices.size());
        assertTrue(other.mainChoice().tags.contains("추천"));
    }

    @Test
    public void usualIsWaitOption() throws Exception {
        AlternativesResult r = load(DOCS_EXAMPLE);
        assertEquals(Option.KIND_WAIT, r.usual().kind);
        assertEquals("203000063", r.usual().stop.id);
    }

    @Test
    public void readsNullsInCases() throws Exception {
        AlternativesResult r = load(DEMO_CASES);
        Option skipped = r.alternatives.otherBus;
        assertTrue(skipped.skipped);
        assertNull(skipped.arriveAt);
        assertNull(skipped.totalMin);
        assertTrue(skipped.buses.isEmpty());
        assertNull(skipped.mainChoice());
        assertEquals("좌석 모름 (경기도 API 값 -1)", Integer.valueOf(-1), r.alternatives.walk.buses.get(1).seatsAtStop);
        assertNull("좌석 모름 (null)", r.alternatives.wait.buses.get(2).seatsAtStop);
        assertEquals(12, r.alternatives.walk.delayMin);
        assertNotNull(r.subway);
        assertEquals("10:12", r.subway.arriveAt);
        assertEquals(2, r.subway.transfers);
    }

    @Test
    public void missingAlternativesIsEmptyList() throws Exception {
        AlternativesResult r = AlternativesJson.parse(new StringReader(
                "{\"my_route\":\"8800\",\"alternatives\":{\"기다리기\":null,\"걸어가기\":null,\"다른 버스\":null}}"));
        assertTrue(r.options().isEmpty());
        assertNull(r.usual());
        AlternativesResult none = AlternativesJson.parse(new StringReader("{\"my_route\":\"8800\"}"));
        assertTrue(none.options().isEmpty());
    }

    @Test
    public void brokenJsonIsSourceException() {
        try {
            AlternativesJson.parse(new StringReader("{\"alternatives\": [1, 2"));
            fail();
        } catch (SourceException e) {
            assertEquals("대안 데이터 형식이 올바르지 않습니다.", e.getMessage());
        }
        try {
            AlternativesJson.parse(new StringReader(""));
            fail();
        } catch (SourceException e) {
            assertEquals("대안 데이터가 비어 있습니다.", e.getMessage());
        }
    }

    /** 시연 앱 assets 의 alternatives.json 은 docs 예시 복사본 — 원본이 바뀌면 다시 복사 */
    @Test
    public void demoAssetIsSameAsDocs() throws Exception {
        assertArrayEquals(Files.readAllBytes(DOCS_EXAMPLE.toPath()), Files.readAllBytes(DEMO_ASSET.toPath()));
    }
}
