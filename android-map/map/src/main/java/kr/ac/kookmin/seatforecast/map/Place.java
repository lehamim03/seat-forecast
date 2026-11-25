package kr.ac.kookmin.seatforecast.map;

import java.util.Locale;

/**
 * 장소 {name, lat, lng} — 출발지 · 도착지 (A 홈 home.Place · B alternatives.Place 와 같은 모양, docs/api.md '출발지 · 도착지 모드').
 * D 의 Firestore {@code destination} · {@code last_trip.to} 와 같은 모양입니다.
 */
public class Place {

    /** 출발지를 GPS 로 정했을 때 이름 (서버 기본값과 같음) */
    public static final String CURRENT_LOCATION = "현재 위치";

    public String name;

    public String address;

    public double lat;

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

    /** "현재 위치" 출발지인지 — 지도 화면이 기기 위치를 읽음 */
    public boolean isCurrentLocation() {
        return CURRENT_LOCATION.equals(name);
    }

    public String nameOr(String fallback) {
        return name == null || name.trim().isEmpty() ? fallback : name;
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s@%.5f,%.5f", name, lat, lng);
    }
}
