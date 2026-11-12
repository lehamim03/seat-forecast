package kr.ac.kookmin.seatforecast.home.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

/** 다가오는 차량 한 대. */
public class Bus {

    /** 1 = 첫 번째로 오는 차 */
    @SerializedName("order")
    public int order;

    /** 정류장 도착까지(분) */
    @SerializedName("eta_min")
    public int etaMin;

    /** 정류장 도착 때 예상 좌석 — 모르면 null */
    @Nullable
    @SerializedName("seats_at_stop")
    public Integer seatsAtStop;

    /** 이 차의 탑승 확률 0.0 ~ 1.0 */
    @SerializedName("board_prob")
    public double boardProb;

    /** 걸어가서 탈 수 있는지 (걷는 시간보다 먼저 오면 false) */
    @SerializedName("reachable")
    public boolean reachable;
}
