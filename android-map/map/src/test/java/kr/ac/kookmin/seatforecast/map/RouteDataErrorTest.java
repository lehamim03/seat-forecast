package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

/** 오류 표시 점검 (503 route_unavailable 문장 그대로), route_source=saved_nearby, 도로 모양 선 고르기 */
public class RouteDataErrorTest {

    @Test
    public void routeUnavailableMessageAsIs() {
        RouteData.ServerError e = RouteData.errorOf(503,
                "{\"error\": {\"code\": \"route_unavailable\", \"message\": \"경로 조회 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.\"}}");
        assertEquals(503, e.status);
        assertEquals("route_unavailable", e.code);
        assertEquals("경로 조회 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
    }

    @Test
    public void htmlErrorPageGetsReadableMessage() {
        RouteData.ServerError e = RouteData.errorOf(502, "<html>Bad Gateway</html>");
        assertEquals("경로 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요. (502)", e.getMessage());
        assertEquals("로그인이 필요합니다.", RouteData.errorOf(401, "").getMessage());
    }

    private static JSONObject fixture(String file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Path.of("src/test/resources/trip", file)),
                java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    public void savedNearbyFromRouteSource() throws Exception {
        JSONObject rec = fixture("trip_recommendations.json");
        RouteData normal = new RouteData(fixture("trip_map.geojson"), fixture("trip_route_choices.json"), rec);
        assertFalse(normal.savedNearby);
        rec.put("route_source", "saved_nearby");
        RouteData nearby = new RouteData(fixture("trip_map.geojson"), fixture("trip_route_choices.json"), rec);
        assertTrue(nearby.savedNearby);
    }

    @Test
    public void shapeUsesRoadLineWhenSnapped() throws Exception {
        RouteData d = new RouteData(fixture("trip_map.geojson"), fixture("trip_route_choices.json"),
                fixture("trip_recommendations.json"));
        RouteData.Feature f = d.transfers().get(0);
        assertSame(f.pts(), d.shape(f));   // 아직 정류장 직선
        List<RouteData.Pt> road = Arrays.asList(new RouteData.Pt(127.1, 37.38), new RouteData.Pt(127.11, 37.39),
                new RouteData.Pt(127.12, 37.40));
        d.road.put(f, road);
        assertSame(road, d.shape(f));
    }
}
