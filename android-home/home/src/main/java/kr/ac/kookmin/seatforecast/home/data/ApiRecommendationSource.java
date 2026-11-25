package kr.ac.kookmin.seatforecast.home.data;

import retrofit2.Call;

import java.util.List;

import kr.ac.kookmin.seatforecast.home.Place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.stream.MalformedJsonException;

import java.io.EOFException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.TimeUnit;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * 예측 서버 {@code GET /api/v1/recommendations?from_lat&from_lng&from_name&to_lat&to_lng&to_name}
 * (docs/api.md '출발지 · 도착지 모드') · 장소 검색 {@code GET /api/v1/places?q=}.
 * 헤더 {@code Authorization: Bearer <Firebase ID 토큰>} (토큰이 없으면 헤더 생략).
 *
 * <p>오류 응답 {@code {"error": {"code", "message"}}} 의 message 는 그대로 보여 줍니다
 * — 예: {@code 400 bad_request}(좌표 일부만 줌) · {@code 503 route_unavailable}(경로 조회 실패).
 *
 * <p>13주차 서버 연결:
 * <ul>
 *   <li>시간 제한 — 연결 {@value #CONNECT_TIMEOUT_SEC}초, 응답 읽기 {@value #READ_TIMEOUT_SEC}초,
 *       요청 전체 {@value #CALL_TIMEOUT_SEC}초. 서버가 실시간 API 를 모아 계산하므로 읽기는 넉넉히.</li>
 *   <li>실패 이유별 문장 — 시간 초과 · 연결 안 됨 · 형식 오류 · 401 · 5xx ({@link SourceException#getMessage()}).</li>
 *   <li>401 은 {@link SourceException#isUnauthorized()} — 홈 화면이 토큰을 새로 받아 한 번 더 요청합니다.</li>
 * </ul>
 */
public class ApiRecommendationSource implements RecommendationSource, PlaceSource, ReplaySource {

    public static final int CONNECT_TIMEOUT_SEC = 10;
    public static final int READ_TIMEOUT_SEC = 20;
    public static final int CALL_TIMEOUT_SEC = 30;

    static final String MSG_TIMEOUT = "서버 응답이 늦어요. 잠시 후 다시 시도해 주세요.";
    static final String MSG_OFFLINE = "서버에 연결할 수 없습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.";
    static final String MSG_FORMAT = "추천 데이터 형식이 올바르지 않습니다.";
    static final String MSG_EMPTY = "추천 데이터가 비어 있습니다.";
    static final String MSG_UNAUTHORIZED = "로그인이 필요합니다.";

    private final RecommendationApi api;

    /** @param baseUrl 예측 서버 주소, 예: "https://&lt;서버&gt;/" · 에뮬레이터 "http://10.0.2.2:8000/" (끝에 / 가 없으면 붙임) */
    public ApiRecommendationSource(String baseUrl) {
        this(baseUrl, defaultClient());
    }

    /** 시간 제한 등을 바꾼 OkHttpClient 를 쓸 때 (테스트 · 팀 앱 공용 클라이언트) */
    public ApiRecommendationSource(String baseUrl, OkHttpClient client) {
        this(new Retrofit.Builder()
                .baseUrl(normalize(baseUrl))
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(RecommendationJson.GSON))
                .build()
                .create(RecommendationApi.class));
    }

    public ApiRecommendationSource(RecommendationApi api) {
        this.api = api;
    }

    public static OkHttpClient defaultClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .build();
    }

    @VisibleForTesting
    static String normalize(String baseUrl) {
        String url = baseUrl.trim();
        return url.endsWith("/") ? url : url + "/";
    }

    @NonNull
    @Override
    public Recommendations load(@NonNull Place from, @NonNull Place to, @Nullable String idToken)
            throws SourceException {
        return execute(api.recommendations(auth(idToken), from.lat, from.lng, from.name, to.lat, to.lng, to.name));
    }

    @NonNull
    @Override
    public List<Place> search(@NonNull String query, @Nullable String idToken) throws SourceException {
        return execute(api.places(auth(idToken), query.trim())).placesOrEmpty();
    }

    @NonNull
    @Override
    public Recommendations replay(@NonNull String at, @Nullable String idToken) throws SourceException {
        return execute(api.replay(auth(idToken), at));
    }

    @NonNull
    @Override
    public List<String> replayDays(@Nullable String idToken) throws SourceException {
        List<String> days = execute(api.replayDays(auth(idToken))).days;
        return days == null ? new java.util.ArrayList<>() : days;
    }

    @Nullable
    private static String auth(@Nullable String idToken) {
        return idToken == null || idToken.isEmpty() ? null : "Bearer " + idToken;
    }

    private static <T> T execute(Call<T> call) throws SourceException {
        Response<T> res;
        try {
            res = call.execute();
        } catch (JsonParseException e) {
            // JsonIOException(본문 끝에 다른 내용) 도 여기
            throw new SourceException(MSG_FORMAT, e);
        } catch (IOException e) {
            throw ofIo(e);
        }
        if (!res.isSuccessful()) throw errorOf(res);
        T body = res.body();
        if (body == null) throw new SourceException(MSG_EMPTY, null, res.code(), null);
        return body;
    }

    /** 통신 실패 — 형식 오류(Gson 이 IOException 으로 던지는 경우)와 시간 초과 · 연결 안 됨을 구분 */
    static SourceException ofIo(IOException e) {
        if (e instanceof MalformedJsonException || e instanceof EOFException) {
            return new SourceException(MSG_FORMAT, e);
        }
        if (e instanceof InterruptedIOException) {   // SocketTimeoutException · callTimeout
            return new SourceException(MSG_TIMEOUT, SourceException.CODE_TIMEOUT, 0, e);
        }
        // ConnectException · UnknownHostException(인터넷 꺼짐 · 주소 틀림) 등
        return new SourceException(MSG_OFFLINE, SourceException.CODE_OFFLINE, 0, e);
    }

    /** 오류 응답 {@code {"error": {"code": "...", "message": "..."}}} */
    static SourceException errorOf(Response<?> res) {
        String code = null;
        String message = null;
        try (ResponseBody body = res.errorBody()) {
            if (body != null) {
                JsonObject error = RecommendationJson.GSON.fromJson(body.charStream(), JsonObject.class)
                        .getAsJsonObject("error");
                if (error != null) {
                    if (error.has("code") && !error.get("code").isJsonNull()) code = error.get("code").getAsString();
                    if (error.has("message") && !error.get("message").isJsonNull()) {
                        message = error.get("message").getAsString();
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // 형식이 다른 오류 본문(HTML 등) → 기본 문구
        }
        if (message == null || message.trim().isEmpty()) message = defaultMessage(res.code());
        return new SourceException(message, code, res.code(), null);
    }

    static String defaultMessage(int status) {
        if (status == 401) return MSG_UNAUTHORIZED;
        if (status == 403) return "추천을 볼 권한이 없습니다. (403)";
        if (status == 404) return "추천 서버 주소가 올바르지 않습니다. (404)";
        if (status == 408 || status == 504) return MSG_TIMEOUT;
        if (status == 429) return "요청이 많아요. 잠시 후 다시 시도해 주세요. (429)";
        if (status >= 500) return "추천 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요. (" + status + ")";
        return "추천을 불러오지 못했습니다. (" + status + ")";
    }
}
