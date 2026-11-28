package kr.ac.kookmin.seatforecast.alternatives;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 화면 사이 약속 (A · B · C · D · E 공통, A 홈의 home.TripExtras 와 같은 이름) — 출발지 · 도착지 extra 6개.
 * <pre>
 * from_lat · from_lng · to_lat · to_lng : double
 * from_name · to_name                   : String
 * </pre>
 * 출발지가 없거나 from_name 이 "현재 위치"면 대안 화면이 GPS 를 읽습니다 (1분 자동 갱신 때도 다시 읽음).
 */
public final class TripExtras {

    public static final String FROM_LAT = "from_lat";
    public static final String FROM_LNG = "from_lng";
    public static final String FROM_NAME = "from_name";
    public static final String TO_LAT = "to_lat";
    public static final String TO_LNG = "to_lng";
    public static final String TO_NAME = "to_name";
    /** 지난 출근길 재생 시각 (예: "2026-11-11T08:20") — A 홈이 재생 중일 때만. 있으면 출발지 · 도착지 대신 replay 로 요청 */
    public static final String REPLAY = "replay";

    private TripExtras() {
    }

    /** @param from null 이면 출발지를 넣지 않음 (= 현재 위치) */
    public static Intent put(@NonNull Intent intent, @Nullable Place from, @Nullable Place to) {
        Bundle b = new Bundle();
        put(b, from, to);
        return intent.putExtras(b);
    }

    public static void put(@NonNull Bundle b, @Nullable Place from, @Nullable Place to) {
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

    /** 재생 시각 extra, 없거나 비었으면 null */
    @Nullable
    public static String replay(@Nullable Bundle b) {
        String r = b == null ? null : b.getString(REPLAY);
        return r == null || r.trim().isEmpty() ? null : r.trim();
    }

    @Nullable
    public static Place from(@Nullable Bundle b) {
        return read(b, FROM_LAT, FROM_LNG, FROM_NAME, Place.CURRENT_LOCATION);
    }

    @Nullable
    public static Place to(@Nullable Bundle b) {
        return read(b, TO_LAT, TO_LNG, TO_NAME, "도착지");
    }

    @Nullable
    private static Place read(@Nullable Bundle b, String lat, String lng, String name, String fallback) {
        if (b == null || !b.containsKey(lat) || !b.containsKey(lng)) return null;
        String n = b.getString(name);
        return new Place(n == null || n.isEmpty() ? fallback : n, b.getDouble(lat, Double.NaN), b.getDouble(lng, Double.NaN));
    }
}
