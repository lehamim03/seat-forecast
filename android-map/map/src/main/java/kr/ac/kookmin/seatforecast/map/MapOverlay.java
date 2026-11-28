package kr.ac.kookmin.seatforecast.map;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.label.Label;
import com.kakao.vectormap.label.LabelOptions;
import com.kakao.vectormap.label.LabelStyle;
import com.kakao.vectormap.route.*;
import java.util.ArrayList;
import java.util.List;

/** 정류장과 도보선은 고정하고, 선택 노선과 기기 위치만 교체한다. */
final class MapOverlay {
    private final KakaoMap map;
    private final float density;
    private RouteLine selected;
    private final List<RouteLine> comparison = new ArrayList<>();
    private Label device;
    private final List<LatLng> nearby = new ArrayList<>();

    MapOverlay(KakaoMap map, float density, MapExample example) {
        this.map = map;
        this.density = density;
        for (MapExample.Stop stop : example.stops) {
            label(stop.point(), stop.name(), Color.rgb(49, 58, 72), false);
            nearby.add(stop.point());
        }
        if (example.origin != null) {
            String name = example.originName == null || example.originName.isEmpty() ? "예시 출발점"
                    : "출발 · " + example.originName;
            label(example.origin, name, Color.rgb(66, 82, 110), true);
            nearby.add(example.origin);
        }
        if (example.destination != null) {
            label(example.destination, "도착 · " + example.destinationName, RouteUi.RED, false);
            nearby.add(example.destination);
        }
        for (MapExample.Line walk : example.walks) {
            Bitmap dot = Bitmap.createBitmap(px(8), px(5), Bitmap.Config.ARGB_8888);
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            paint.setColor(walk.color());
            new Canvas(dot).drawCircle(px(3), px(2), px(2), paint);
            line(walk, RouteLineStyle.from(RouteLinePattern.from(dot, px(4)))).setZOrder(1);
            nearby.addAll(walk.points());
        }
        showStops();
    }

    void showStops() {
        if (!nearby.isEmpty()) map.moveCamera(CameraUpdateFactory.fitMapPoints(nearby.toArray(new LatLng[0]), px(42)));
    }

    void select(MapExample.Line route, boolean fit) {
        clearComparison();
        if (selected != null) selected.remove();
        selected = line(route, RouteLineStyle.from(px(3), route.color()));
        if (fit) map.moveCamera(CameraUpdateFactory.fitMapPoints(route.points().toArray(new LatLng[0]), px(36)));
    }

    private void clearComparison() {
        for (RouteLine line : comparison) line.remove();
        comparison.clear();
    }

    void compare(RouteData data, org.json.JSONObject active, boolean fit) {
        clearComparison();
        if (selected != null) {
            selected.remove();
            selected = null;
        }
        List<LatLng> focus = new ArrayList<>();
        org.json.JSONObject[] options = {data.usual, data.alternative};
        for (org.json.JSONObject option : options) {
            if (option == null) continue;
            String number = option.optString("route");
            List<RouteData.Choice> choices = data.choices.get(number);
            RouteData.Choice transfer = choices == null || choices.isEmpty() ? null : choices.get(0);
            for (RouteData.Feature feature : data.features) {
                boolean bus = feature.kind().equals("route") && number.equals(feature.value("route"));
                if (!bus && !RouteData.matches(feature, number, transfer)) continue;
                List<LatLng> points = new ArrayList<>();
                for (RouteData.Pt point : feature.pts()) points.add(LatLng.from(point.lat(), point.lon()));
                boolean highlighted = option == active;
                int color = highlighted ? RouteUi.RED : 0xff909aa8;
                RouteLine line = line(new MapExample.Line(number, color, points),
                        RouteLineStyle.from(px(highlighted ? 4 : 2), color));
                line.setZOrder(highlighted ? 3 : 0);
                comparison.add(line);
                focus.addAll(points);
            }
        }
        if (fit && !focus.isEmpty()) {
            map.moveCamera(CameraUpdateFactory.fitMapPoints(focus.toArray(new LatLng[0]), px(28)));
        }
    }

    /**
     * 출발지 · 도착지 모드: 상위 경로 선 — 고른 경로(selected, transfers() 순서)는 빨강 굵게, 나머지는 회색.
     * selected 가 -1 이면 1순위(main). 도로 모양으로 바꾼 선이 있으면 그 선 (RoadSnap).
     */
    void showTrip(RouteData data, int selected, boolean fit) {
        clearComparison();
        if (this.selected != null) {
            this.selected.remove();
            this.selected = null;
        }
        List<LatLng> focus = new ArrayList<>(nearby);
        List<RouteData.Feature> lines = data.transfers();
        List<LatLng> chosenPoints = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            RouteData.Feature f = lines.get(i);
            boolean chosen = selected < 0 ? f.p().optBoolean("main") : i == selected;
            List<LatLng> points = new ArrayList<>();
            for (RouteData.Pt point : data.shape(f)) points.add(LatLng.from(point.lat(), point.lon()));
            int color = chosen ? RouteUi.RED : RouteUi.GRAY_LINE;
            RouteLine line = line(new MapExample.Line(f.value("route"), color, points),
                    RouteLineStyle.from(px(chosen ? 5 : 3), color, px(1), Color.WHITE));
            line.setZOrder(chosen ? 3 : 0);
            comparison.add(line);
            focus.addAll(points);
            if (chosen) chosenPoints.addAll(points);
        }
        if (fit) {
            List<LatLng> target = chosenPoints.isEmpty() ? focus : chosenPoints;
            if (!target.isEmpty()) {
                map.moveCamera(CameraUpdateFactory.fitMapPoints(target.toArray(new LatLng[0]), px(40)));
            }
        }
    }

    void locate(LatLng point) {
        clearDevice();
        device = label(point, "내 위치", Color.rgb(29, 78, 216), true);
        map.moveCamera(CameraUpdateFactory.newCenterPosition(point, 16));
    }

    void clearDevice() {
        if (device != null) {
            device.remove();
            device = null;
        }
    }

    private int px(int dp) { return Math.max(1, Math.round(dp * density)); }

    private RouteLine line(MapExample.Line feature, RouteLineStyle style) {
        RouteLineStyles styles = RouteLineStyles.from(style);
        return map.getRouteLineManager().getLayer().addRouteLine(RouteLineOptions
                .from(RouteLineSegment.from(feature.points(), styles))
                .setStylesSet(RouteLineStylesSet.from(styles)));
    }

    private Label label(LatLng point, String title, int color, boolean below) {
        return textLabel(map, density, point, title, color, below);
    }

    static Label textLabel(KakaoMap map, float density, LatLng point, String title, int color, boolean below) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(11 * density);
        float padding = 7 * density;
        int width = (int) Math.ceil(paint.measureText(title) + padding * 2);
        int height = Math.max(1, Math.round(22 * density));
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        paint.setColor(color);
        canvas.drawRoundRect(0, 0, width, height, padding, padding, paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(title, padding, (height - paint.ascent() - paint.descent()) / 2, paint);
        return map.getLabelManager().getLayer().addLabel(LabelOptions.from(point)
                .setRank(title.equals("내 위치") ? 100 : 10)
                .setStyles(LabelStyle.from(bitmap).setAnchorPoint(.5f, below ? 0f : 1f)));
    }
}
