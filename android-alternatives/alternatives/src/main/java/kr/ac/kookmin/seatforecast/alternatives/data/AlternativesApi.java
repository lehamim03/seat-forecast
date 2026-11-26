package kr.ac.kookmin.seatforecast.alternatives.data;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/** docs/api.md ③ 실시간 대안 · '출발지 · 도착지 모드' */
public interface AlternativesApi {

    /** 좌표 4개 필수, 이름은 화면 표시용. authorization 이 null 이면 헤더를 보내지 않습니다. */
    @GET("api/v1/alternatives")
    Call<AlternativesResult> alternatives(@Nullable @Header("Authorization") String authorization,
                                          @Query("from_lat") double fromLat,
                                          @Query("from_lng") double fromLng,
                                          @Nullable @Query("from_name") String fromName,
                                          @Query("to_lat") double toLat,
                                          @Query("to_lng") double toLng,
                                          @Nullable @Query("to_name") String toName);

    /**
     * 지난 출근길 재생 — {@code replay=2026-11-11T08:20} 만 보냄 (from_* · to_* 와 함께 보내면 400).
     * 응답에 최상위 replay {at, date, message, note} 와 선택지마다 actual 이 붙음.
     */
    @GET("api/v1/alternatives")
    Call<AlternativesResult> replay(@Nullable @Header("Authorization") String authorization,
                                    @Query("replay") String replayAt);
}
