package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 홈 화면 추천 데이터를 가져오는 곳.
 * 지금은 {@link AssetRecommendationSource}(예시 JSON), 13주차부터 {@link ApiRecommendationSource}(서버).
 */
public interface RecommendationSource {

    /**
     * 시간이 걸릴 수 있으므로 메인 스레드가 아닌 곳에서 부릅니다 (HomeFragment 가 알아서 처리).
     *
     * @param homeStopId 평소 정류장 ID, 예: "203000063"
     * @param route      평소 노선, 예: "8800"
     * @param idToken    Firebase ID 토큰 (서버 요청 인증용, 예시 JSON 에서는 쓰지 않음)
     */
    @WorkerThread
    @NonNull
    Recommendations load(@NonNull String homeStopId, @NonNull String route, @Nullable String idToken)
            throws SourceException;
}
