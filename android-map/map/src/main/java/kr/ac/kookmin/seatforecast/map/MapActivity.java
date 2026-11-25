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
    private KakaoMap readyMap;
    /** 출발지 — null 이면 현재 위치(기기 위치) */
    private Place tripFrom;
    /** 도착지 — null 이면 안내만 (고정 기본값 없음) */
    private Place tripTo;
    /** 현재 위치로 받은 마지막 좌표 (extra 로 받은 "현재 위치" 좌표로 시작) */
    private Place deviceFix;
    /** 지금 경로를 계산한 출발지 — 경로 목록 화면에 넘김 */
    private Place routeFrom;
    /** 출발지를 정하려고 기기 위치를 기다리는 중 */
    private boolean waitingForOrigin;
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
        readTrip();
        findViewById(R.id.map_choices_action).setOnClickListener(v ->
                startActivity(RouteChoicesActivity.newIntent(this, selectedNumber, routeFrom, tripTo)));
        position = new DevicePosition(this, location -> {
            if (overlay != null && (allowed(COARSE) || allowed(FINE))) {
                overlay.locate(LatLng.from(location.getLatitude(), location.getLongitude()));
                locationStatus(R.string.map_location_found);
            }
            if (waitingForOrigin) {   // "현재 위치" 출발지 — 받은 좌표로 경로 계산
                waitingForOrigin = false;
                deviceFix = new Place(Place.CURRENT_LOCATION, location.getLatitude(), location.getLongitude());
                loadRoutes(deviceFix);
            }
        }, status -> {
            locationStatus(status);
            if (waitingForOrigin && status != R.string.map_locating) originFailed(getString(status));
        });
        startRoutes();
        findViewById(R.id.map_locate).setOnClickListener(v -> {
            if (allowed(COARSE) || allowed(FINE)) position.request();
            else {
                locateAfterGrant = true;
                permissionButton.performClick();
            }
        });
        startMap();
    }

    /** 출발지 · 도착지 — extra 6개(TripExtras), 없으면 팀 앱이 정한 마지막 값(MapServer.setLastTrip) */
    private void readTrip() {
        Bundle extras = getIntent().getExtras();
        Place from = TripExtras.from(extras);
        Place to = TripExtras.to(extras);
        if (to == null) {
            to = MapServer.lastTo();
            if (from == null) from = MapServer.lastFrom();
        }
        tripTo = to != null && to.hasCoords() ? to : null;
        if (from == null || from.isCurrentLocation()) {
            tripFrom = null;
            deviceFix = from != null && from.hasCoords() ? from : null;
        } else {
            tripFrom = from;
        }
    }

    /** 도착지가 없으면 안내, 출발지가 현재 위치면 기기 위치부터 */
    private void startRoutes() {
        if (tripTo == null) {
            panelMessage("도착지가 없어요. 홈에서 출발지 · 도착지를 정해 주세요.", false);
            return;
        }
        if (tripFrom != null) {
            loadRoutes(tripFrom);
            return;
        }
        if (allowed(COARSE) || allowed(FINE)) {
            panelMessage("현재 위치를 확인하는 중…", false);
            waitingForOrigin = true;
            position.request();
        } else if (deviceFix != null) {
            loadRoutes(deviceFix);   // 홈이 준 "현재 위치" 좌표로
        } else {
            panelMessage("위치 권한을 허용하면 현재 위치에서 경로를 계산해요. 아래 '내 위치 권한 설정'을 눌러 주세요.", false);
            waitingForOrigin = true;
        }
    }

    /** 현재 위치를 못 받음 — 마지막 좌표가 있으면 그것으로 */
    private void originFailed(String message) {
        waitingForOrigin = false;
        if (deviceFix != null) loadRoutes(deviceFix);
        else panelMessage(message, true);
    }

    private void panelMessage(String message, boolean retry) {
        LinearLayout panel = findViewById(R.id.map_comparison);
        panel.removeAllViews();
        RouteUi ui = new RouteUi(this);
        panel.addView(ui.text(message, 14, RouteUi.MUTED, false));
        if (retry) panel.addView(ui.action("다시 시도", this::startRoutes));
    }

    /** 서버(실패하면 예시 JSON)에서 from_* · to_* 로 경로 · 추천을 읽은 뒤 비교 카드와 지도를 그린다. */
    private void loadRoutes(Place from) {
        routeFrom = from;
        panelMessage(MapServer.isLive() ? "서버에서 경로 정보를 불러오는 중…" : "경로 정보를 불러오는 중…", false);
        RouteData.load(this, from, tripTo, data -> {
            routeData = data;
            if (overlay != null) {   // 지도가 먼저 그려졌으면 새 경로로 다시
                closeOverlay();
            }
            renderComparison();
            if (readyMap != null && overlay == null) showExample(readyMap);
        }, error -> {
            panelMessage("경로 비교 정보를 불러오지 못했습니다.", true);
            if (readyMap != null && overlay == null) mapMessage.setText(R.string.map_example_error);
        });
    }

    /** 지도 위 표시만 지움 (지도는 그대로) */
    private void closeOverlay() {
        if (readyMap != null) {
            readyMap.getLabelManager().getLayer().removeAll();
            readyMap.getRouteLineManager().getLayer().removeAll();
        }
        overlay = null;
        ((RadioGroup) findViewById(R.id.map_routes)).removeAllViews();
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
                            readyMap = map;
                            if (routeData != null) showExample(map);
                            else if (tripTo == null) mapMessage.setVisibility(View.GONE);
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
            MapExample example = MapExample.load(routeData.geo);
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
            if (routeData != null && routeData.trip) overlay.showTrip(routeData, true);   // 상위 경로 4개 (1순위 굵게)
            else if (comparing && routeData != null) showComparison(false);
            refreshPermission();
        } catch (JSONException | IllegalArgumentException e) {
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
        String source = routeData == null ? "예시 경로"
                : routeData.fallback != null ? "서버에 연결하지 못해 예시 경로"
                : routeData.live ? "실시간 " + routeData.time : "예시 경로";
        ((TextView) findViewById(R.id.map_legend)).setText(routeData != null && routeData.trip
                ? source + " · 빨강 굵은 선은 1순위 경로, 회색은 다른 후보"
                : comparing ? source + " · 빨강은 선택 경로, 회색은 비교 경로"
                : source + " · 실선은 버스 노선, 파란 점선은 도보");
        RouteUi ui = new RouteUi(this);
        if (routeData == null) {
            panel.addView(ui.text("경로 비교 정보를 불러오지 못했습니다.", 14, RouteUi.MUTED, false));
            return;
        }
        JSONObject[] options = {routeData.usual, routeData.alternative};
        for (int i = 0; i < options.length; i++) {
            final boolean alternative = i == 1;
            JSONObject option = options[i];
            String label = routeData.trip ? (alternative ? "추천" : "다음 후보")
                    : alternative ? "대안 · 추천" : "원래 경로";
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
            String arrival = option.isNull("arrive_at") ? "도착 정보 없음" : "도착 " + option.optString("arrive_at");
            card.addView(ui.text(label + " · " + RouteUi.routeName(option) + " · " + arrival,
                    14, RouteUi.INK, true));
            JSONObject stop = option.optJSONObject("stop");
            String stopName = stop == null ? "정류장 정보 없음" : stop.optString("name");
            String probability = RouteUi.probability(option);
            card.addView(ui.text(stopName + " · 도보 " + option.optInt("walk_min") + "분 · " + probability,
                    12, RouteUi.MUTED, false));
            java.util.List<RouteData.Choice> transfers = routeData.choices.get(option.optString("route"));
            if (transfers != null && !transfers.isEmpty()
                    && !routeData.hasShape(option.optString("route"), transfers.get(0))) {
                card.addView(ui.text(routeData.trip ? "경로 선은 경로 목록에서 확인" : "서울 환승 구간은 경로 목록에서 확인",
                        12, RouteUi.MUTED, false));
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
        readyMap = null;
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
            // "현재 위치" 출발지를 기다리던 중이면 이제 기기 위치로 경로 계산
            if (waitingForOrigin && routeData == null) {
                if (allowed(COARSE) || allowed(FINE)) startRoutes();
                else originFailed("위치 권한이 없어 현재 위치를 쓸 수 없어요. 홈에서 출발지를 정해 주세요.");
            }
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
