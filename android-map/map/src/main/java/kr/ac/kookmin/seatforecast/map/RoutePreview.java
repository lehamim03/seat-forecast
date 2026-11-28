package kr.ac.kookmin.seatforecast.map;

import android.content.Context;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import com.kakao.vectormap.*;
import com.kakao.vectormap.camera.CameraUpdateFactory;
import com.kakao.vectormap.route.*;
import com.kakao.vectormap.label.Label;
import java.util.ArrayList;
import java.util.List;

/** 환승 구간마다 분리된 GeoJSON 선을 유지하며 선택 경로를 강조한다. */
final class RoutePreview extends FrameLayout {
    private final RouteData data;
    private final TextView message;
    private final List<RouteLine> lines = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();
    private MapView mapView;
    private KakaoMap map;
    private String route;
    private RouteData.Choice choice;
    private boolean closed;

    RoutePreview(Context context, RouteData data, String key) {
        super(context);
        this.data = data;
        setBackgroundColor(RouteUi.BACKGROUND);
        message = new TextView(context);
        message.setText("환승 지도를 불러오는 중입니다.");
        message.setGravity(Gravity.CENTER);
        message.setPadding(16, 16, 16, 16);
        addView(message, new LayoutParams(-1, -1));
        if (key.isEmpty()) {
            message.setText(R.string.map_missing_key);
            return;
        }
        try {
            if (!KakaoMapSdk.isInitialized()) KakaoMapSdk.init(context.getApplicationContext(), key);
            mapView = new MapView(context);
            addView(mapView, 0, new LayoutParams(-1, -1));
            mapView.start(new MapLifeCycleCallback() {
                @Override public void onMapDestroy() {}
                @Override public void onMapError(Exception error) {
                    post(() -> {
                        if (!closed) {
                            map = null;
                            message.setText(R.string.map_failed);
                            message.setVisibility(View.VISIBLE);
                        }
                    });
                }
            }, new KakaoMapReadyCallback() {
                @Override public LatLng getPosition() { return LatLng.from(37.575, 126.99); }
                @Override public void onMapReady(KakaoMap ready) {
                    post(() -> {
                        if (closed) return;
                        map = ready;
                        mapView.setFinishManually(true);
                        draw();
                    });
                }
            });
        } catch (RuntimeException | LinkageError error) {
            message.setText(R.string.map_failed);
        }
    }

    void select(String number, RouteData.Choice selected) {
        route = number;
        choice = selected;
        if (map != null) draw();
    }

    private void draw() {
        for (RouteLine line : lines) line.remove();
        lines.clear();
        for (Label label : labels) label.remove();
        labels.clear();
        List<LatLng> focused = new ArrayList<>();
        List<RouteData.Choice> choices = data.choices.get(route);
        if (choices == null || choice == null) {
            message.setText("표시할 환승 경로가 없습니다.");
            message.setVisibility(View.VISIBLE);
            return;
        }
        for (RouteData.Feature feature : data.features) {
            if (!feature.kind().equals("transfer") || !feature.value("route").equals(route)) continue;
            boolean selected = RouteData.matches(feature, route, choice);
            List<LatLng> points = new ArrayList<>();
            for (RouteData.Pt point : data.shape(feature)) points.add(LatLng.from(point.lat(), point.lon()));
            int color = selected ? RouteUi.COLORS[choices.indexOf(choice) % RouteUi.COLORS.length] : 0xffb3b8c2;
            int width = Math.max(1, Math.round((selected ? 4 : 2) * getResources().getDisplayMetrics().density));
            RouteLineStyles styles = RouteLineStyles.from(RouteLineStyle.from(width, color));
            RouteLine line = map.getRouteLineManager().getLayer().addRouteLine(
                    RouteLineOptions.from(RouteLineSegment.from(points, styles))
                            .setStylesSet(RouteLineStylesSet.from(styles)));
            line.setZOrder(selected ? 3 : 1);
            lines.add(line);
            if (selected) focused.addAll(points);
        }
        if (focused.isEmpty()) {
            message.setText("이 경로의 지도 좌표가 없습니다. 아래 이동 순서를 확인해 주세요.");
            message.setBackgroundColor(Color.WHITE);
            message.setVisibility(View.VISIBLE);
        } else {
            message.setVisibility(View.GONE);
            float density = getResources().getDisplayMetrics().density;
            labels.add(MapOverlay.textLabel(map, density, focused.get(0), "환승 시작", RouteUi.INK, true));
            labels.add(MapOverlay.textLabel(map, density, focused.get(focused.size() - 1),
                    "마지막 하차", RouteUi.INK, false));
            map.moveCamera(CameraUpdateFactory.fitMapPoints(focused.toArray(new LatLng[0]),
                    Math.round(24 * getResources().getDisplayMetrics().density)));
        }
    }

    void resume() { if (mapView != null) mapView.resume(); }
    void pause() { if (mapView != null) mapView.pause(); }
    void close() {
        closed = true;
        if (mapView != null) mapView.finish();
        map = null;
    }
}
