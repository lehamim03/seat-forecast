package kr.ac.kookmin.seatforecast.account;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 장소 {name, lat, lng} — Firestore users/{uid} 의 favorites[] · destination · last_trip.from/to 와
 * GET /api/v1/places 결과 (docs/api.md '출발지 · 도착지 모드'). A home.Place · B · C 의 Place 와 같은 모양입니다.
 */
public class Place {

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

    /** 이름이 있고 좌표가 한국 범위 안인지 (서버 요청 값 범위와 같음: 위도 33~39, 경도 124~132) */
    public boolean isValid() {
        return name != null && !name.trim().isEmpty()
                && lat >= 33 && lat <= 39 && lng >= 124 && lng <= 132;
    }

    /** 같은 곳인지 (이름 + 좌표 소수 5자리 ≈ 1m) */
    public boolean samePlace(@Nullable Place o) {
        return o != null && key().equals(o.key());
    }

    public String key() {
        return String.format(Locale.US, "%s@%.5f,%.5f", name, lat, lng);
    }

    /** Firestore 저장용 {name, lat, lng} */
    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("name", name);
        m.put("lat", lat);
        m.put("lng", lng);
        return m;
    }

    /**
     * Firestore 값 → 장소. {name, lat, lng}, 예전 destination 형식 {name, x(경도), y(위도)} 도 읽습니다.
     * 형식이 다르면 null.
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static Place fromMap(@Nullable Object value) {
        if (!(value instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) value;
        Object name = m.get("name");
        Object lat = m.containsKey("lat") ? m.get("lat") : m.get("y");
        Object lng = m.containsKey("lng") ? m.get("lng") : m.get("x");
        if (!(name instanceof String) || !(lat instanceof Number) || !(lng instanceof Number)) return null;
        return new Place((String) name, ((Number) lat).doubleValue(), ((Number) lng).doubleValue());
    }

    @NonNull
    @Override
    public String toString() {
        return key();
    }
}
