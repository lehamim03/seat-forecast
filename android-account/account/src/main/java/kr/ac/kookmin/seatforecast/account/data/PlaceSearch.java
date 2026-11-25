package kr.ac.kookmin.seatforecast.account.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.Place;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

/** 즐겨찾는 곳 추가 화면의 장소 검색 — 예측 서버 {@code GET /api/v1/places?q=} (주소: Account.setServerBaseUrl) */
public class PlaceSearch {

    interface Api {
        @GET("api/v1/places")
        Call<Result> places(@Nullable @Header("Authorization") String auth, @Query("q") String q);
    }

    static class Result {
        @SerializedName("places")
        List<Place> places = new ArrayList<>();
    }

    private final Api api;

    public PlaceSearch() {
        this(Account.getServerBaseUrl());
    }

    public PlaceSearch(String baseUrl) {
        api = new Retrofit.Builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(Api.class);
    }

    /** 로그인 토큰을 받아 검색하고 결과를 메인 스레드로 (좌표가 없는 항목은 뺌) */
    public void search(String query, Callback<List<Place>> callback) {
        Account.getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                run(query, token, callback);
            }

            @Override
            public void onError(String message) {
                run(query, null, callback);
            }
        });
    }

    private void run(String query, @Nullable String token, Callback<List<Place>> callback) {
        Handler main = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            try {
                List<Place> places = searchNow(query, token);
                main.post(() -> callback.onSuccess(places));
            } catch (IOException e) {
                String message = e.getMessage();
                main.post(() -> callback.onError(message));
            }
        }).start();
    }

    /** 메인 스레드가 아닌 곳에서 — 오류면 서버 message 를 담은 IOException */
    @VisibleForTesting
    public List<Place> searchNow(String query, @Nullable String token) throws IOException {
        Response<Result> res;
        try {
            res = api.places(token == null || token.isEmpty() ? null : "Bearer " + token, query.trim()).execute();
        } catch (IOException e) {
            throw new IOException("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", e);
        }
        if (!res.isSuccessful()) throw new IOException(errorMessage(res));
        List<Place> out = new ArrayList<>();
        Result body = res.body();
        if (body != null && body.places != null) {
            for (Place p : body.places) if (p != null && p.isValid()) out.add(p);
        }
        return out;
    }

    private static String errorMessage(Response<?> res) {
        try (ResponseBody body = res.errorBody()) {
            if (body != null) {
                JsonObject error = new com.google.gson.Gson().fromJson(body.charStream(), JsonObject.class)
                        .getAsJsonObject("error");
                if (error != null && error.has("message")) return error.get("message").getAsString();
            }
        } catch (RuntimeException ignored) {
            // 형식이 다른 오류 본문
        }
        return "장소를 검색하지 못했습니다. (" + res.code() + ")";
    }
}
