package kr.ac.kookmin.seatforecast.home.model;

import kr.ac.kookmin.seatforecast.home.Place;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * ② 홈 — {@code GET /api/v1/recommendations} 응답. 필드 이름은 docs/api.md · docs/examples/recommendations.json 과 같습니다.
 */
public class Recommendations {

    /** 계산한 시각, 예: "2026-10-07T07:40:00+09:00" */
    @SerializedName("generated_at")
    public String generatedAt;

    /** 요청한 출발지 · 도착지 (출발지 · 도착지 모드, 예전 응답에는 없음) */
    @Nullable
    @SerializedName("origin")
    public Place origin;

    @Nullable
    @SerializedName("destination")
    public Place destination;

    /** 경로를 어디서 받았는지 {@link #ROUTE_SOURCE_ODSAY} · {@link #ROUTE_SOURCE_SAVED_NEARBY} (없으면 null) */
    @Nullable
    @SerializedName("route_source")
    public String routeSource;

    public static final String ROUTE_SOURCE_ODSAY = "odsay";
    /** ODsay 를 못 써서(하루 한도 · IP) 1.5km 안에 저장된 출발지의 경로를 쓰고 도보만 다시 계산 → "근처 저장 경로 기준" */
    public static final String ROUTE_SOURCE_SAVED_NEARBY = "saved_nearby";

    /** 지난 출근길 재생일 때만 (실시간이면 null) */
    @Nullable
    @SerializedName("replay")
    public Replay replay;

    /** "근처 저장 경로 기준" 을 보여 줄지 */
    public boolean isSavedNearbyRoute() {
        return ROUTE_SOURCE_SAVED_NEARBY.equals(routeSource);
    }

    /** 요청한 평소 노선 — 출발지 · 도착지 모드에서는 항상 null */
    @SerializedName("my_route")
    public String myRoute;

    /** 평소 정류장 — 출발지 · 도착지 모드에서는 1순위 선택지의 탈 정류장 ({@code id}, {@code name} 만 옴) */
    @SerializedName("home_stop")
    public Stop homeStop;

    @SerializedName("day")
    public Day day;

    /** 1순위 요약 */
    @Nullable
    @SerializedName("recommendation")
    public Recommendation recommendation;

    /** 도착 예상 시각 순 정류장 × 노선 조합 */
    @SerializedName("ranking")
    public List<Option> ranking = new ArrayList<>();

    /** 서버가 ranking 을 빼고 보내도 null 대신 빈 목록 */
    public List<Option> rankingOrEmpty() {
        return ranking == null ? new ArrayList<>() : ranking;
    }
}
