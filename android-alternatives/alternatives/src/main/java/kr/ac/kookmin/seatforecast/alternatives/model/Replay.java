package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 지난 출근길 재생 — 응답 최상위 {@code replay} {at, date, message, note} (docs/api.md '지난 출근길 재생') */
public class Replay {

    /** 재생한 시각, 예: "2026-11-12T07:40:00+09:00" */
    @Nullable
    @SerializedName("at")
    public String at;

    /** 운행일, 예: "2026-11-12" */
    @Nullable
    @SerializedName("date")
    public String date;

    /** 화면에 그대로 */
    @Nullable
    @SerializedName("message")
    public String message;

    /** 한계 안내 */
    @Nullable
    @SerializedName("note")
    public String note;
}
