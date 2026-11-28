package kr.ac.kookmin.seatforecast.alternatives.data;

import kr.ac.kookmin.seatforecast.alternatives.Place;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;

/**
 * 실시간 대안 데이터를 가져오는 곳.
 * 지금은 {@link AssetAlternativesSource}(예시 JSON), 13주차부터 {@link ApiAlternativesSource}(서버).
 */
public interface AlternativesSource {

    /**
     * 시간이 걸릴 수 있으므로 메인 스레드가 아닌 곳에서 부릅니다 (AlternativesFragment 가 알아서 처리).
     *
     * @param from    출발지 — 현재 GPS("현재 위치") 또는 넘겨받은 곳
     * @param to      도착지
     * @param idToken Firebase ID 토큰 (서버 요청 인증용, 없으면 헤더 생략 · 예시 JSON 에서는 쓰지 않음)
     */
    @WorkerThread
    @NonNull
    AlternativesResult load(@NonNull Place from, @NonNull Place to, @Nullable String idToken)
            throws SourceException;

    /**
     * 지난 출근길 재생 (A 홈이 재생 중일 때 extra "replay") — 그날 기록으로 다시 계산한 대안 + 실제 결과.
     * 기본은 지원하지 않음 (예시 JSON 소스).
     *
     * @param replayAt 예: "2026-11-11T08:20"
     */
    @WorkerThread
    @NonNull
    default AlternativesResult loadReplay(@NonNull String replayAt, @Nullable String idToken) throws SourceException {
        throw new SourceException("이 데이터 소스는 지난 출근길 재생을 지원하지 않습니다.", null);
    }
}
