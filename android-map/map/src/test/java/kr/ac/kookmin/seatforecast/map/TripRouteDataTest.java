package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;
import org.junit.Test;

/** 출발지 · 도착지 모드 (docs/examples/trip_* — 서버 /map · /route-choices · /recommendations 형식) */
public class TripRouteDataTest {
    private static JSONObject fixture(String file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Path.of("src/test/resources/trip", file)),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    private RouteData data() throws Exception {
        return new RouteData(fixture("trip_map.geojson"), fixture("trip_route_choices.json").put("live", true),
                fixture("trip_recommendations.json"));
    }

    @Test
    public void tripModeCardsAndLines() throws Exception {
        RouteData d = data();
        assertTrue(d.trip);
        assertTrue(d.live);
        // 추천 = recommendation (수인분당선 · 서현), 다음 후보 = 그 밖에 가장 빠른 것
        assertEquals("수인분당선", d.alternative.optString("route"));
        assertNotNull(d.usual);
        assertEquals("8100", d.usual.optString("route"));
        // 상위 경로 선 3개, main 은 1순위 하나
        assertEquals(3, d.transfers().size());
        assertEquals(1, d.transfers().stream().filter(f -> f.p().optBoolean("main")).count());
        // 출발지 · 도착지 점
        assertEquals(1, d.features.stream().filter(f -> f.kind().equals("me")).count());
        assertEquals(1, d.features.stream().filter(f -> f.kind().equals("destination")).count());
    }

    /** 출발지 · 도착지 모드: 환승 선은 summary 로, 경로 이름도 summary, 도착 시각은 그 경로의 arrive_at */
    @Test
    public void choicesMatchBySummary() throws Exception {
        RouteData d = data();
        RouteData.Choice subway = d.choices.get("수인분당선").get(0);
        assertTrue(d.hasShape("수인분당선", subway));
        assertEquals("도보 3분 → 수인분당선(서현→선릉) → 2호선(선릉→신설동)", d.label(subway));
        assertEquals("09:10", d.eta(d.best("수인분당선"), subway));
        RouteData.Choice bus = d.choices.get("8100").get(0);
        assertFalse(d.hasShape("수인분당선", bus));
    }

    @Test
    public void mapExampleReadsOriginAndDestination() throws Exception {
        // MapExample 은 카카오 LatLng 를 쓰므로 GeoJSON 속성만 확인
        JSONObject geo = fixture("trip_map.geojson");
        int me = 0, dest = 0;
        for (int i = 0; i < geo.getJSONArray("features").length(); i++) {
            String kind = geo.getJSONArray("features").getJSONObject(i).getJSONObject("properties").getString("kind");
            if (kind.equals("me")) me++;
            if (kind.equals("destination")) dest++;
        }
        assertEquals(1, me);
        assertEquals(1, dest);
    }

    /** 예전 모드 응답(my_route 있음)은 그대로 */
    @Test
    public void oldModeIsNotTrip() throws Exception {
        JSONObject rec = new JSONObject(new String(Files.readAllBytes(Path.of("../../docs/examples", "recommendations.json")),
                java.nio.charset.StandardCharsets.UTF_8));
        JSONObject geo = new JSONObject(new String(Files.readAllBytes(Path.of("../../docs/examples", "map.geojson")),
                java.nio.charset.StandardCharsets.UTF_8));
        JSONObject routes = new JSONObject(new String(Files.readAllBytes(Path.of("../../docs/examples", "route_choices.json")),
                java.nio.charset.StandardCharsets.UTF_8));
        RouteData d = new RouteData(geo, routes, rec);
        assertFalse(d.trip);
        RouteData.Choice c = d.choices.get("8800").get(0);
        assertEquals(c.key(), d.label(c));
    }
}
