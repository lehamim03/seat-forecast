package kr.ac.kookmin.seatforecast.alternatives.model;

import com.google.gson.annotations.SerializedName;

/** 추천 요약 — {@link #kind} 와 같은 대안 카드에 '추천' 표시, {@link #message} 는 화면에 그대로. */
public class Recommendation {

    /** "기다리기" · "걸어가기" · "다른 버스" */
    @SerializedName("kind")
    public String kind;

    @SerializedName("route")
    public String route;

    @SerializedName("stop_name")
    public String stopName;

    @SerializedName("message")
    public String message;
}
