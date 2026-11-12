package kr.ac.kookmin.seatforecast.home;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import kr.ac.kookmin.seatforecast.home.data.RecommendationJson;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

public class RecommendationsParseTest {

    /** 저장소의 docs/examples 파일 (테스트는 home/ 폴더에서 실행됨) */
    static String readExample(String name) throws IOException {
        File f = new File("../../docs/examples/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    static Recommendations parse(String json) throws SourceException {
        return RecommendationJson.parse(new StringReader(json));
    }

    @Test
    public void readsDocsExample() throws Exception {
        Recommendations r = parse(readExample("recommendations.json"));

        assertEquals("2026-10-07T07:40:00+09:00", r.generatedAt);
        assertEquals("8800", r.myRoute);
        assertEquals("203000063", r.homeStop.id);
        assertEquals("삼성1차아파트", r.homeStop.name);
        assertNull(r.homeStop.mobileNo);
        assertEquals("평일 출퇴근 시간대", r.day.label);
        assertEquals(1.0, r.day.dropFactor, 1e-9);
        assertEquals("다른 버스", r.recommendation.kind);
        assertEquals("3007", r.recommendation.route);
        assertEquals("자유총연맹", r.recommendation.stopName);
        assertTrue(r.recommendation.message.startsWith("다른 버스 (3007번 · 자유총연맹)"));

        assertEquals(11, r.ranking.size());
        Option first = r.ranking.get(0);
        assertEquals(Option.KIND_OTHER_BUS, first.kind);
        assertEquals("3007", first.route);
        assertEquals("203000161", first.stop.id);
        assertEquals("자유총연맹", first.stop.name);
        assertEquals("04236", first.stop.mobileNo);
        assertEquals(3, first.walkMin);
        assertEquals(1.0, first.boardProb, 1e-9);
        assertEquals("10:08", first.arriveAt);
        assertEquals(Integer.valueOf(148), first.totalMin);
        assertEquals(0, first.delayMin);
        assertFalse(first.skipped);
        assertEquals("양재역.서초문화예술회관(중)", first.alightStop);

        Bus bus = first.firstBus();
        assertNotNull(bus);
        assertEquals(1, bus.order);
        assertEquals(4, bus.etaMin);
        assertEquals(Integer.valueOf(21), bus.seatsAtStop);
        assertTrue(bus.reachable);

        assertEquals(4, first.routeChoices.size());
        assertEquals(Arrays.asList("추천", "최소환승"), first.routeChoices.get(0).tags);
        assertEquals(2, first.routeChoices.get(0).transfers);
        assertTrue(first.routeChoices.get(2).tags.isEmpty());
    }

    @Test
    public void keepsStopNamesAsApi() throws Exception {
        Recommendations r = parse(readExample("recommendations.json"));
        boolean found = false;
        for (Option o : r.ranking) {
            if ("203000162".equals(o.stop.id)) {
                assertEquals("매탄위브하늘채.경기아트센터", o.stop.name);
                found = true;
            }
        }
        assertTrue(found);
    }

    @Test
    public void rankingOrderIsKept() throws Exception {
        Recommendations r = parse(readExample("recommendations.json"));
        String[] routes = new String[r.ranking.size()];
        for (int i = 0; i < routes.length; i++) routes[i] = r.ranking.get(i).route;
        assertArrayEquals(new String[]{"3007", "3007", "7001", "7001", "3008", "7001", "3008", "3008",
                "8800", "8800", "3007"}, routes);
    }

    @Test
    public void emptyBusesAndUnreachableFirstBus() throws Exception {
        Recommendations r = parse(readExample("recommendations.json"));
        Option o7001 = r.ranking.get(2);
        assertEquals("7001", o7001.route);
        assertTrue(o7001.buses.isEmpty());
        assertNull(o7001.firstBus());

        Option walk = r.ranking.get(9);
        assertEquals(Option.KIND_WALK, walk.kind);
        assertFalse(walk.firstBus().reachable);
        assertTrue(walk.buses.get(1).reachable);
    }

    @Test
    public void nullFields() throws Exception {
        String json = "{\"generated_at\":\"2026-10-07T07:40:00+09:00\",\"my_route\":\"8800\","
                + "\"home_stop\":{\"id\":\"203000063\",\"name\":\"삼성1차아파트\"},"
                + "\"day\":{\"label\":\"평일 출퇴근 시간대\",\"kind\":\"평일 출퇴근\",\"drop_factor\":1.0,\"headway_factor\":1.0},"
                + "\"recommendation\":null,"
                + "\"ranking\":[{\"kind\":\"기다리기\",\"route\":\"8800\","
                + "\"stop\":{\"id\":\"203000063\",\"name\":\"삼성1차아파트\",\"mobile_no\":\"04007\"},"
                + "\"walk_min\":0,\"board_prob\":0.0,\"arrive_at\":null,\"total_min\":null,\"delay_min\":30,"
                + "\"skipped\":true,\"alight_stop\":null,"
                + "\"buses\":[{\"order\":1,\"eta_min\":6,\"seats_at_stop\":null,\"board_prob\":0.0,\"reachable\":true}],"
                + "\"route_choices\":null}]}";
        Recommendations r = parse(json);

        assertNull(r.recommendation);
        Option o = r.ranking.get(0);
        assertNull(o.arriveAt);
        assertNull(o.totalMin);
        assertNull(o.alightStop);
        assertTrue(o.skipped);
        assertEquals(30, o.delayMin);
        assertNull(o.firstBus().seatsAtStop);
        assertNull(o.routeChoices);
    }

    @Test
    public void missingRankingIsEmpty() throws Exception {
        Recommendations r = parse("{\"my_route\":\"8800\",\"ranking\":null}");
        assertTrue(r.rankingOrEmpty().isEmpty());
    }

    @Test
    public void brokenJsonIsSourceException() {
        try {
            parse("{\"ranking\": [");
            fail();
        } catch (SourceException e) {
            assertEquals("추천 데이터 형식이 올바르지 않습니다.", e.getMessage());
        }
    }

    /** 시연 앱 assets 복사본이 docs/examples 원본과 같은지 (원본이 바뀌면 다시 복사) */
    @Test
    public void demoAssetMatchesDocs() throws IOException {
        byte[] asset = Files.readAllBytes(new File("../demo/src/main/assets/recommendations.json").toPath());
        assertArrayEquals(readExample("recommendations.json").getBytes(StandardCharsets.UTF_8), asset);
    }
}
