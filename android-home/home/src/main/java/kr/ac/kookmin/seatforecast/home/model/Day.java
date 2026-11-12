package kr.ac.kookmin.seatforecast.home.model;

import com.google.gson.annotations.SerializedName;

/** 요일 · 시간대 구분. {@link #label} 은 화면에 그대로 보여 주는 문구 (예: "평일 출퇴근 시간대"). */
public class Day {

    @SerializedName("label")
    public String label;

    @SerializedName("kind")
    public String kind;

    @SerializedName("drop_factor")
    public double dropFactor;

    @SerializedName("headway_factor")
    public double headwayFactor;
}
