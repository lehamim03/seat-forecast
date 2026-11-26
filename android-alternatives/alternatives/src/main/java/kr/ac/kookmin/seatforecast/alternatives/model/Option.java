package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * 선택지(option) — ② 홈 ranking, ③ 실시간 대안, ⑥ 돌발 알림에서 같은 형식 (docs/api.md).
 * A 홈 모듈의 Option 과 필드가 같습니다.
 */
public class Option {

    public static final String KIND_WAIT = "기다리기";
    public static final String KIND_WALK = "걸어가기";
    public static final String KIND_OTHER_BUS = "다른 버스";
    /** 출발지 · 도착지 모드: 지하철로 시작 (recommendation.kind 로 옴 — 지하철 비교 칸에 '추천') */
    public static final String KIND_SUBWAY = "지하철";
    public static final String MODE_SUBWAY = "subway";

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

    /** 서울에서 국민대까지 경로 여러 개 — 첫 번째가 '추천' */
    @SerializedName("route_choices")
    public List<RouteChoice> routeChoices = new ArrayList<>();

    /** 처음 타는 것 bus · subway (예전 응답 · 모르는 값이면 버스로 봄) */
    @Nullable
    @SerializedName("first_mode")
    public String firstMode;

    /** true = 좌석 버스(만석이면 못 탐) · false = 서서 탈 수 있음 · 지하철 → "입석 가능". 없으면 확률 표시 */
    @Nullable
    @SerializedName("seat_limited")
    public Boolean seatLimited;

    /** false = 좌석 버스인데 좌석 수를 모름 (서울 등록 노선 등) → "좌석 정보 없음". 없으면 확률 표시 */
    @Nullable
    @SerializedName("seat_known")
    public Boolean seatKnown;

    @Nullable
    @SerializedName("transfers")
    public Integer transfers;

    /** 출발지 → 도착지 전체 경로 한 줄 */
    @Nullable
    @SerializedName("summary")
    public String summary;

    /** 지난 출근길 재생일 때만 — 그날 실제로 어떻게 됐는지 (아니면 null) */
    @Nullable
    @SerializedName("actual")
    public Actual actual;

    public boolean isStandingOk() {
        return Boolean.FALSE.equals(seatLimited);
    }

    public boolean isSeatUnknown() {
        return !isStandingOk() && Boolean.FALSE.equals(seatKnown);
    }

    /** 탑승 확률을 보여 줄지 — 입석 가능 · 좌석 정보 없음이면 false */
    public boolean showsProbability() {
        return !isStandingOk() && !isSeatUnknown();
    }

    public boolean isSubway() {
        return MODE_SUBWAY.equals(firstMode) || (firstMode == null && KIND_SUBWAY.equals(kind));
    }

    /** 추천 경로(route_choices 첫 번째), 없으면 null */
    @Nullable
    public RouteChoice mainChoice() {
        return routeChoices == null || routeChoices.isEmpty() ? null : routeChoices.get(0);
    }
}
