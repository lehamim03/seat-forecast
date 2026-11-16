package kr.ac.kookmin.seatforecast.map;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import java.util.function.Consumer;

/** 화면에서 요청한 위치 한 건만 전달하고, 화면을 떠나면 수신을 해제한다. */
final class DevicePosition implements LocationListener {
    private final Activity activity;
    private final LocationManager manager;
    private final Consumer<Location> success;
    private final Consumer<Integer> status;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean listening;
    private final Runnable timeout = () -> finishWith(R.string.map_location_timeout);

    DevicePosition(Activity activity, Consumer<Location> success, Consumer<Integer> status) {
        this.activity = activity;
        this.manager = activity.getSystemService(LocationManager.class);
        this.success = success;
        this.status = status;
    }

    void request() {
        stop();
        boolean precise = activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
        if (!precise && activity.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return;

        for (String provider : new String[]{LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER}) {
            if (!precise && provider.equals(LocationManager.GPS_PROVIDER)) continue;
            try {
                if (!manager.isProviderEnabled(provider)) continue;
                Location last = manager.getLastKnownLocation(provider);
                if (last != null && SystemClock.elapsedRealtimeNanos() - last.getElapsedRealtimeNanos()
                        < 120_000_000_000L) {
                    stop();
                    success.accept(last);
                    return;
                }
                manager.requestLocationUpdates(provider, 0, 0, this, Looper.getMainLooper());
                listening = true;
            } catch (IllegalArgumentException | SecurityException ignored) {
                // 다른 공급자가 활성 상태이면 계속 시도한다.
            }
        }
        if (listening) {
            status.accept(R.string.map_locating);
            handler.postDelayed(timeout, 12000);
        } else {
            status.accept(R.string.map_location_unavailable);
        }
    }

    @Override public void onLocationChanged(Location location) {
        if (!listening) return;
        stop();
        success.accept(location);
    }

    @Override public void onProviderEnabled(String provider) {}
    @Override public void onProviderDisabled(String provider) {}
    @Override public void onStatusChanged(String provider, int state, Bundle extras) {}

    private void finishWith(int message) {
        stop();
        status.accept(message);
    }

    void stop() {
        handler.removeCallbacks(timeout);
        if (listening) {
            listening = false;
            manager.removeUpdates(this);
        }
    }
}
