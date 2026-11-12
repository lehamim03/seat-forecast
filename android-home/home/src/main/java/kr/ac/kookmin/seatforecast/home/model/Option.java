package kr.ac.kookmin.seatforecast.home.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 선택지(option) — ② 홈 ranking, ③ 실시간 대안, ⑥ 돌발 알림에서 같은 형식으로 씁니다.
 */
public class Option {

    public static final String KIND_WAIT = "기다리기";
    public static final String KIND_WALK = "걸어가기";
    public static final String KIND_OTHER_BUS = "다른 버스";

    /** {@link #KIND_WAIT} · {@link #KIND_WALK} · {@link #KIND_OTHER_BUS} */
    @SerializedName("kind")
    public String kind;

    /** 탈 노선, 예: "3007" */
    @SerializedName("route")
    public String route;

    /** 탈 정류장 */
    @SerializedName("stop")
    public Stop stop;

    /** 평소 정류장에서 걸어가는 시간(분), 0 = 평소 정류장 */
    @SerializedName("walk_min")
    public int walkMin;

    /** 걸어가서 탈 수 있는 첫 차의 탑승 확률 0.0 ~ 1.0 */
    @SerializedName("board_prob")
    public double boardProb;

    /** 국민대 도착 예상 "HH:MM" — {@link #skipped} 면 null */
    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    /** 지금부터 국민대까지 예상(분) */
    @Nullable
    @SerializedName("total_min")
    public Integer totalMin;

    /** 돌발 · 행사로 더한 지연(분) */
    @SerializedName("delay_min")
    public int delayMin;

    /** 우회로 이 정류장을 건너뛰는 중이면 true (탈 수 없음) */
    @SerializedName("skipped")
    public boolean skipped;

    /** 서울에서 내릴 정류장, 예: "양재역.서초문화예술회관(중)" */
    @Nullable
    @SerializedName("alight_stop")
    public String alightStop;

    /** 다가오는 차량 (정보가 없으면 빈 목록) */
    @SerializedName("buses")
    public List<Bus> buses = new ArrayList<>();

    /** 서울에서 국민대까지 경로 여러 개 */
    @SerializedName("route_choices")
    public List<RouteChoice> routeChoices = new ArrayList<>();

    /** 다가오는 첫 차, 없으면 null */
    @Nullable
    public Bus firstBus() {
        return buses == null || buses.isEmpty() ? null : buses.get(0);
    }
}
