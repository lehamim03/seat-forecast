package kr.ac.kookmin.seatforecast.alternatives.data;

import kr.ac.kookmin.seatforecast.alternatives.Place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;

import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 예측 서버 {@code GET /api/v1/alternatives?from_lat&from_lng&from_name&to_lat&to_lng&to_name}
 * (docs/api.md '출발지 · 도착지 모드'), 헤더 {@code Authorization: Bearer <Firebase ID 토큰>} (없으면 생략).
 * 오류 응답의 message 는 그대로 보여 줍니다 — 예: 503 route_unavailable · 400 bad_request.
 */
public class ApiAlternativesSource implements AlternativesSource {

    private final AlternativesApi api;

    /** @param baseUrl 예측 서버 주소, 예: "https://<서버>/" (끝에 / 가 없으면 붙임) */
    public ApiAlternativesSource(String baseUrl) {
        this(new Retrofit.Builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .addConverterFactory(GsonConverterFactory.create(AlternativesJson.GSON))
                .build()
                .create(AlternativesApi.class));
    }

    public ApiAlternativesSource(AlternativesApi api) {
        this.api = api;
    }

    @NonNull
    @Override
    public AlternativesResult load(@NonNull Place from, @NonNull Place to, @Nullable String idToken)
            throws SourceException {
        String auth = idToken == null || idToken.isEmpty() ? null : "Bearer " + idToken;
        Response<AlternativesResult> res;
        try {
            res = api.alternatives(auth, from.lat, from.lng, from.name, to.lat, to.lng, to.name).execute();
        } catch (JsonParseException e) {
            throw new SourceException("대안 데이터 형식이 올바르지 않습니다.", e);
        } catch (IOException e) {
            throw new SourceException("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", e);
        }
        if (!res.isSuccessful()) throw errorOf(res);
        AlternativesResult body = res.body();
        if (body == null) throw new SourceException("대안 데이터가 비어 있습니다.", null, res.code(), null);
        body.source = res.headers().get("X-Data-Source");   // 서버가 보내면 live / example (팀 server.py 는 보내지 않음 → null)
        return body;
    }

    /** 오류 응답 {@code {"error": {"code": "...", "message": "..."}}} */
    private static SourceException errorOf(Response<?> res) {
        String code = null;
        String message = null;
        try (ResponseBody body = res.errorBody()) {
            if (body != null) {
                JsonObject error = AlternativesJson.GSON.fromJson(body.charStream(), JsonObject.class)
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
            message = res.code() == 401 ? "로그인이 필요합니다." : "실시간 대안을 불러오지 못했습니다. (" + res.code() + ")";
        }
        return new SourceException(message, code, res.code(), null);
    }
}
