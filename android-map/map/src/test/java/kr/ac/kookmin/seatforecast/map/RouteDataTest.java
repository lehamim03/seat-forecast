package kr.ac.kookmin.seatforecast.map;

import static org.junit.Assert.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.json.*;
import org.junit.Test;

public class RouteDataTest {
    private JSONObject fixture(String file) throws Exception {
        return new JSONObject(new String(Files.readAllBytes(Path.of("../../docs/examples", file)), java.nio.charset.StandardCharsets.UTF_8));
    }

    private RouteData data(JSONObject recommendations) throws Exception {
        return new RouteData(fixture("map.geojson"), fixture("route_choices.json"), recommendations);
    }

    @Test public void mapsEachChoiceToAllItsSeparateGeometrySegments() throws Exception {
        RouteData data = data(fixture("recommendations.json"));
        assertEquals(4, data.choices.size());
        for (var entry : data.choices.entrySet()) assertEquals(4, entry.getValue().size());
        RouteData.Choice fastest = data.choices.get("8800").get(1);
        assertTrue(fastest.tags().contains("최단시간"));
        assertEquals(2, data.features.stream().filter(f -> RouteData.matches(f, "8800", fastest)).count());
        assertFalse(data.hasShape("3007", data.choices.get("3007").get(0)));
        assertFalse(data.hasShape("8800", data.choices.get("3007").get(0)));
    }

    @Test public void arrivalIsMatchedByStopAndTransferSummary() throws Exception {
        RouteData data = data(fixture("recommendations.json"));
        RouteData.Choice choice = data.choices.get("8800").get(1);
        assertEquals("10:27", data.eta(data.usual, choice));
        assertEquals("", data.eta(null, choice));
        assertEquals("", data.eta(data.alternative, choice));
        assertEquals("8800", data.usual.getString("route"));
        assertEquals("3007", data.alternative.getString("route"));
    }

    @Test public void skippedOptionsCannotBecomeTheOriginalOrRecommendedPlan() throws Exception {
        JSONObject recommendations = fixture("recommendations.json");
        JSONArray ranking = recommendations.getJSONArray("ranking");
        for (int i = 0; i < ranking.length(); i++) ranking.getJSONObject(i).put("skipped", true);
        RouteData data = data(recommendations);
        assertNull(data.usual);
        assertNull(data.alternative);
        assertNull(data.best("8800"));
    }

    @Test public void emptyChoicesAndMissingGeometryAreRepresentable() throws Exception {
        RouteData data = new RouteData(new JSONObject("{\"type\":\"FeatureCollection\",\"features\":[]}"),
                new JSONObject("{\"routes\":[]}"), fixture("recommendations.json"));
        assertTrue(data.choices.isEmpty());
        assertTrue(data.features.isEmpty());
    }
}
