package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** 서울에서 내린 뒤 국민대까지 경로 하나 (⑤ 형식 + arrive_at). 목록 첫 번째가 항상 '추천'. */
public class RouteChoice {

    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    @SerializedName("transfers")
    public int transfers;

    @SerializedName("walk_min")
    public int walkMin;

    /** "추천" · "최단시간" · "최소환승" */
    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @Nullable
    @SerializedName("alight_stop")
    public String alightStop;

    /** 예: "도보 5분 → 3호선(양재→경복궁) → 도보 3분 → 버스 1711/8111/1020(경복궁역3번출구→국민대앞) → 도보 4분" */
    @Nullable
    @SerializedName("summary")
    public String summary;
}
