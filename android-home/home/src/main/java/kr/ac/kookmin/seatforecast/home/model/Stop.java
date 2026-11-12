package kr.ac.kookmin.seatforecast.home.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 정류장. 이름은 공공데이터 API 이름 그대로 (줄이거나 바꾸지 않음). */
public class Stop {

    /** 경기도 정류소 ID, 예: "203000063" */
    @SerializedName("id")
    public String id;

    /** 예: "매탄위브하늘채.경기아트센터" */
    @SerializedName("name")
    public String name;

    /** 화면에 보이는 5자리 번호, 예: "04007" (home_stop 에는 없음) */
    @Nullable
    @SerializedName("mobile_no")
    public String mobileNo;
}
