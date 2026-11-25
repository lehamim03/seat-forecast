package kr.ac.kookmin.seatforecast.map;

import android.graphics.Color;
import com.kakao.vectormap.LatLng;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MapExample {
    record Stop(String name, LatLng point) {}
    record Line(String name, int color, List<LatLng> points) {}

    LatLng origin;
    /** 출발지 이름 (서버: "현재 위치" 또는 검색한 곳) */
    String originName;
    /** 출발지 · 도착지 모드의 도착지 */
    LatLng destination;
    String destinationName;
    final List<Stop> stops = new ArrayList<>();
    final List<Line> walks = new ArrayList<>();
    final Map<String, Line> routes = new LinkedHashMap<>();

    /** map.geojson 형식 (예시 파일 또는 서버 GET /api/v1/map) */
    static MapExample load(JSONObject geo) throws JSONException {
        MapExample data = new MapExample();
        JSONArray features = geo.getJSONArray("features");
        for (int i = 0; i < features.length(); i++) {
            JSONObject feature = features.getJSONObject(i);
            JSONObject properties = feature.getJSONObject("properties");
            JSONObject geometry = feature.getJSONObject("geometry");
            JSONArray coords = geometry.getJSONArray("coordinates");
            switch (properties.getString("kind")) {
                case "me" -> {
                    data.origin = point(coords);
                    data.originName = properties.optString("name", "");
                }
                case "destination" -> {
                    data.destination = point(coords);
                    data.destinationName = properties.optString("name", "도착지");
                }
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
