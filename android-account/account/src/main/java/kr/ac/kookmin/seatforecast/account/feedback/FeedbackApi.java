package kr.ac.kookmin.seatforecast.account.feedback;

import retrofit2.Call;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

/** 예측 서버 탑승 피드백 API (docs/api.md ⑦) */
public interface FeedbackApi {

    @POST("api/v1/feedback")
    Call<FeedbackResponse> send(@Header("Authorization") String bearerToken, @Body FeedbackRequest body);

    static FeedbackApi create(String baseUrl) {
        return new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(FeedbackApi.class);
    }
}
