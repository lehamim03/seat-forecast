package kr.ac.kookmin.seatforecast.alternatives.data;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/** docs/api.md ③ 실시간 대안 — 요청 값은 ② 홈과 같음 */
public interface AlternativesApi {

    /** authorization 이 null 이면 헤더를 보내지 않습니다. */
    @GET("api/v1/alternatives")
    Call<AlternativesResult> alternatives(@Nullable @Header("Authorization") String authorization,
                                          @Query("home_stop") String homeStop,
                                          @Query("route") String route);
}
