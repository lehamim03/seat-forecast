package kr.ac.kookmin.seatforecast.home.ui;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import androidx.annotation.NonNull;

import kr.ac.kookmin.seatforecast.home.R;

/**
 * "현재 위치" 출발지 — GPS 한 건만 받아 전달하고 바로 수신을 끕니다.
 * android-map 의 DevicePosition 과 같은 방식 (LocationManager, 최근 2분 위치 재사용, {@value #TIMEOUT_MS}ms 시간 초과).
 * 권한 요청은 화면(HomeFragment)이 하고, 여기서는 권한이 있을 때만 위치를 읽습니다.
 */
final class HomeLocation implements LocationListener {

    interface Listener {
        void onLocation(@NonNull Location location);

        /** 위치를 받지 못함 — 화면에 그대로 보여 줄 문장 리소스 */
        void onFailure(int message);
    }

    static final long TIMEOUT_MS = 12_000;
    /** 이보다 최근 위치면 새로 받지 않고 그대로 씀 */
    static final long REUSE_NANOS = 120_000_000_000L;

    private final Context context;
    private final LocationManager manager;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;
    private boolean listening;
    private final Runnable timeout = () -> finishWith(R.string.home_location_timeout);

    HomeLocation(Context context) {
        this.context = context.getApplicationContext();
        this.manager = this.context.getSystemService(LocationManager.class);
    }

    static boolean hasPermission(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** 위치 한 건 요청 — 이전 요청은 취소 */
    void request(Listener listener) {
        stop();
        this.listener = listener;
        boolean precise = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!precise && context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            finishWith(R.string.home_location_denied);
            return;
        }
        if (manager == null) {
            finishWith(R.string.home_location_unavailable);
            return;
        }
        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (!precise && provider.equals(LocationManager.GPS_PROVIDER)) continue;
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                Location last = manager.getLastKnownLocation(provider);
                if (last != null && SystemClock.elapsedRealtimeNanos() - last.getElapsedRealtimeNanos() < REUSE_NANOS) {
                    deliver(last);
                    return;
                }
                manager.requestLocationUpdates(provider, 0, 0, this, Looper.getMainLooper());
                listening = true;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // 다른 공급자가 켜져 있으면 계속 시도
            }
        }
        if (listening) handler.postDelayed(timeout, TIMEOUT_MS);
        else finishWith(R.string.home_location_unavailable);
    }

    @Override
    public void onLocationChanged(@NonNull Location location) {
        if (listening) deliver(location);
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private void deliver(Location location) {
        Listener l = listener;
        stop();
        if (l != null) l.onLocation(location);
    }

    private void finishWith(int message) {
        Listener l = listener;
        stop();
        if (l != null) l.onFailure(message);
    }

    /** 화면을 떠날 때 — 결과를 더 전달하지 않음 */
    void stop() {
        handler.removeCallbacks(timeout);
        listener = null;
        if (listening) {
            listening = false;
            manager.removeUpdates(this);
        }
    }
}
