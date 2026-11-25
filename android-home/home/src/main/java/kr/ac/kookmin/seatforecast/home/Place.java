package kr.ac.kookmin.seatforecast.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.Locale;

/**
 * 장소 {name, lat, lng} — 출발지 · 도착지 · 즐겨찾는 곳 · {@code GET /api/v1/places} 결과 (docs/api.md '출발지 · 도착지 모드').
 * D 의 Firestore {@code favorites[]} · {@code destination} · {@code last_trip.from/to} 와 같은 모양입니다.
 */
public class Place {

    /** 출발지를 GPS 로 정했을 때 이름 (서버 기본값과 같음) */
    public static final String CURRENT_LOCATION = "현재 위치";

    @SerializedName("name")
    public String name;

    /** 검색 결과에만 있음, 예: "서울 성북구 정릉동" */
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
        this(name, null, lat, lng);
    }

    public Place(String name, @Nullable String address, double lat, double lng) {
        this.name = name;
        this.address = address;
        this.lat = lat;
        this.lng = lng;
    }

    /** 좌표가 들어 있는지 (0,0 · NaN 이면 아직 모름) */
    public boolean hasCoords() {
        return !Double.isNaN(lat) && !Double.isNaN(lng) && !(lat == 0 && lng == 0);
    }

    /** 이름이 비었으면 대신 보여 줄 값 */
    public String nameOr(String fallback) {
        return name == null || name.trim().isEmpty() ? fallback : name;
    }

    /** 같은 출발지 · 도착지인지 비교할 때 (좌표는 소수 5자리 ≈ 1m) */
    public String key() {
        return String.format(Locale.US, "%s@%.5f,%.5f", name, lat, lng);
    }

    @NonNull
    @Override
    public String toString() {
        return key();
    }
}
