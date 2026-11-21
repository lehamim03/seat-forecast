package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 지하철로 갈 때 비교 값 (조회에 실패하면 응답에서 null). */
public class Subway {

    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    @Nullable
    @SerializedName("total_min")
    public Integer totalMin;

    @SerializedName("transfers")
    public int transfers;

    @SerializedName("walk_min")
    public int walkMin;

    @Nullable
    @SerializedName("summary")
    public String summary;
}
