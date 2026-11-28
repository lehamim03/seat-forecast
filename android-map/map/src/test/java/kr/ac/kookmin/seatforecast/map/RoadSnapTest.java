package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** 경로 선을 도로 모양으로 (RoadSnap) — TMAP 응답 나누기, U턴 · 지하철 구간은 직선, 요청 본문 */
public class RoadSnapTest {

    private static RouteData.Pt pt(double lon, double lat) {
        return new RouteData.Pt(lon, lat);
    }

    private static JSONObject point() throws Exception {
        return new JSONObject().put("type", "Feature")
                .put("geometry", new JSONObject().put("type", "Point").put("coordinates", new JSONArray("[127.0,37.0]")));
    }

    private static JSONObject line(double[][] coords) throws Exception {
        JSONArray c = new JSONArray();
        for (double[] p : coords) c.put(new JSONArray().put(p[0]).put(p[1]));
        return new JSONObject().put("type", "Feature")
                .put("geometry", new JSONObject().put("type", "LineString").put("coordinates", c));
    }

    /** TMAP routeSequential30 순서: Point 출발, Point 경유1, Line(출발→경유1), Point 도착, Line(경유1→도착) */
    @Test
    public void splitsResponseIntoSegmentsAfterEachPoint() throws Exception {
        JSONObject res = new JSONObject().put("features", new JSONArray()
                .put(point())
                .put(point())
                .put(line(new double[][]{{127.0, 37.0}, {127.001, 37.0}}))
                .put(line(new double[][]{{127.001, 37.0}, {127.002, 37.0}}))
                .put(point())
                .put(line(new double[][]{{127.002, 37.0}, {127.003, 37.001}})));
        List<List<RouteData.Pt>> segs = RoadSnap.segments(res);
        assertEquals(2, segs.size());
        // 한 구간이 선 여러 개로 와도 이어 붙이고, 겹치는 점은 한 번만
        assertEquals(Arrays.asList(pt(127.0, 37.0), pt(127.001, 37.0), pt(127.002, 37.0)), segs.get(0));
        assertEquals(2, segs.get(1).size());
    }

    @Test
    public void keepsRoadSegmentThatFollowsStraightLine() {
        RouteData.Pt a = pt(127.000, 37.000), b = pt(127.010, 37.000);   // 약 0.89km
        // 도로가 살짝 휘어 있음 (직선의 1.1배 정도)
        List<RouteData.Pt> road = Arrays.asList(a, pt(127.005, 37.002), b);
        List<RouteData.Pt> out = RoadSnap.merge(Arrays.asList(a, b), List.of(road));
        assertEquals(3, out.size());
        assertEquals(pt(127.005, 37.002), out.get(1));
    }

    /** 정류장이 도로 반대편이라 블록을 돌아 U턴하는 구간 → 직선 */
    @Test
    public void replacesUturnLoopWithStraightSegment() {
        RouteData.Pt a = pt(127.000, 37.000), b = pt(127.003, 37.000);   // 약 0.27km
        List<RouteData.Pt> loop = Arrays.asList(a, pt(127.010, 37.000), pt(127.010, 37.006),
                pt(127.003, 37.006), b);   // 약 2km 돌아감
        List<RouteData.Pt> out = RoadSnap.merge(Arrays.asList(a, b), List.of(loop));
        assertEquals(Arrays.asList(a, b), out);
    }

    @Test
    public void mixedSegmentsAlwaysPassThroughStops() {
        RouteData.Pt a = pt(127.000, 37.000), b = pt(127.010, 37.000), c = pt(127.013, 37.000);
        List<RouteData.Pt> good = Arrays.asList(a, pt(127.005, 37.001), b);
        List<RouteData.Pt> loop = Arrays.asList(b, pt(127.03, 37.0), pt(127.03, 37.01), c);
        List<RouteData.Pt> out = RoadSnap.merge(Arrays.asList(a, b, c), Arrays.asList(good, loop));
        assertEquals(Arrays.asList(a, pt(127.005, 37.001), b, c), out);
    }

    @Test
    public void segmentCountMismatchKeepsStops() {
        List<RouteData.Pt> stops = Arrays.asList(pt(127.0, 37.0), pt(127.01, 37.0), pt(127.02, 37.0));
        assertEquals(stops, RoadSnap.merge(stops, List.of(new ArrayList<>())));
    }

    @Test
    public void requestBodyHasStartViaEnd() throws Exception {
        List<RouteData.Pt> chunk = Arrays.asList(pt(127.1233, 37.385), pt(127.1, 37.4), pt(127.05, 37.45),
                pt(126.9952, 37.611));
        JSONObject b = RoadSnap.body(chunk);
        assertEquals("WGS84GEO", b.getString("reqCoordType"));
        assertEquals("WGS84GEO", b.getString("resCoordType"));
        assertEquals("127.123300", b.getString("startX"));
        assertEquals("37.385000", b.getString("startY"));
        assertEquals("126.995200", b.getString("endX"));
        assertEquals(2, b.getJSONArray("viaPoints").length());
        assertEquals("127.100000", b.getJSONArray("viaPoints").getJSONObject(0).getString("viaX"));
    }

    @Test
    public void cacheKeyDependsOnStops() {
        List<RouteData.Pt> a = Arrays.asList(pt(127.0, 37.0), pt(127.01, 37.0));
        List<RouteData.Pt> b = Arrays.asList(pt(127.0, 37.0), pt(127.02, 37.0));
        assertEquals(RoadSnap.cacheKey(a), RoadSnap.cacheKey(new ArrayList<>(a)));
        assertNotEquals(RoadSnap.cacheKey(a), RoadSnap.cacheKey(b));
    }

    @Test
    public void kmIsAboutRight() {
        // 위도 37° 에서 경도 0.01° ≈ 0.89km
        assertEquals(0.889, RoadSnap.km(pt(127.0, 37.0), pt(127.01, 37.0)), 0.01);
        assertFalse(RoadSnap.plausible(pt(127.0, 37.0), pt(127.0001, 37.0),
                Arrays.asList(pt(127.0, 37.0), pt(127.0001, 37.0))));   // 너무 짧은 구간은 직선
        assertTrue(RoadSnap.plausible(pt(127.0, 37.0), pt(127.01, 37.0),
                Arrays.asList(pt(127.0, 37.0), pt(127.01, 37.0))));
    }
}
