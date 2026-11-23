package kr.ac.kookmin.seatforecast.map;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** 팀 예시의 경로·추천 데이터를 읽는다. 하나의 환승 경로는 여러 개의 선으로 나뉠 수 있다. */
final class RouteData {
  static JSONObject asset(Context c, String name) throws Exception {
    try (InputStream in = c.getAssets().open(name);
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      return new JSONObject(out.toString(StandardCharsets.UTF_8.name()));
    }
  }

  record Pt(double lon, double lat) {
    static Pt parse(JSONArray a) throws JSONException {
      double x = a.getDouble(0), y = a.getDouble(1);
      if (!(Math.abs(x) <= 180 && Math.abs(y) <= 90)) throw new JSONException("invalid coordinate");
      return new Pt(x, y);
    }
  }

  record Feature(String kind, JSONObject p, List<Pt> pts) {
    String value(String key) {
      return p.optString(key, "");
    }

  }

  record Choice(String from, String summary, int minutes, int transfers, int walk, String tags) {
    String key() {
      return from + " 하차 → " + summary;
    }
  }

  final List<Feature> features = new ArrayList<>();
  final Map<String, List<Choice>> choices = new LinkedHashMap<>();
  final List<JSONObject> ranking = new ArrayList<>();
  final JSONObject usual, alternative;
  final String time;

  RouteData(Context c) throws Exception {
    this(asset(c, "map.geojson"), asset(c, "route_choices.json"), asset(c, "recommendations.json"));
  }

  RouteData(JSONObject geo, JSONObject routes, JSONObject rec) throws JSONException {
    if (!"FeatureCollection".equals(geo.optString("type")))
      throw new JSONException("expected FeatureCollection");
    JSONArray fs = geo.getJSONArray("features");
    for (int i = 0; i < fs.length(); i++) {
      JSONObject f = fs.getJSONObject(i),
          p = f.getJSONObject("properties"),
          g = f.getJSONObject("geometry");
      String type = g.getString("type");
      JSONArray coords = g.getJSONArray("coordinates");
      List<Pt> points = new ArrayList<>();
      if (type.equals("Point")) points.add(Pt.parse(coords));
      else if (type.equals("LineString") && coords.length() >= 2)
        for (int j = 0; j < coords.length(); j++) points.add(Pt.parse(coords.getJSONArray(j)));
      else continue;
      features.add(new Feature(p.optString("kind"), p, points));
    }
    JSONArray rs = routes.getJSONArray("routes");
    for (int i = 0; i < rs.length(); i++) {
      JSONObject r = rs.getJSONObject(i);
      List<Choice> list = new ArrayList<>();
      JSONArray cs = r.getJSONArray("choices");
      for (int j = 0; j < cs.length(); j++) {
        JSONObject x = cs.getJSONObject(j);
        JSONArray t = x.optJSONArray("tags");
        List<String> labels = new ArrayList<>();
        for (int k = 0; t != null && k < t.length(); k++) labels.add(t.getString(k));
        list.add(
            new Choice(
                x.getString("from"),
                x.getString("transfer"),
                x.getInt("minutes"),
                x.getInt("transfers"),
                x.getInt("walk"),
                String.join(" · ", labels)));
      }
      if (!list.isEmpty()) choices.put(r.getString("route"), list);
    }
    JSONArray rank = rec.getJSONArray("ranking");
    JSONObject u = null, a = null;
    JSONObject suggested = rec.getJSONObject("recommendation");
    for (int i = 0; i < rank.length(); i++) {
      JSONObject x = rank.getJSONObject(i);
      if (x.optBoolean("skipped")) continue;
      ranking.add(x);
      JSONObject stop = x.getJSONObject("stop");
      if (x.optString("route").equals(rec.getString("my_route"))
          && stop.optString("id").equals(rec.getJSONObject("home_stop").optString("id"))) u = x;
      if (x.optString("route").equals(suggested.optString("route"))
          && stop.optString("name").equals(suggested.optString("stop_name"))) a = x;
    }
    usual = u;
    alternative = a;
    String at = rec.optString("generated_at");
    time = at.length() >= 16 ? at.substring(11, 16) : "";
  }

  String eta(JSONObject opt, Choice ch) {
    JSONArray cs = opt == null ? null : opt.optJSONArray("route_choices");
    for (int i = 0; cs != null && i < cs.length(); i++) {
      JSONObject v = cs.optJSONObject(i);
      if (v != null
          && ch.from.equals(v.optString("alight_stop"))
          && ch.summary.equals(v.optString("summary"))
          && !v.isNull("arrive_at")) return v.optString("arrive_at");
    }
    return "";
  }

  JSONObject best(String route) {
    for (JSONObject x : ranking) if (route.equals(x.optString("route"))) return x;
    return null;
  }

  boolean hasShape(String route, Choice ch) {
    for (Feature f : features) if (matches(f, route, ch)) return true;
    return false;
  }

  static boolean matches(Feature f, String route, Choice ch) {
    return ch != null
        && f.kind.equals("transfer")
        && f.value("route").equals(route)
        && f.value("summary").equals(ch.key());
  }
}
