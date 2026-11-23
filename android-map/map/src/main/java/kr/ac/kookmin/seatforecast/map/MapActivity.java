package kr.ac.kookmin.seatforecast.map;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import org.json.JSONObject;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import java.io.IOException;
import org.json.JSONException;

import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.KakaoMapSdk;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;

/** C 지도 화면의 진입점. 지도 초기화와 위치 권한 요청은 독립적으로 동작한다. */
public final class MapActivity extends Activity {
    private static final int REQUEST_LOCATION = 42;
    private static final String KEY_METADATA = "kr.ac.kookmin.seatforecast.map.NATIVE_APP_KEY";
    private static final String DENIED = "location_request_denied";
    private static final String COARSE = Manifest.permission.ACCESS_COARSE_LOCATION;
    private static final String FINE = Manifest.permission.ACCESS_FINE_LOCATION;

    private RouteData routeData;
    private boolean comparing = true;
    private boolean alternativeSelected;
    private MapOverlay overlay;
    private DevicePosition position;
    private boolean locateAfterGrant;
    private String selectedNumber;
    private MapView mapView;
    private TextView mapMessage;
    private Button permissionButton;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.map_activity);
        View root = findViewById(R.id.map_root);
        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((view, insets) -> {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
            getWindow().setDecorFitsSystemWindows(false);
        }

        mapMessage = findViewById(R.id.map_message);
        permissionButton = findViewById(R.id.map_permission_action);
        if (state != null) {
            selectedNumber = state.getString("selected_route");
            comparing = state.getBoolean("comparing", true);
            alternativeSelected = state.getBoolean("alternative");
        }
        try {
            routeData = new RouteData(this);
            renderComparison();
        } catch (Exception error) {
            ((LinearLayout) findViewById(R.id.map_comparison)).addView(
                    new RouteUi(this).text("경로 비교 정보를 불러오지 못했습니다.", 14, RouteUi.MUTED, false));
        }
        findViewById(R.id.map_choices_action).setOnClickListener(v ->
                startActivity(RouteChoicesActivity.newIntent(this, selectedNumber)));
        position = new DevicePosition(this, location -> {
            if (overlay != null && (allowed(COARSE) || allowed(FINE))) {
                overlay.locate(LatLng.from(location.getLatitude(), location.getLongitude()));
                locationStatus(R.string.map_location_found);
            }
        }, this::locationStatus);
        findViewById(R.id.map_locate).setOnClickListener(v -> {
            if (allowed(COARSE) || allowed(FINE)) position.request();
            else {
                locateAfterGrant = true;
                permissionButton.performClick();
            }
        });
        startMap();
    }

    static String readNativeAppKey(Activity activity) {
        try {
            ApplicationInfo app = activity.getPackageManager().getApplicationInfo(activity.getPackageName(),
                    PackageManager.GET_META_DATA);
            int resource = app.metaData == null ? 0 : app.metaData.getInt(KEY_METADATA);
            return resource == 0 ? "" : activity.getString(resource).trim();
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private void startMap() {
        String key = readNativeAppKey(this);
        if (key.isEmpty()) {
            mapMessage.setText(R.string.map_missing_key);
            return;
        }

        try {
            if (!KakaoMapSdk.isInitialized()) {
                KakaoMapSdk.init(getApplicationContext(), key);
            }
            mapView = new MapView(this);
            FrameLayout container = findViewById(R.id.map_container);
            container.addView(mapView, 0);
            mapView.start(new MapLifeCycleCallback() {
                @Override public void onMapDestroy() {}
                @Override public void onMapError(Exception error) {
                    runOnUiThread(() -> showMapFailure());
                }
            }, new KakaoMapReadyCallback() {
                @Override public LatLng getPosition() {
                    // 기기 위치를 요청하기 전에는 예시 지역(수원 매탄동)에서 시작한다.
                    return LatLng.from(37.26602, 127.04102);
                }
                @Override public void onMapReady(KakaoMap map) {
                    runOnUiThread(() -> {
                        if (mapView != null) {
                            mapView.setFinishManually(true);
                            showExample(map);
                        }
                    });
                }
            });
        } catch (RuntimeException | LinkageError error) {
            showMapFailure();
        }
    }

    private void showExample(KakaoMap map) {
        try {
            MapExample example = MapExample.load(getAssets());
            overlay = new MapOverlay(map, getResources().getDisplayMetrics().density, example);
            RadioGroup routes = findViewById(R.id.map_routes);
            for (MapExample.Line route : example.routes.values()) {
                RadioButton button = new RadioButton(this);
                button.setId(View.generateViewId());
                button.setText(route.name());
                routes.addView(button);
                if (selectedNumber == null || route.name().equals(selectedNumber)) {
                    selectedNumber = route.name();
                    button.setChecked(true);
                    overlay.select(route, false);
                }
                button.setOnClickListener(v -> {
                    comparing = false;
                    selectedNumber = route.name();
                    overlay.select(route, true);
                    renderComparison();
                });
            }
            View nearby = findViewById(R.id.map_nearby);
            nearby.setOnClickListener(v -> overlay.showStops());
            nearby.setVisibility(View.VISIBLE);
            findViewById(R.id.map_legend).setVisibility(View.VISIBLE);
            mapMessage.setVisibility(View.GONE);
            if (comparing && routeData != null) showComparison(false);
            refreshPermission();
        } catch (IOException | JSONException | IllegalArgumentException e) {
            mapMessage.setText(R.string.map_example_error);
        }
    }

    private void showComparison(boolean fit) {
        comparing = true;
        JSONObject chosen = alternativeSelected ? routeData.alternative : routeData.usual;
        if (chosen != null) selectedNumber = chosen.optString("route");
        if (overlay != null) overlay.compare(routeData, chosen, fit);
        RadioGroup buttons = findViewById(R.id.map_routes);
        for (int i = 0; i < buttons.getChildCount(); i++) {
            RadioButton button = (RadioButton) buttons.getChildAt(i);
            button.setChecked(button.getText().toString().equals(selectedNumber));
        }
        renderComparison();
    }

    private void renderComparison() {
        LinearLayout panel = findViewById(R.id.map_comparison);
        panel.removeAllViews();
        ((TextView) findViewById(R.id.map_legend)).setText(comparing
                ? "예시 경로 · 빨강은 선택 경로, 회색은 비교 경로"
                : "예시 경로 · 실선은 버스 노선, 파란 점선은 도보");
        RouteUi ui = new RouteUi(this);
        if (routeData == null) {
            panel.addView(ui.text("경로 비교 정보를 불러오지 못했습니다.", 14, RouteUi.MUTED, false));
            return;
        }
        JSONObject[] options = {routeData.usual, routeData.alternative};
        for (int i = 0; i < options.length; i++) {
            final boolean alternative = i == 1;
            JSONObject option = options[i];
            String label = alternative ? "대안 · 추천" : "원래 경로";
            if (option == null) {
                panel.addView(ui.text(label + " 정보가 없습니다.", 13, RouteUi.MUTED, false));
                continue;
            }
            boolean selected = comparing && alternativeSelected == alternative;
            LinearLayout card = ui.column();
            card.setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7));
            card.setBackground(ui.background(selected ? 0xfffff2f4 : RouteUi.SURFACE,
                    selected ? RouteUi.RED : RouteUi.BORDER, 10));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = ui.dp(5);
            panel.addView(card, params);
            String arrival = option.isNull("arrive_at") ? "도착 정보 없음" : "국민대 " + option.optString("arrive_at");
            card.addView(ui.text(label + " · " + option.optString("route") + "번 · " + arrival,
                    14, RouteUi.INK, true));
            JSONObject stop = option.optJSONObject("stop");
            String stopName = stop == null ? "정류장 정보 없음" : stop.optString("name");
            double chance = option.optDouble("board_prob", Double.NaN);
            String probability = Double.isNaN(chance) ? "확률 미확인" : "탑승 " + Math.round(chance * 100) + "%";
            card.addView(ui.text(stopName + " · 도보 " + option.optInt("walk_min") + "분 · " + probability,
                    12, RouteUi.MUTED, false));
            java.util.List<RouteData.Choice> transfers = routeData.choices.get(option.optString("route"));
            if (transfers != null && !transfers.isEmpty()
                    && !routeData.hasShape(option.optString("route"), transfers.get(0))) {
                card.addView(ui.text("서울 환승 구간은 경로 목록에서 확인", 12, RouteUi.MUTED, false));
            }
            card.setSelected(selected);
            card.setFocusable(true);
            card.setContentDescription(label + " 지도 보기. " + option.optString("route") + "번. " + arrival);
            card.setOnClickListener(v -> {
                alternativeSelected = alternative;
                showComparison(true);
            });
        }
    }

    private void locationStatus(int resource) {
        ((TextView) findViewById(R.id.map_permission_status)).setText(resource);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("selected_route", selectedNumber);
        state.putBoolean("comparing", comparing);
        state.putBoolean("alternative", alternativeSelected);
    }

    private void showMapFailure() {
        if (isDestroyed() || isFinishing()) return;
        closeMap();
        findViewById(R.id.map_locate).setVisibility(View.GONE);
        findViewById(R.id.map_legend).setVisibility(View.GONE);
        findViewById(R.id.map_nearby).setVisibility(View.GONE);
        ((RadioGroup) findViewById(R.id.map_routes)).removeAllViews();
        mapMessage.setText(R.string.map_failed);
        mapMessage.setVisibility(View.VISIBLE);
    }

    private void closeMap() {
        overlay = null;
        if (mapView != null) {
            MapView old = mapView;
            mapView = null;
            old.finish();
            ((FrameLayout) findViewById(R.id.map_container)).removeView(old);
        }
    }

    private boolean allowed(String permission) {
        return checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
    }

    private void refreshPermission() {
        boolean precise = allowed(FINE);
        boolean approximate = allowed(COARSE);
        boolean denied = getPreferences(MODE_PRIVATE).getBoolean(DENIED, false);
        boolean needsSettings = !precise && !approximate && denied && !shouldShowRequestPermissionRationale(COARSE)
                && !shouldShowRequestPermissionRationale(FINE);
        int message = R.string.map_permission_initial;
        if (precise || approximate) {
            message = precise ? R.string.map_permission_precise : R.string.map_permission_approximate;
            getPreferences(MODE_PRIVATE).edit().remove(DENIED).apply();
        } else if (denied) {
            message = needsSettings ? R.string.map_permission_settings : R.string.map_permission_denied;
        }

        locationStatus(message);
        if (overlay != null && !precise && !approximate) overlay.clearDevice();
        findViewById(R.id.map_locate).setVisibility(overlay != null && !needsSettings ? View.VISIBLE : View.GONE);
        boolean openSettings = precise || approximate || needsSettings;
        permissionButton.setText(openSettings ? R.string.map_settings : R.string.map_location_button);
        permissionButton.setOnClickListener(v -> {
            if (openSettings) {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)));
            } else {
                requestPermissions(new String[]{COARSE, FINE}, REQUEST_LOCATION);
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_LOCATION && results.length > 0) {
            getPreferences(MODE_PRIVATE).edit().putBoolean(DENIED, !allowed(COARSE) && !allowed(FINE)).apply();
            refreshPermission();
            if (locateAfterGrant && (allowed(COARSE) || allowed(FINE))) position.request();
            locateAfterGrant = false;
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPermission();
        if (mapView != null) mapView.resume();
    }

    @Override protected void onPause() {
        position.stop();
        if (mapView != null) mapView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        closeMap();
        super.onDestroy();
    }
}
