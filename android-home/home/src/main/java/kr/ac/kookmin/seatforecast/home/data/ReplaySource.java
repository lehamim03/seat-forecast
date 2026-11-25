package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import java.util.List;

import kr.ac.kookmin.seatforecast.home.model.Recommendations;

/**
 * 지난 출근길 재생 (발표 · 시연용, docs/api.md '지난 출근길 재생') — 수집 기록으로 그날 그 시각의 추천과 실제 결과.
 * 집 → 국민대 경로 기록으로만 되므로 출발지 · 도착지는 보내지 않습니다.
 */
public interface ReplaySource {

    /** @param at "2026-11-12T07:40" (한국 시간) */
    @WorkerThread
    @NonNull
    Recommendations replay(@NonNull String at, @Nullable String idToken) throws SourceException;

    /** 재생할 수 있는 운행일 "2026-10-07" … (오래된 날부터) */
    @WorkerThread
    @NonNull
    List<String> replayDays(@Nullable String idToken) throws SourceException;
}
