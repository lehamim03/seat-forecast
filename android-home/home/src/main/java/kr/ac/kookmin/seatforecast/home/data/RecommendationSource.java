package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 홈 화면 추천 데이터를 가져오는 곳.
 * {@link ApiRecommendationSource}(예측 서버) 또는 {@link AssetRecommendationSource}(예시 JSON · 서버 꺼짐 대비).
 */
public interface RecommendationSource {

    /**
     * 출발지 · 도착지 모드 (docs/api.md). 시간이 걸릴 수 있으므로 메인 스레드가 아닌 곳에서 부릅니다
     * (HomeFragment 가 알아서 처리).
     *
     * @param from    출발지 — 현재 GPS("현재 위치") 또는 검색한 곳
     * @param to      도착지
     * @param idToken Firebase ID 토큰 (서버 요청 인증용, 없으면 헤더 생략 · 예시 JSON 에서는 쓰지 않음)
     */
    @WorkerThread
    @NonNull
    Recommendations load(@NonNull Place from, @NonNull Place to, @Nullable String idToken)
            throws SourceException;
}
