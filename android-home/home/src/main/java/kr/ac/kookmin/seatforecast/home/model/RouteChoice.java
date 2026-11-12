package kr.ac.kookmin.seatforecast.home.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** 서울에서 내린 뒤 국민대까지 경로 하나 (⑤ 경로 여러 개 형식 + arrive_at). */
public class RouteChoice {

    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    /** 광역버스에서 내린 뒤 갈아타는 수단 수 */
    @SerializedName("transfers")
    public int transfers;

    @SerializedName("walk_min")
    public int walkMin;

    /** "추천" · "최단시간" · "최소환승" (여러 개 또는 없음) */
    @SerializedName("tags")
    public List<String> tags = new ArrayList<>();

    @SerializedName("alight_stop")
    public String alightStop;

    @SerializedName("summary")
    public String summary;
}
