package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 예측 서버 {@code GET /api/v1/recommendations?home_stop=...&route=...}
 * 헤더 {@code Authorization: Bearer <Firebase ID 토큰>}. 실제 서버 연결은 13주차.
 */
public class ApiRecommendationSource implements RecommendationSource {

    private final RecommendationApi api;

    /** @param baseUrl 예측 서버 주소, 예: "https://<서버>/" (끝에 / 가 없으면 붙임) */
    public ApiRecommendationSource(String baseUrl) {
        this(new Retrofit.Builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .addConverterFactory(GsonConverterFactory.create(RecommendationJson.GSON))
                .build()
                .create(RecommendationApi.class));
    }

    public ApiRecommendationSource(RecommendationApi api) {
        this.api = api;
    }

    @NonNull
    @Override
    public Recommendations load(@NonNull String homeStopId, @NonNull String route, @Nullable String idToken)
            throws SourceException {
        String auth = idToken == null || idToken.isEmpty() ? null : "Bearer " + idToken;
        Response<Recommendations> res;
        try {
            res = api.recommendations(auth, homeStopId, route).execute();
        } catch (JsonParseException e) {
            throw new SourceException("추천 데이터 형식이 올바르지 않습니다.", e);
        } catch (IOException e) {
            throw new SourceException("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", e);
        }
        if (!res.isSuccessful()) throw errorOf(res);
        Recommendations body = res.body();
        if (body == null) throw new SourceException("추천 데이터가 비어 있습니다.", null, res.code(), null);
        return body;
    }

    /** 오류 응답 {@code {"error": {"code": "...", "message": "..."}}} */
    private static SourceException errorOf(Response<?> res) {
        String code = null;
        String message = null;
        try (ResponseBody body = res.errorBody()) {
            if (body != null) {
                JsonObject error = RecommendationJson.GSON.fromJson(body.charStream(), JsonObject.class)
                        .getAsJsonObject("error");
                if (error != null) {
                    if (error.has("code")) code = error.get("code").getAsString();
                    if (error.has("message")) message = error.get("message").getAsString();
                }
            }
        } catch (RuntimeException ignored) {
            // 형식이 다른 오류 본문 → 기본 문구
        }
        if (message == null) {
            message = res.code() == 401 ? "로그인이 필요합니다." : "추천을 불러오지 못했습니다. (" + res.code() + ")";
        }
        return new SourceException(message, code, res.code(), null);
    }
}
