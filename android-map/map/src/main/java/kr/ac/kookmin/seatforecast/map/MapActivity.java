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
import android.widget.TextView;

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
        startMap();
    }

    private String nativeAppKey() {
        try {
            ApplicationInfo app = getPackageManager().getApplicationInfo(getPackageName(),
                    PackageManager.GET_META_DATA);
            int resource = app.metaData == null ? 0 : app.metaData.getInt(KEY_METADATA);
            return resource == 0 ? "" : getString(resource).trim();
        } catch (PackageManager.NameNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private void startMap() {
        String key = nativeAppKey();
        if (key.isEmpty()) {
            mapMessage.setText(R.string.map_missing_key);
            return;
        }

        try {
            if (!KakaoMapSdk.isInitialized()) {
                KakaoMapSdk.init(getApplicationContext(), key);
            }
            mapView = new MapView(this);
            mapView.setFinishManually(true);
            FrameLayout container = findViewById(R.id.map_container);
            container.addView(mapView, 0);
            mapView.start(new MapLifeCycleCallback() {
                @Override public void onMapDestroy() {}
                @Override public void onMapError(Exception error) {
                    runOnUiThread(() -> showMapFailure());
                }
            }, new KakaoMapReadyCallback() {
                @Override public LatLng getPosition() {
                    // 기획서 예시 지역(수원 매탄동). 기기 위치는 다음 주차에 표시한다.
                    return LatLng.from(37.26602, 127.04102);
                }
                @Override public void onMapReady(KakaoMap map) {
                    runOnUiThread(() -> {
                        if (mapView != null) mapMessage.setVisibility(View.GONE);
                    });
                }
            });
        } catch (RuntimeException | LinkageError error) {
            showMapFailure();
        }
    }

    private void showMapFailure() {
        if (isDestroyed() || isFinishing()) return;
        closeMap();
        mapMessage.setText(R.string.map_failed);
        mapMessage.setVisibility(View.VISIBLE);
    }

    private void closeMap() {
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
        boolean needsSettings = denied && !shouldShowRequestPermissionRationale(COARSE)
                && !shouldShowRequestPermissionRationale(FINE);
        int message = R.string.map_permission_initial;
        if (precise || approximate) {
            message = precise ? R.string.map_permission_precise : R.string.map_permission_approximate;
            getPreferences(MODE_PRIVATE).edit().remove(DENIED).apply();
        } else if (denied) {
            message = needsSettings ? R.string.map_permission_settings : R.string.map_permission_denied;
        }

        ((TextView) findViewById(R.id.map_permission_status)).setText(message);
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
        }
    }

    @Override protected void onResume() {
        super.onResume();
        refreshPermission();
        if (mapView != null) mapView.resume();
    }

    @Override protected void onPause() {
        if (mapView != null) mapView.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        closeMap();
        super.onDestroy();
    }
}
