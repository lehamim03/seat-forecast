package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.model.PlaceResult;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/** docs/api.md ② 홈 · '출발지 · 도착지 모드' */
public interface RecommendationApi {

    /**
     * 출발지 · 도착지 모드 — 좌표 4개 필수, 이름은 화면 표시용.
     * authorization 이 null 이면 헤더를 보내지 않습니다.
     */
    @GET("api/v1/recommendations")
    Call<Recommendations> recommendations(@Nullable @Header("Authorization") String authorization,
                                          @Query("from_lat") double fromLat,
                                          @Query("from_lng") double fromLng,
                                          @Nullable @Query("from_name") String fromName,
                                          @Query("to_lat") double toLat,
                                          @Query("to_lng") double toLng,
                                          @Nullable @Query("to_name") String toName);

    /** 장소 검색 {@code GET /api/v1/places?q=국민대} → {@code {"query", "places": [{name, address, lat, lng}]}} */
    @GET("api/v1/places")
    Call<PlaceResult> places(@Nullable @Header("Authorization") String authorization,
                             @Query("q") String query);
}
