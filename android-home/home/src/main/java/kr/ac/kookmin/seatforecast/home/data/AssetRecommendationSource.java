package kr.ac.kookmin.seatforecast.home.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.model.PlaceResult;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 앱 assets 에 넣은 예시 응답을 읽습니다 — 테스트 · 서버가 꺼졌을 때 시연용.
 * 기본 파일은 출발지 · 도착지 모드 예시 {@value #DEFAULT_FILE} (docs/examples 복사본)이고,
 * 요청 값(출발지 · 도착지 · 토큰)은 쓰지 않습니다. 장소 검색은 {@value #PLACES_FILE} 에서 이름 · 주소로 찾습니다.
 */
public class AssetRecommendationSource implements RecommendationSource, PlaceSource {

    public static final String DEFAULT_FILE = "trip_recommendations.json";
    public static final String PLACES_FILE = "trip_places.json";

    private final Context context;
    private final String fileName;

    public AssetRecommendationSource(Context context) {
        this(context, DEFAULT_FILE);
    }

    public AssetRecommendationSource(Context context, String fileName) {
        this.context = context.getApplicationContext();
        this.fileName = fileName;
    }

    @NonNull
    @Override
    public Recommendations load(@NonNull Place from, @NonNull Place to, @Nullable String idToken)
            throws SourceException {
        try (InputStream in = context.getAssets().open(fileName)) {
            return RecommendationJson.parse(in);
        } catch (IOException e) {
            throw new SourceException("예시 파일 " + fileName + " 을(를) 찾지 못했습니다.", e);
        }
    }

    @NonNull
    @Override
    public List<Place> search(@NonNull String query, @Nullable String idToken) throws SourceException {
        PlaceResult all;
        try (InputStream in = context.getAssets().open(PLACES_FILE);
             Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            all = RecommendationJson.GSON.fromJson(reader, PlaceResult.class);
        } catch (IOException | RuntimeException e) {
            throw new SourceException("예시 파일 " + PLACES_FILE + " 을(를) 읽지 못했습니다.", e);
        }
        return filter(all == null ? new ArrayList<>() : all.placesOrEmpty(), query);
    }

    /** 이름 · 주소에 검색어가 들어 있는 곳 (공백 무시) */
    static List<Place> filter(List<Place> places, String query) {
        String q = normalize(query);
        List<Place> out = new ArrayList<>();
        for (Place p : places) {
            if (q.isEmpty() || normalize(p.name).contains(q) || normalize(p.address).contains(q)) out.add(p);
        }
        return out;
    }

    private static String normalize(@Nullable String s) {
        return s == null ? "" : s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
