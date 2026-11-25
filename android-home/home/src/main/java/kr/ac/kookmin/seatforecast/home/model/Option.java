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
    /** 출발지 · 도착지 모드: 지하철로 시작하는 경로 */
    public static final String KIND_SUBWAY = "지하철";

    /** {@link #firstMode} 값 */
    public static final String MODE_BUS = "bus";
    public static final String MODE_SUBWAY = "subway";

    /** {@link #KIND_WAIT} · {@link #KIND_WALK} · {@link #KIND_OTHER_BUS} · {@link #KIND_SUBWAY} (모르는 값이 와도 그대로 둠) */
    @SerializedName("kind")
    public String kind;

    /** 탈 노선, 예: "3007" */
    @SerializedName("route")
    public String route;

    /** 탈 정류장 */
    @SerializedName("stop")
    public Stop stop;

    /** 출발지(예전: 평소 정류장)에서 탈 곳까지 걷는 시간(분) */
    @SerializedName("walk_min")
    public int walkMin;

    /** 걸어가서 탈 수 있는 첫 차의 탑승 확률 0.0 ~ 1.0 */
    @SerializedName("board_prob")
    public double boardProb;

    /** 도착지 도착 예상 "HH:MM" — {@link #skipped} 면 null */
    @Nullable
    @SerializedName("arrive_at")
    public String arriveAt;

    /** 지금부터 도착지까지 예상(분) */
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

    /** 경로 (출발지 · 도착지 모드에서는 그 경로 하나) */
    @SerializedName("route_choices")
    public List<RouteChoice> routeChoices = new ArrayList<>();

    /** 처음 타는 것 {@link #MODE_BUS} · {@link #MODE_SUBWAY} (예전 응답 · 모르는 값이면 버스로 봄) */
    @Nullable
    @SerializedName("first_mode")
    public String firstMode;

    /**
     * true = 직행좌석 · 좌석 · 광역급행이라 만석이면 못 탐 → 탑승 확률을 보여 줌.
     * false = 서서 탈 수 있음 · 지하철 → 확률 대신 "입석 가능". 예전 응답처럼 없으면(null) 확률을 보여 줌.
     */
    @Nullable
    @SerializedName("seat_limited")
    public Boolean seatLimited;

    /**
     * {@link #seatLimited} 인데 좌석 수를 알면 true. false 면 (서울 등록 노선 등) 평소 탑승률로 계산한 값이라
     * 확률 대신 "좌석 정보 없음". 예전 응답처럼 없으면(null) 확률을 보여 줌.
     */
    @Nullable
    @SerializedName("seat_known")
    public Boolean seatKnown;

    /** 갈아타는 횟수 (출발지 · 도착지 모드) */
    @Nullable
    @SerializedName("transfers")
    public Integer transfers;

    /** 출발지 → 도착지 전체 경로 한 줄, 예: "도보 3분 → 수인분당선(서현→선릉) → 2호선(선릉→신설동)" */
    @Nullable
    @SerializedName("summary")
    public String summary;

    /** 지난 출근길 재생일 때만 — 그날 실제로 어떻게 됐는지 (실시간이면 null) */
    @Nullable
    @SerializedName("actual")
    public Actual actual;

    /** 지하철로 시작하는지 ({@code first_mode}, 없으면 {@code kind} 로 판단) */
    public boolean isSubway() {
        return MODE_SUBWAY.equals(firstMode) || (firstMode == null && KIND_SUBWAY.equals(kind));
    }

    /** 서서 탈 수 있음 · 지하철 ({@code seat_limited=false}) — 화면엔 "입석 가능" */
    public boolean isStandingOk() {
        return Boolean.FALSE.equals(seatLimited);
    }

    /** 좌석 버스인데 좌석 수를 모름 ({@code seat_known=false}) — 화면엔 "좌석 정보 없음" */
    public boolean isSeatUnknown() {
        return !isStandingOk() && Boolean.FALSE.equals(seatKnown);
    }

    /** 탑승 확률을 보여 줄지 — 입석 가능 · 좌석 정보 없음이면 false */
    public boolean showsProbability() {
        return !isStandingOk() && !isSeatUnknown();
    }

    /** 다가오는 첫 차, 없으면 null */
    @Nullable
    public Bus firstBus() {
        return buses == null || buses.isEmpty() ? null : buses.get(0);
    }
}
