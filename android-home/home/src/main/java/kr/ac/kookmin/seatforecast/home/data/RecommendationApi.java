package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.Nullable;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/** docs/api.md ② 홈 */
public interface RecommendationApi {

    /** authorization 이 null 이면 헤더를 보내지 않습니다. */
    @GET("api/v1/recommendations")
    Call<Recommendations> recommendations(@Nullable @Header("Authorization") String authorization,
                                          @Query("home_stop") String homeStop,
                                          @Query("route") String route);
}
