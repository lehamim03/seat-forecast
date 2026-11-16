package kr.ac.kookmin.seatforecast.map;

import android.content.res.AssetManager;
import android.graphics.Color;
import com.kakao.vectormap.LatLng;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MapExample {
    record Stop(String name, LatLng point) {}
    record Line(String name, int color, List<LatLng> points) {}

    LatLng origin;
    final List<Stop> stops = new ArrayList<>();
    final List<Line> walks = new ArrayList<>();
    final Map<String, Line> routes = new LinkedHashMap<>();

    static MapExample load(AssetManager assets) throws IOException, JSONException {
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                assets.open("map.geojson"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        MapExample data = new MapExample();
        JSONArray features = new JSONObject(text.toString()).getJSONArray("features");
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject properties = feature.getJSONObject("properties");
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONArray coords = geometry.getJSONArray("coordinates");
            switch (properties.getString("kind")) {
                case "me" -> data.origin = point(coords);
                case "stop" -> data.stops.add(new Stop(properties.getString("name"), point(coords)));
                case "walk" -> data.walks.add(new Line(properties.getString("to"), Color.rgb(37, 99, 235), path(coords)));
                case "route" -> {
                    String number = properties.getString("route");
                    data.routes.put(number, new Line(number, Color.parseColor(properties.getString("color")), path(coords)));
                }
            }
        }
        return data;
    }

    private static LatLng point(JSONArray pair) throws JSONException {
        return LatLng.from(pair.getDouble(1), pair.getDouble(0));
    }

    private static List<LatLng> path(JSONArray coordinates) throws JSONException {
        List<LatLng> points = new ArrayList<>();
        for (int i = 0; i < coordinates.length(); i++) points.add(point(coordinates.getJSONArray(i)));
        if (points.size() < 2) throw new JSONException("LineString has fewer than two points");
        return points;
    }
}
