package kr.ac.kookmin.seatforecast.map;

import android.content.Intent;
import android.os.Bundle;

/**
 * 화면 사이 약속 (A · B · C · D · E 공통, A home.TripExtras · B alternatives.TripExtras 와 같은 이름) — 출발지 · 도착지 extra 6개.
 * <pre>
 * from_lat · from_lng · to_lat · to_lng : double
 * from_name · to_name                   : String
 * </pre>
 * 출발지가 없거나 from_name 이 "현재 위치"면 지도 화면이 기기 위치(DevicePosition)를 출발지로 씁니다.
 */
public final class TripExtras {

    public static final String FROM_LAT = "from_lat";
    public static final String FROM_LNG = "from_lng";
    public static final String FROM_NAME = "from_name";
    public static final String TO_LAT = "to_lat";
    public static final String TO_LNG = "to_lng";
    public static final String TO_NAME = "to_name";

    private TripExtras() {
    }

    /** @param from null 이면 출발지를 넣지 않음 (= 현재 위치) */
    public static Intent put(Intent intent, Place from, Place to) {
        Bundle b = new Bundle();
        put(b, from, to);
        return intent.putExtras(b);
    }

    public static void put(Bundle b, Place from, Place to) {
        if (from != null) {
            b.putDouble(FROM_LAT, from.lat);
            b.putDouble(FROM_LNG, from.lng);
            b.putString(FROM_NAME, from.name);
        }
        if (to != null) {
            b.putDouble(TO_LAT, to.lat);
            b.putDouble(TO_LNG, to.lng);
            b.putString(TO_NAME, to.name);
        }
    }

    public static Place from(Bundle b) {
        return read(b, FROM_LAT, FROM_LNG, FROM_NAME, Place.CURRENT_LOCATION);
    }

    public static Place to(Bundle b) {
        return read(b, TO_LAT, TO_LNG, TO_NAME, "도착지");
    }

    private static Place read(Bundle b, String lat, String lng, String name, String fallback) {
        if (b == null || !b.containsKey(lat) || !b.containsKey(lng)) return null;
        String n = b.getString(name);
        return new Place(n == null || n.isEmpty() ? fallback : n, b.getDouble(lat, Double.NaN), b.getDouble(lng, Double.NaN));
    }
}
