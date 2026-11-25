package kr.ac.kookmin.seatforecast.home.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.Place;

/** {@code GET /api/v1/places} 응답 — docs/examples/trip_places.json */
public class PlaceResult {

    @SerializedName("query")
    public String query;

    @SerializedName("places")
    public List<Place> places = new ArrayList<>();

    /** 서버가 places 를 빼고 보내도 null 대신 빈 목록 (좌표가 없는 항목은 뺌) */
    public List<Place> placesOrEmpty() {
        List<Place> out = new ArrayList<>();
        if (places == null) return out;
        for (Place p : places) if (p != null && p.hasCoords()) out.add(p);
        return out;
    }
}
