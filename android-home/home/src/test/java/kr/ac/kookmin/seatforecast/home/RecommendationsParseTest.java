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

    /** 출발지 · 도착지 모드 예시 (docs/examples/trip_*.json 을 시연 앱 assets 에 복사한 것) */
    static String readTripExample(String name) throws IOException {
        File f = new File("../demo/src/main/assets/" + name);
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
        assertEquals("삼성1차아파트", r.recommendation.stopName);
        assertTrue(r.recommendation.message.startsWith("다른 버스 (3007번 · 삼성1차아파트)"));

        assertEquals(11, r.ranking.size());
        Option first = r.ranking.get(0);
        assertEquals(Option.KIND_OTHER_BUS, first.kind);
        assertEquals("3007", first.route);
        assertEquals("203000063", first.stop.id);
        assertEquals("삼성1차아파트", first.stop.name);
        assertEquals("04007", first.stop.mobileNo);
        assertEquals(0, first.walkMin);   // 같은 버스 · 비슷한 확률이면 가까운 정류장 먼저
        assertEquals(1.0, first.boardProb, 1e-9);
        assertEquals("10:08", first.arriveAt);
        assertEquals(Integer.valueOf(148), first.totalMin);
        assertEquals(0, first.delayMin);
        assertFalse(first.skipped);
        assertEquals("양재역.서초문화예술회관(중)", first.alightStop);

        Bus bus = first.firstBus();
        assertNotNull(bus);
        assertEquals(1, bus.order);
        assertEquals(6, bus.etaMin);
        assertEquals(Integer.valueOf(19), bus.seatsAtStop);
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

    // ── 출발지 · 도착지 모드 (trip_recommendations.json) ──

    @Test
    public void readsTripExample() throws Exception {
        Recommendations r = parse(readTripExample("trip_recommendations.json"));

        assertEquals("현재 위치", r.origin.name);
        assertEquals(37.385, r.origin.lat, 1e-9);
        assertEquals(127.1233, r.origin.lng, 1e-9);
        assertEquals("국민대학교", r.destination.name);
        assertEquals(37.611, r.destination.lat, 1e-9);
        assertNull(r.myRoute);
        assertEquals("1855", r.homeStop.id);
        assertEquals(Option.KIND_SUBWAY, r.recommendation.kind);
        assertEquals(3, r.ranking.size());

        Option subway = r.ranking.get(0);
        assertEquals(Option.KIND_SUBWAY, subway.kind);
        assertEquals(Option.MODE_SUBWAY, subway.firstMode);
        assertTrue(subway.isSubway());
        assertEquals(Boolean.FALSE, subway.seatLimited);
        assertTrue(subway.isStandingOk());
        assertFalse(subway.showsProbability());
        assertEquals(Integer.valueOf(1), subway.transfers);
        assertEquals("도보 3분 → 수인분당선(서현→선릉) → 2호선(선릉→신설동)", subway.summary);
        assertEquals(37.385, subway.stop.lat, 1e-9);
        assertEquals(127.1233, subway.stop.lng, 1e-9);
        assertTrue(subway.buses.isEmpty());

        Option bus = r.ranking.get(1);
        assertEquals("8100", bus.route);
        assertEquals(Option.MODE_BUS, bus.firstMode);
        assertFalse(bus.isSubway());
        assertEquals(Boolean.TRUE, bus.seatLimited);
        assertEquals(Boolean.TRUE, bus.seatKnown);
        assertTrue(bus.showsProbability());
        assertEquals("서현역.AK프라자", bus.stop.name);
    }

    /** seat_known=false (서울 등록 좌석버스 등) → 확률 대신 "좌석 정보 없음" */
    @Test
    public void seatUnknown() throws Exception {
        Option o = parse("{\"ranking\": [{\"kind\": \"기다리기\", \"route\": \"9711\", \"first_mode\": \"bus\","
                + " \"seat_limited\": true, \"seat_known\": false, \"board_prob\": 0.93}]}").ranking.get(0);
        assertTrue(o.isSeatUnknown());
        assertFalse(o.isStandingOk());
        assertFalse(o.showsProbability());
    }

    /** 예전 응답(새 필드 없음)은 그대로 확률 표시 · 버스로 봄 */
    @Test
    public void oldResponseStillShowsProbability() throws Exception {
        Option o = parse(readExample("recommendations.json")).ranking.get(0);
        assertNull(o.seatLimited);
        assertNull(o.seatKnown);
        assertNull(o.firstMode);
        assertTrue(o.showsProbability());
        assertFalse(o.isSubway());
    }

    /** 모르는 kind · first_mode 가 와도 죽지 않음 (그대로 두고 버스로 봄) */
    @Test
    public void unknownKindAndMode() throws Exception {
        Recommendations r = parse("{\"ranking\": [{\"kind\": \"버스\", \"route\": \"62-1\", \"first_mode\": \"ferry\","
                + " \"seat_limited\": false}], \"origin\": null}");
        Option o = r.ranking.get(0);
        assertEquals("버스", o.kind);
        assertFalse(o.isSubway());
        assertTrue(o.isStandingOk());
        assertNull(r.origin);
    }

    /** kind 만 "지하철" 이고 first_mode 가 없어도 지하철로 */
    @Test
    public void subwayByKindOnly() throws Exception {
        Option o = parse("{\"ranking\": [{\"kind\": \"지하철\", \"route\": \"4호선\"}]}").ranking.get(0);
        assertTrue(o.isSubway());
    }

    /** 시연 앱 assets 복사본이 docs/examples 원본과 같은지 (원본이 바뀌면 다시 복사) */
    @Test
    public void demoAssetMatchesDocs() throws IOException {
        byte[] asset = Files.readAllBytes(new File("../demo/src/main/assets/recommendations.json").toPath());
        assertArrayEquals(readExample("recommendations.json").getBytes(StandardCharsets.UTF_8), asset);
    }
}
