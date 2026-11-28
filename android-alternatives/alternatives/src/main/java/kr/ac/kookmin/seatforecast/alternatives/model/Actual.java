package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 지난 출근길 재생 — 선택지마다 {@code actual}: 그날 실제로 어떻게 됐는지 (A 홈의 home.model.Actual 과 같은 모양) */
public class Actual {

    /** 그 정류장에 갔다면 실제로 탄 버스의 출발 시각 "HH:MM" — 모두 만석이면 null */
    @Nullable
    @SerializedName("boarded_at")
    public String boardedAt;

    /** 만석으로 보낸 버스 수 */
    @SerializedName("full_skipped")
    public int fullSkipped;

    /** 실제 기록 기준 도착 "HH:MM" (못 탔으면 null) */
    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    /** 실제 기록 기준 걸린 분 */
    @Nullable
    @SerializedName("total_min")
    public Integer totalMin;
}
