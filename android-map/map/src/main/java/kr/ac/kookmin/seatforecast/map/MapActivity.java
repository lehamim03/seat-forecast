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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.kakao.vectormap.KakaoMap;
import com.kakao.vectormap.KakaoMapReadyCallback;
import com.kakao.vectormap.KakaoMapSdk;
import com.kakao.vectormap.LatLng;
import com.kakao.vectormap.MapLifeCycleCallback;
import com.kakao.vectormap.MapView;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * C 지도 화면의 진입점. 지도 초기화와 위치 권한 요청은 독립적으로 동작한다.
 * 모양은 팀 앱 A 홈 · B 대안과 같음 (빨강 #C8102E · 흰 카드 · Material 3 버튼 · 칩).
 */
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
    /** 출발지 · 도착지 모드에서 지도에 강조한 경로 (transfers() 순서), -1 = 1순위 */
    private int tripSelected = -1;
    private MapOverlay overlay;
    private DevicePosition position;
    private boolean locateAfterGrant;
    private String selectedNumber;
    private MapView mapView;
    private TextView mapMessage;
    private Button permissionButton;
    private ChipGroup chips;
    private RouteUi ui;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.map_activity);
        ui = new RouteUi(this);
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
        chips = findViewById(R.id.map_routes);
        findViewById(R.id.map_back).setOnClickListener(v -> finish());
        if (state != null) {
            selectedNumber = state.getString("selected_route");
            comparing = state.getBoolean("comparing", true);
            alternativeSelected = state.getBoolean("alternative");
            tripSelected = state.getInt("trip_selected", -1);
        }
        readTrip();
        showTripTitle();
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

    /** 제목 아래 "출발 → 도착" */
    private void showTripTitle() {
        TextView trip = findViewById(R.id.map_trip);
        if (tripTo == null) {
            trip.setVisibility(View.GONE);
            return;
        }
        String from = tripFrom == null ? Place.CURRENT_LOCATION : tripFrom.nameOr(Place.CURRENT_LOCATION);
        trip.setText(from + " → " + tripTo.nameOr("도착지"));
        trip.setVisibility(View.VISIBLE);
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
            panelMessage("위치 권한을 허용하면 현재 위치에서 경로를 계산해요. 아래 '위치 권한 허용'을 눌러 주세요.", false);
            waitingForOrigin = true;
        }
    }

    /** 현재 위치를 못 받음 — 마지막 좌표가 있으면 그것으로 */
    private void originFailed(String message) {
        waitingForOrigin = false;
        if (deviceFix != null) loadRoutes(deviceFix);
        else panelMessage(message, true);
    }

    /** 시트에 안내 문장 (+ 다시 시도 버튼) */
    private void panelMessage(String message, boolean retry) {
        LinearLayout panel = findViewById(R.id.map_comparison);
        panel.removeAllViews();
        TextView text = ui.text(message, 14, RouteUi.BODY, false);
        text.setPadding(0, ui.dp(10), 0, ui.dp(4));
        panel.addView(text);
        if (retry) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = ui.dp(8);
            panel.addView(ui.primaryButton(getString(R.string.map_retry), this::startRoutes), params);
        }
    }

    /** 서버에서 from_* · to_* 로 경로 · 추천을 읽은 뒤 비교 카드와 지도를 그린다. 실패하면 서버 문장 + 다시 시도. */
    private void loadRoutes(Place from) {
        routeFrom = from;
        panelMessage(MapServer.isLive() ? "서버에서 경로 정보를 불러오는 중…" : "경로 정보를 불러오는 중…", false);
        findViewById(R.id.map_source).setVisibility(View.GONE);
        RouteData.load(this, from, tripTo, data -> {
            routeData = data;
            if (overlay != null) {   // 지도가 먼저 그려졌으면 새 경로로 다시
                closeOverlay();
            }
            renderComparison();
            if (readyMap != null && overlay == null) showExample(readyMap);
            startRoadSnap(data);
        }, error -> {
            String message = error == null || error.getMessage() == null
                    ? "경로 비교 정보를 불러오지 못했습니다." : error.getMessage();
            routeData = null;
            panelMessage(message, true);   // 503 route_unavailable 등 서버 문장 그대로
            if (overlay != null) closeOverlay();
            if (readyMap != null) {
                mapMessage.setText(R.string.map_route_error);
                mapMessage.setVisibility(View.VISIBLE);
            }
        });
    }

    /** 경로 선을 도로 모양으로 (TMAP 키가 있을 때) — 끝나는 대로 지도를 다시 그림 */
    private void startRoadSnap(RouteData data) {
        TextView status = findViewById(R.id.map_road_status);
        boolean started = RoadSnap.snapTrip(this, data, () -> {
            if (overlay != null && routeData == data) overlay.showTrip(data, tripSelected, false);
        }, changed -> status.setVisibility(View.GONE));
        status.setVisibility(started ? View.VISIBLE : View.GONE);
    }

    /** 지도 위 표시만 지움 (지도는 그대로) */
    private void closeOverlay() {
        if (readyMap != null) {
            readyMap.getLabelManager().getLayer().removeAll();
            readyMap.getRouteLineManager().getLayer().removeAll();
        }
        overlay = null;
        chips.removeAllViews();
        findViewById(R.id.map_routes_scroll).setVisibility(View.GONE);
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
                    // 출발지를 알면 거기서, 아니면 예시 지역(수원 매탄동)에서 시작한다.
                    Place start = tripFrom != null ? tripFrom : deviceFix;
                    return start != null && start.hasCoords() ? LatLng.from(start.lat, start.lng)
                            : LatLng.from(37.26602, 127.04102);
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
            chips.removeAllViews();
            if (routeData.trip) buildTripChips();
            else buildRouteChips(example);
            findViewById(R.id.map_routes_scroll).setVisibility(chips.getChildCount() > 0 ? View.VISIBLE : View.GONE);
            View nearby = findViewById(R.id.map_nearby);
            nearby.setOnClickListener(v -> {
                if (routeData != null && routeData.trip) overlay.showTrip(routeData, tripSelected, true);
                else overlay.showStops();
            });
            nearby.setVisibility(View.VISIBLE);
            findViewById(R.id.map_legend).setVisibility(View.VISIBLE);
            mapMessage.setVisibility(View.GONE);
            if (routeData.trip) overlay.showTrip(routeData, tripSelected, true);   // 상위 경로 (고른 경로 빨강)
            else if (comparing) showComparison(false);
            refreshPermission();
        } catch (JSONException | IllegalArgumentException e) {
            mapMessage.setText(R.string.map_example_error);
        }
    }

    /** 출발지 · 도착지 모드: "1순위 · 9401번" 칩 — 누르면 그 경로를 빨갛게 */
    private void buildTripChips() {
        List<RouteData.Feature> lines = routeData.transfers();
        if (tripSelected >= lines.size()) tripSelected = -1;
        for (int i = 0; i < lines.size(); i++) {
            final int index = i;
            RouteData.Feature f = lines.get(i);
            Chip chip = ui.choiceChip((i + 1) + "순위 · " + RouteUi.routeName(f.value("route")));
            chip.setId(View.generateViewId());
            chips.addView(chip);
            boolean chosen = tripSelected < 0 ? f.p().optBoolean("main") : i == tripSelected;
            chip.setChecked(chosen);
            chip.setOnClickListener(v -> selectTrip(index));
        }
    }

    private void selectTrip(int index) {
        tripSelected = index;
        for (int i = 0; i < chips.getChildCount(); i++) ((Chip) chips.getChildAt(i)).setChecked(i == index);
        if (overlay != null) overlay.showTrip(routeData, index, true);
        renderComparison();
    }

    /** 예전 모드: 노선 번호 칩 */
    private void buildRouteChips(MapExample example) {
        for (MapExample.Line route : example.routes.values()) {
            Chip chip = ui.choiceChip(RouteUi.routeName(route.name()));
            chip.setId(View.generateViewId());
            chip.setTag(route.name());
            chips.addView(chip);
            if (selectedNumber == null || route.name().equals(selectedNumber)) {
                selectedNumber = route.name();
                chip.setChecked(true);
                overlay.select(route, false);
            }
            chip.setOnClickListener(v -> {
                comparing = false;
                selectedNumber = route.name();
                for (int i = 0; i < chips.getChildCount(); i++) {
                    Chip c = (Chip) chips.getChildAt(i);
                    c.setChecked(route.name().equals(c.getTag()));
                }
                overlay.select(route, true);
                renderComparison();
            });
        }
    }

    private void showComparison(boolean fit) {
        comparing = true;
        JSONObject chosen = alternativeSelected ? routeData.alternative : routeData.usual;
        if (chosen != null) selectedNumber = chosen.optString("route");
        if (overlay != null) overlay.compare(routeData, chosen, fit);
        for (int i = 0; i < chips.getChildCount(); i++) {
            Chip chip = (Chip) chips.getChildAt(i);
            chip.setChecked(String.valueOf(chip.getTag()).equals(selectedNumber));
        }
        renderComparison();
    }

    /** 출발지 · 도착지 모드: 지금 지도에 빨갛게 그린 경로의 노선 */
    private String tripSelectedRoute() {
        List<RouteData.Feature> lines = routeData.transfers();
        for (int i = 0; i < lines.size(); i++) {
            RouteData.Feature f = lines.get(i);
            if (tripSelected < 0 ? f.p().optBoolean("main") : i == tripSelected) return f.value("route");
        }
        return null;
    }

    private int tripIndexOf(String route) {
        List<RouteData.Feature> lines = routeData.transfers();
        for (int i = 0; i < lines.size(); i++) if (lines.get(i).value("route").equals(route)) return i;
        return -1;
    }

    private void renderComparison() {
        LinearLayout panel = findViewById(R.id.map_comparison);
        panel.removeAllViews();
        TextView sourcePill = findViewById(R.id.map_source);
        if (routeData == null) {
            sourcePill.setVisibility(View.GONE);
            panel.addView(ui.text("경로 비교 정보를 불러오지 못했습니다.", 14, RouteUi.MUTED, false));
            return;
        }
        // 오른쪽 위 상태 알약: 근처 저장 경로 기준 (노랑) · 실시간 07:41 (초록) · 예시 경로 (회색)
        if (routeData.savedNearby) ui.stylePill(sourcePill, getString(R.string.map_saved_nearby), RouteUi.Level.MID);
        else if (routeData.live) ui.stylePill(sourcePill, "실시간 " + routeData.time, RouteUi.Level.HIGH);
        else ui.stylePill(sourcePill, "예시 경로", RouteUi.Level.UNKNOWN);

        String legend = routeData.trip ? "빨강 굵은 선은 고른 경로, 회색은 다른 후보 · 칩을 눌러 바꿔 보세요"
                : comparing ? "빨강은 선택 경로, 회색은 비교 경로"
                : "실선은 버스 노선, 파란 점선은 도보";
        if (routeData.savedNearby) legend = getString(R.string.map_saved_nearby) + " · " + legend;
        ((TextView) findViewById(R.id.map_legend)).setText(legend);

        String highlighted = routeData.trip ? tripSelectedRoute() : null;
        JSONObject[] options = {routeData.alternative, routeData.usual};
        for (JSONObject option : options) {
            final boolean alternative = option == routeData.alternative;
            String label = routeData.trip ? (alternative ? "추천" : "다음 후보")
                    : alternative ? "대안 · 추천" : "원래 경로";
            if (option == null) {
                TextView none = ui.text(label + " 정보가 없습니다.", 13, RouteUi.MUTED, false);
                none.setPadding(0, ui.dp(8), 0, 0);
                panel.addView(none);
                continue;
            }
            boolean selected = routeData.trip ? option.optString("route").equals(highlighted)
                    : comparing && alternativeSelected == alternative;
            panel.addView(optionCard(option, label, alternative, selected));
        }
    }

    /** 경로 비교 카드 — B 대안 카드와 같은 모양 (노선 칩 · 추천 태그 · 확률 알약 · 도착) */
    private View optionCard(JSONObject option, String label, boolean alternative, boolean selected) {
        LinearLayout card = ui.column();
        card.setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12));
        ui.card(card, selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = ui.dp(8);
        card.setLayoutParams(params);

        LinearLayout top = ui.row();
        TextView route = ui.routeChip(RouteUi.routeName(option));
        top.addView(route);
        TextView tag = ui.text(label, 13, alternative ? RouteUi.RED : RouteUi.MUTED, true);
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(0, -2, 1);
        tagParams.leftMargin = ui.dp(8);
        top.addView(tag, tagParams);
        top.addView(ui.pill(RouteUi.probability(option), RouteUi.probabilityLevel(option)));
        card.addView(top);

        String arrival = option.isNull("arrive_at") ? "도착 정보 없음" : option.optString("arrive_at") + " 도착 예상";
        TextView arrive = ui.text(arrival, 16, RouteUi.INK, true);
        arrive.setPadding(0, ui.dp(8), 0, ui.dp(2));
        card.addView(arrive);
        JSONObject stop = option.optJSONObject("stop");
        String stopName = stop == null ? "정류장 정보 없음" : stop.optString("name");   // 서버 이름 그대로
        card.addView(ui.text(stopName + " · 도보 " + option.optInt("walk_min") + "분", 13, RouteUi.BODY, false));
        String summary = option.optString("summary", "");
        if (!summary.isEmpty()) {
            TextView line = ui.text(summary, 12, RouteUi.MUTED, false);
            line.setMaxLines(2);
            line.setEllipsize(android.text.TextUtils.TruncateAt.END);
            line.setPadding(0, ui.dp(2), 0, 0);
            card.addView(line);
        }
        java.util.List<RouteData.Choice> transfers = routeData.choices.get(option.optString("route"));
        if (!routeData.trip && transfers != null && !transfers.isEmpty()
                && !routeData.hasShape(option.optString("route"), transfers.get(0))) {
            card.addView(ui.text("서울 환승 구간은 경로 목록에서 확인", 12, RouteUi.MUTED, false));
        }
        card.setSelected(selected);
        card.setFocusable(true);
        card.setClickable(true);
        card.setForeground(getDrawable(android.R.drawable.list_selector_background));
        card.setContentDescription(label + " 지도 보기. " + option.optString("route") + "번. " + arrival);
        card.setOnClickListener(v -> {
            if (routeData.trip) {
                int index = tripIndexOf(option.optString("route"));
                if (index >= 0) selectTrip(index);
            } else {
                alternativeSelected = alternative;
                showComparison(true);
            }
        });
        return card;
    }

    private void locationStatus(int resource) {
        ((TextView) findViewById(R.id.map_permission_status)).setText(resource);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("selected_route", selectedNumber);
        state.putBoolean("comparing", comparing);
        state.putBoolean("alternative", alternativeSelected);
        state.putInt("trip_selected", tripSelected);
    }

    private void showMapFailure() {
        if (isDestroyed() || isFinishing()) return;
        closeMap();
        findViewById(R.id.map_locate).setVisibility(View.GONE);
        findViewById(R.id.map_legend).setVisibility(View.GONE);
        findViewById(R.id.map_nearby).setVisibility(View.GONE);
        findViewById(R.id.map_road_status).setVisibility(View.GONE);
        chips.removeAllViews();
        findViewById(R.id.map_routes_scroll).setVisibility(View.GONE);
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
