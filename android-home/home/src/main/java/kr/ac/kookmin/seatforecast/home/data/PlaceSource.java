package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

import kr.ac.kookmin.seatforecast.home.Place;

/**
 * 장소 검색 — 출발지 · 도착지 검색 화면이 씁니다.
 * {@link ApiRecommendationSource}({@code GET /api/v1/places}) 또는 {@link AssetRecommendationSource}(trip_places.json).
 */
public interface PlaceSource {

    /** 메인 스레드가 아닌 곳에서 부릅니다. 결과가 없으면 빈 목록. */
    @WorkerThread
    @NonNull
    List<Place> search(@NonNull String query, @Nullable String idToken) throws SourceException;
}
