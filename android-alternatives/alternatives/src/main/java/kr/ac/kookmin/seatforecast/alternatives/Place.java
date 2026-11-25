package kr.ac.kookmin.seatforecast.alternatives;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

/**
 * 장소 {name, lat, lng} — 출발지 · 도착지 (A 홈의 home.Place 와 같은 모양, docs/api.md '출발지 · 도착지 모드').
 * D 의 Firestore {@code destination} · {@code last_trip.to} 와 같은 모양입니다.
 */
public class Place {

    /** 출발지를 GPS 로 정했을 때 이름 (서버 기본값과 같음) */
    public static final String CURRENT_LOCATION = "현재 위치";

    @SerializedName("name")
    public String name;

    @Nullable
    @SerializedName("address")
    public String address;

    @SerializedName("lat")
    public double lat;

    @SerializedName("lng")
    public double lng;

    public Place() {
    }

    public Place(String name, double lat, double lng) {
        this.name = name;
        this.lat = lat;
        this.lng = lng;
    }

    public boolean hasCoords() {
        return !Double.isNaN(lat) && !Double.isNaN(lng) && !(lat == 0 && lng == 0);
    }

    /** "현재 위치" 출발지인지 — 자동 갱신 때 GPS 를 다시 읽음 */
    public boolean isCurrentLocation() {
        return CURRENT_LOCATION.equals(name);
    }

    public String nameOr(String fallback) {
        return name == null || name.trim().isEmpty() ? fallback : name;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.US, "%s@%.5f,%.5f", name, lat, lng);
    }
}
