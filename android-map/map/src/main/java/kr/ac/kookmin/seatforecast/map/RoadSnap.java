package kr.ac.kookmin.seatforecast.map;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 경로 선을 도로 모양으로 — 서버 지도(GET /api/v1/map)의 transfer 선은 "지나는 정류장 좌표"를 직선으로 이은 것이라
 * 정류장 사이가 멀면 도로를 벗어나 보입니다. 정류장 순서를 TMAP 경유지 길찾기(routeSequential30)에 넣어
 * 정류장 사이를 실제 도로로 잇습니다.
 *
 * <p>정류장 좌표가 도로 반대편에 있으면 길찾기가 블록을 돌아 U턴하거나, 지하철 구간은 도로로 크게 돌아갑니다.
 * 그래서 정류장 사이 구간마다 도로 길이가 직선의 {@value #MAX_RATIO}배(또는 직선 + {@value #MAX_EXTRA_KM}km)를 넘으면
 * 그 구간만 직선으로 둡니다 (서현역 → 국민대 실제 응답으로 확인: 버스 구간 대부분 1.0~1.1배, U턴 구간 2~14배).
 *
 * <p>TMAP 키는 팀 앱 매니페스트 meta-data {@code kr.ac.kookmin.seatforecast.map.TMAP_APP_KEY} (local.properties
 * 의 TMAP_APP_KEY). 키가 없거나 요청이 실패하면 지금처럼 직선으로 그립니다. 같은 정류장 순서는 기기에 저장해 다시 요청하지 않습니다.
 */
final class RoadSnap {
    private static final String TAG = "RoadSnap";
    static final String KEY_METADATA = "kr.ac.kookmin.seatforecast.map.TMAP_APP_KEY";
    static final String URL = "https://apis.openapi.sk.com/tmap/routes/routeSequential30?version=1";
    /** 한 요청의 점 수 (출발 + 경유 29 + 도착) */
    static final int CHUNK = 31;
    static final double MAX_RATIO = 1.5;
    static final double MAX_EXTRA_KM = 0.3;
    /** 이보다 짧은 구간은 직선 그대로 (정류장 두 개가 거의 같은 곳) */
    static final double MIN_SEGMENT_KM = 0.05;

    private static final Map<String, List<RouteData.Pt>> MEMORY = new LinkedHashMap<>(16, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<RouteData.Pt>> eldest) {
            return size() > 32;
        }
    };

    private final String key;
    private final File cacheDir;

    RoadSnap(String key, File cacheDir) {
        this.key = key;
        this.cacheDir = cacheDir;
    }

    /** 키가 없으면 null (직선 그대로) */
    static RoadSnap from(Context context) {
        String key = readKey(context);
        if (key.isEmpty()) return null;
        File dir = new File(context.getCacheDir(), "road_snap");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new RoadSnap(key, dir);
    }

    static String readKey(Context context) {
        try {
            ApplicationInfo app = context.getPackageManager().getApplicationInfo(context.getPackageName(),
                    PackageManager.GET_META_DATA);
            int resource = app.metaData == null ? 0 : app.metaData.getInt(KEY_METADATA);
            return resource == 0 ? "" : context.getString(resource).trim();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            return "";
        }
    }

    interface Done {
        void run(boolean changed);
    }

    /**
     * 출발지 · 도착지 모드의 경로 선을 백그라운드에서 도로 모양으로 바꿉니다 (1순위 먼저).
     * 선 하나가 끝날 때마다 onUpdate, 모두 끝나면 onDone — 둘 다 화면 스레드. 키가 없으면 false (아무것도 안 함).
     */
    static boolean snapTrip(Activity activity, RouteData data, Runnable onUpdate, Done onDone) {
        if (data == null || !data.trip) return false;
        RoadSnap snap = from(activity);
        if (snap == null) return false;
        List<RouteData.Feature> lines = new ArrayList<>(data.topLines());   // 지도에 그리는 순위 선만, 1순위부터
        if (lines.isEmpty()) return false;
        new Thread(() -> {
            boolean changed = false;
            for (RouteData.Feature f : lines) {
                if (data.road.containsKey(f)) continue;
                try {
                    data.road.put(f, snap.snap(f.pts()));
                    changed = true;
                    activity.runOnUiThread(() -> {
                        if (!activity.isFinishing() && !activity.isDestroyed()) onUpdate.run();
                    });
                } catch (Exception e) {
                    Log.w(TAG, "도로 모양으로 바꾸지 못해 직선으로 둠: " + e.getMessage());
                }
            }
            final boolean any = changed;
            activity.runOnUiThread(() -> {
                if (!activity.isFinishing() && !activity.isDestroyed()) onDone.run(any);
            });
        }, "road-snap").start();
        return true;
    }

    /** 정류장 순서 → 도로 모양 선 (저장된 것이 있으면 요청하지 않음) */
    List<RouteData.Pt> snap(List<RouteData.Pt> stops) throws IOException, JSONException {
        if (stops.size() < 2) return stops;
        String id = cacheKey(stops);
        synchronized (MEMORY) {
            List<RouteData.Pt> hit = MEMORY.get(id);
            if (hit != null) return hit;
        }
        File file = new File(cacheDir, id + ".json");
        List<RouteData.Pt> out = null;
        if (file.exists()) {
            try (InputStream in = new FileInputStream(file)) {
                out = parsePoints(new JSONArray(read(in)));
            } catch (IOException | JSONException e) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
            }
        }
        if (out == null) {
            List<List<RouteData.Pt>> segments = new ArrayList<>();
            for (int start = 0; start < stops.size() - 1; ) {
                int end = Math.min(start + CHUNK - 1, stops.size() - 1);
                List<List<RouteData.Pt>> got = segments(post(body(stops.subList(start, end + 1))));
                if (got.size() != end - start) throw new JSONException("구간 수가 다름: " + got.size() + " / " + (end - start));
                segments.addAll(got);
                start = end;
            }
            out = merge(stops, segments);
            try (OutputStream o = new FileOutputStream(file)) {
                o.write(toJson(out).toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // 저장 못 해도 이번에는 그림
            }
        }
        synchronized (MEMORY) {
            MEMORY.put(id, out);
        }
        return out;
    }

    // ── 순수 함수 (단위 테스트) ──

    /** TMAP routeSequential30 요청 본문 — 좌표는 WGS84 (경도 x, 위도 y) */
    static JSONObject body(List<RouteData.Pt> chunk) throws JSONException {
        RouteData.Pt s = chunk.get(0), e = chunk.get(chunk.size() - 1);
        JSONObject b = new JSONObject()
                .put("reqCoordType", "WGS84GEO").put("resCoordType", "WGS84GEO")
                .put("startName", "출발").put("startX", fmt(s.lon())).put("startY", fmt(s.lat()))
                .put("startTime", "202601010800")
                .put("endName", "도착").put("endX", fmt(e.lon())).put("endY", fmt(e.lat()))
                .put("searchOption", "0");
        JSONArray via = new JSONArray();
        for (int i = 1; i < chunk.size() - 1; i++) {
            RouteData.Pt p = chunk.get(i);
            via.put(new JSONObject().put("viaPointId", "v" + i).put("viaPointName", "v" + i)
                    .put("viaX", fmt(p.lon())).put("viaY", fmt(p.lat())));
        }
        return b.put("viaPoints", via);
    }

    /**
     * 응답 features → 구간별 선. 순서는 [Point 출발, Point 경유1, Line(출발→경유1), Point 경유2, Line(경유1→경유2), …]
     * — 점(Point) 다음에 오는 선(LineString)들이 그 점까지의 구간입니다.
     */
    static List<List<RouteData.Pt>> segments(JSONObject response) throws JSONException {
        JSONArray features = response.getJSONArray("features");
        List<List<RouteData.Pt>> out = new ArrayList<>();
        List<RouteData.Pt> cur = null;
        boolean first = true;
        for (int i = 0; i < features.length(); i++) {
            JSONObject geometry = features.getJSONObject(i).getJSONObject("geometry");
            String type = geometry.getString("type");
            if ("Point".equals(type)) {
                if (first) {
                    first = false;   // 출발점 — 아직 구간 없음
                    continue;
                }
                if (cur != null) out.add(cur);
                cur = new ArrayList<>();
            } else if ("LineString".equals(type)) {
                if (cur == null) cur = new ArrayList<>();
                JSONArray coords = geometry.getJSONArray("coordinates");
                for (int j = 0; j < coords.length(); j++) {
                    RouteData.Pt p = RouteData.Pt.parse(coords.getJSONArray(j));
                    if (cur.isEmpty() || !cur.get(cur.size() - 1).equals(p)) cur.add(p);
                }
            }
        }
        if (cur != null) out.add(cur);
        return out;
    }

    /** 구간마다 도로 선이 그럴듯하면 쓰고, 많이 돌아가면(U턴 · 지하철) 직선 — 정류장 점은 항상 지남 */
    static List<RouteData.Pt> merge(List<RouteData.Pt> stops, List<List<RouteData.Pt>> segments) {
        if (segments.size() != stops.size() - 1) return stops;
        List<RouteData.Pt> out = new ArrayList<>();
        out.add(stops.get(0));
        for (int i = 0; i < segments.size(); i++) {
            RouteData.Pt a = stops.get(i), b = stops.get(i + 1);
            List<RouteData.Pt> road = segments.get(i);
            if (plausible(a, b, road)) {
                for (RouteData.Pt p : road) add(out, p);
            }
            add(out, b);
        }
        return out;
    }

    static boolean plausible(RouteData.Pt a, RouteData.Pt b, List<RouteData.Pt> road) {
        double straight = km(a, b);
        if (road == null || road.size() < 2 || straight < MIN_SEGMENT_KM) return false;
        double length = km(a, road.get(0)) + length(road) + km(road.get(road.size() - 1), b);
        return length <= Math.max(straight * MAX_RATIO, straight + MAX_EXTRA_KM);
    }

    static double length(List<RouteData.Pt> line) {
        double sum = 0;
        for (int i = 1; i < line.size(); i++) sum += km(line.get(i - 1), line.get(i));
        return sum;
    }

    /** 두 점 사이 km (짧은 거리라 평면 근사, 위도 37° 기준 경도 1° ≈ 88.8km) */
    static double km(RouteData.Pt a, RouteData.Pt b) {
        double dx = (a.lon() - b.lon()) * 111.32 * Math.cos(Math.toRadians((a.lat() + b.lat()) / 2));
        double dy = (a.lat() - b.lat()) * 110.57;
        return Math.sqrt(dx * dx + dy * dy);
    }

    static String cacheKey(List<RouteData.Pt> stops) {
        StringBuilder sb = new StringBuilder("v1");
        for (RouteData.Pt p : stops) sb.append(String.format(Locale.US, "|%.5f,%.5f", p.lon(), p.lat()));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte x : hash) hex.append(String.format(Locale.US, "%02x", x));
            return hex.toString();
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    // ── 내부 ──

    private static void add(List<RouteData.Pt> out, RouteData.Pt p) {
        if (out.isEmpty() || !out.get(out.size() - 1).equals(p)) out.add(p);
    }

    private static String fmt(double v) {
        return String.format(Locale.US, "%.6f", v);
    }

    private JSONObject post(JSONObject body) throws IOException, JSONException {
        HttpURLConnection con = (HttpURLConnection) new URL(URL).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestMethod("POST");
        con.setDoOutput(true);
        con.setRequestProperty("appKey", key);
        con.setRequestProperty("Content-Type", "application/json");
        con.setRequestProperty("Accept", "application/json");
        try {
            try (OutputStream o = con.getOutputStream()) {
                o.write(body.toString().getBytes(StandardCharsets.UTF_8));
            }
            int code = con.getResponseCode();
            if (code / 100 != 2) throw new IOException("TMAP 응답 " + code);
            try (InputStream in = con.getInputStream()) {
                return new JSONObject(read(in));
            }
        } finally {
            con.disconnect();
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

    static JSONArray toJson(List<RouteData.Pt> points) throws JSONException {
        JSONArray a = new JSONArray();
        for (RouteData.Pt p : points) a.put(new JSONArray().put(p.lon()).put(p.lat()));
        return a;
    }

    static List<RouteData.Pt> parsePoints(JSONArray a) throws JSONException {
        List<RouteData.Pt> out = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) out.add(RouteData.Pt.parse(a.getJSONArray(i)));
        return out;
    }
}
