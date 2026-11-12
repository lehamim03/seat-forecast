package kr.ac.kookmin.seatforecast.home.model;

import com.google.gson.annotations.SerializedName;

/** 1순위 요약. {@link #message} 는 화면에 그대로 보여 주는 문장입니다. */
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
