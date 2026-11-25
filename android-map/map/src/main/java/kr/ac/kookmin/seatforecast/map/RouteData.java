package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * 경로·추천 데이터를 읽는다. 하나의 환승 경로는 여러 개의 선으로 나뉠 수 있다.
 * 13주차: {@link MapServer} 주소가 있으면 예측 서버에서, 없으면 assets 의 예시 JSON 에서 읽는다.
 */
final class RouteData {
  interface Result<T> {
    void accept(T value);
  }

  static JSONObject asset(Context c, String name) throws Exception {
    try (InputStream in = c.getAssets().open(name)) {
      return new JSONObject(read(in));
    }
  }

  private static String read(InputStream in) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] buf = new byte[8192];
      int n;
      while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
      return out.toString(StandardCharsets.UTF_8.name());
    }
  }

  /** 서버 GET → JSON (서버가 처음 계산할 때 수십 초 걸릴 수 있어 넉넉히 기다림) */
  static JSONObject http(String url) throws Exception {
    HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
    con.setConnectTimeout(10_000);
    con.setReadTimeout(60_000);
    con.setRequestProperty("Accept", "application/json");
    try {
      int code = con.getResponseCode();
      if (code / 100 != 2) {
        InputStream err = con.getErrorStream();
        throw new IOException("서버 응답 " + code + (err == null ? "" : ": " + read(err)));
      }
      try (InputStream in = con.getInputStream()) {
        return new JSONObject(read(in));
      }
    } finally {
      con.disconnect();
    }
  }

  /**
   * 백그라운드에서 읽고 화면 스레드로 결과를 돌려준다 (화면이 닫혔으면 버림).
   * 서버 주소가 있으면 from_* · to_* 로 서버에서, 서버에 연결하지 못하거나 주소가 없으면 예시 JSON ({@link #fallback}).
   */
  static void load(Activity a, Place from, Place to, Result<RouteData> ok, Result<Exception> fail) {
    Context app = a.getApplicationContext();
    new Thread(() -> {
      RouteData data = null;
      Exception error = null;
      try {
        if (MapServer.isLive()) {
          try {
            data = new RouteData(http(MapServer.url("api/v1/map", from, to)),
                http(MapServer.url("api/v1/route-choices", from, to)).put("live", true),
                http(MapServer.url("api/v1/recommendations", from, to)));
          } catch (Exception serverDown) {
            data = new RouteData(app);   // 서버 꺼짐 · 경로 조회 실패 대비 — 예시 경로
            data.fallback = serverDown.getMessage();
          }
        } else {
          data = new RouteData(app);
        }
      } catch (Exception e) {
        error = e;
      }
      final RouteData d = data;
      final Exception e = error;
      a.runOnUiThread(() -> {
        if (a.isFinishing() || a.isDestroyed()) return;
        if (d != null) ok.accept(d);
        else fail.accept(e);
      });
    }, "route-data").start();
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
  final JSONObject geo;
  final boolean live;
  /** 출발지 · 도착지 모드 응답인지 (my_route 가 null) — 카드 이름 · 경로 이름이 달라짐 */
  final boolean trip;
  /** 서버 대신 예시를 보여 주는 이유 (서버에 연결하지 못함), 아니면 null */
  String fallback;
  final String time;

  RouteData(Context c) throws Exception {
    this(asset(c, "map.geojson"), asset(c, "route_choices.json"), asset(c, "recommendations.json"));
  }

  RouteData(JSONObject geo, JSONObject routes, JSONObject rec) throws JSONException {
    if (!"FeatureCollection".equals(geo.optString("type")))
      throw new JSONException("expected FeatureCollection");
    this.geo = geo;
    this.live = routes.optBoolean("live");
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
    JSONObject suggested = rec.optJSONObject("recommendation");
    if (suggested == null) suggested = new JSONObject();
    trip = rec.isNull("my_route") || rec.optString("my_route").isEmpty();
    JSONObject home = rec.optJSONObject("home_stop");
    for (int i = 0; i < rank.length(); i++) {
      JSONObject x = rank.getJSONObject(i);
      if (x.optBoolean("skipped")) continue;
      ranking.add(x);
      JSONObject stop = x.optJSONObject("stop");
      String stopId = stop == null ? "" : stop.optString("id");
      String stopName = stop == null ? "" : stop.optString("name");
      if (!trip && x.optString("route").equals(rec.optString("my_route"))
          && home != null && stopId.equals(home.optString("id"))) u = x;
      if (a == null && x.optString("route").equals(suggested.optString("route"))
          && stopName.equals(suggested.optString("stop_name"))) a = x;
    }
    if (trip) {
      // 출발지 · 도착지 모드: 평소 경로가 없으므로 추천이 아닌 가장 빠른 후보를 '다음 후보'로 비교
      for (JSONObject x : ranking) {
        if (x != a) {
          u = x;
          break;
        }
      }
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
          && (trip || ch.from.equals(v.optString("alight_stop")))
          && ch.summary.equals(v.optString("summary"))
          && !v.isNull("arrive_at")) return v.optString("arrive_at");
    }
    return "";
  }

  /** 경로 이름 — 출발지 · 도착지 모드는 전체 경로 한 줄, 예전 모드는 "하차 정류장 하차 → 환승" */
  String label(Choice ch) {
    return trip ? ch.summary : ch.key();
  }

  /** 출발지 · 도착지 모드 지도: 상위 경로 선 (main = 1순위) */
  List<Feature> transfers() {
    List<Feature> out = new ArrayList<>();
    for (Feature f : features) if (f.kind.equals("transfer") && f.pts.size() >= 2) out.add(f);
    return out;
  }

  JSONObject best(String route) {
    for (JSONObject x : ranking) if (route.equals(x.optString("route"))) return x;
    return null;
  }

  boolean hasShape(String route, Choice ch) {
    for (Feature f : features) if (matches(f, route, ch)) return true;
    return false;
  }

  /** 환승 선이 그 경로인지 — 예전 모드는 "하차 → 환승", 출발지 · 도착지 모드는 summary 그대로 */
  static boolean matches(Feature f, String route, Choice ch) {
    return ch != null
        && f.kind.equals("transfer")
        && f.value("route").equals(route)
        && (f.value("summary").equals(ch.key()) || f.value("summary").equals(ch.summary));
  }
}
