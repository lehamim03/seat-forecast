package kr.ac.kookmin.seatforecast.alternatives.model;

import com.google.gson.annotations.SerializedName;

/** 요일 · 시간대 구분, 예: label "평일 출퇴근 시간대", kind "평일 출퇴근". */
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
