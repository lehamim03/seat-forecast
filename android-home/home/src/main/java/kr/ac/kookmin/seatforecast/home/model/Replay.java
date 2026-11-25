package kr.ac.kookmin.seatforecast.home.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 지난 출근길 재생 — 응답 최상위 {@code replay} {at, date, message, note} (docs/api.md '지난 출근길 재생') */
public class Replay {

    /** 재생한 시각, 예: "2026-11-12T07:40:00+09:00" */
    @SerializedName("at")
    public String at;

    /** 운행일, 예: "2026-11-12" */
    @SerializedName("date")
    public String date;

    /** 화면에 그대로: "그날 1순위(8800번 · 삼성1차아파트)대로 했다면 07:51에 타서 09:44 국민대 도착 · …" */
    @Nullable
    @SerializedName("message")
    public String message;

    /** 한계 안내, 예: "수집 기록으로 다시 계산 — 도착 예정 분은 정류장 수로 추정, 공식 돌발정보 없음" */
    @Nullable
    @SerializedName("note")
    public String note;
}
