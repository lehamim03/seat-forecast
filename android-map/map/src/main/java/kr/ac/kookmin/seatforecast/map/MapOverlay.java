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
            label(example.origin, "예시 출발점", Color.rgb(66, 82, 110), true);
            nearby.add(example.origin);
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
        if (selected != null) selected.remove();
        selected = line(route, RouteLineStyle.from(px(3), route.color()));
        if (fit) map.moveCamera(CameraUpdateFactory.fitMapPoints(route.points().toArray(new LatLng[0]), px(36)));
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
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(px(11));
        int w = (int) Math.ceil(paint.measureText(title)) + px(14);
        int h = px(22);
        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        paint.setColor(color);
        canvas.drawRoundRect(0, 0, w, h, px(7), px(7), paint);
        paint.setColor(Color.WHITE);
        canvas.drawText(title, px(7), (h - paint.ascent() - paint.descent()) / 2, paint);
        return map.getLabelManager().getLayer().addLabel(LabelOptions.from(point)
                .setRank(title.equals("내 위치") ? 100 : 10)
                .setStyles(LabelStyle.from(bitmap).setAnchorPoint(.5f, below ? 0f : 1f)));
    }
}
