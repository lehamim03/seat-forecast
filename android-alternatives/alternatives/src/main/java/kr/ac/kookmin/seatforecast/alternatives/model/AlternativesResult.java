package kr.ac.kookmin.seatforecast.alternatives.model;

import androidx.annotation.Nullable;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.alternatives.Place;

/**
 * ③ 실시간 대안 — {@code GET /api/v1/alternatives} 응답.
 * 필드 이름은 docs/api.md · docs/examples/alternatives.json 과 같습니다.
 */
public class AlternativesResult {

    /** 서버가 지금 상황으로 계산한 값 (응답 헤더 X-Data-Source: live) */
    public static final String SOURCE_LIVE = "live";
    /** 예시 응답 (docs/examples, 또는 서버가 실시간 계산을 못 해 대신 준 값) */
    public static final String SOURCE_EXAMPLE = "example";

    /** 13주차: 어디서 온 값인지 — {@link #SOURCE_LIVE} · {@link #SOURCE_EXAMPLE}, 모르면 null. JSON 에는 없음 */
    @Nullable
    public transient String source;

    /** 계산한 시각, 예: "2026-10-07T07:40:00+09:00" */
    @SerializedName("generated_at")
    public String generatedAt;

    /** 요청한 출발지 · 도착지 (출발지 · 도착지 모드) */
    @Nullable
    @SerializedName("origin")
    public Place origin;

    @Nullable
    @SerializedName("destination")
    public Place destination;

    /** odsay · saved_nearby (ODsay 를 못 써서 근처 저장 경로 사용 → "근처 저장 경로 기준") */
    @Nullable
    @SerializedName("route_source")
    public String routeSource;

    public static final String ROUTE_SOURCE_SAVED_NEARBY = "saved_nearby";

    /** 지난 출근길 재생 요청(replay=…)일 때만 {at, date, message, note} */
    @Nullable
    @SerializedName("replay")
    public Replay replay;

    public boolean isSavedNearbyRoute() {
        return ROUTE_SOURCE_SAVED_NEARBY.equals(routeSource);
    }

    /** 요청한 평소 노선 — 출발지 · 도착지 모드에서는 null */
    @SerializedName("my_route")
    public String myRoute;

    /** 요청한 평소 정류장 ({@code id}, {@code name} 만 옴) */
    @SerializedName("home_stop")
    public Stop homeStop;

    @Nullable
    @SerializedName("day")
    public Day day;

    /** 추천 요약 */
    @Nullable
    @SerializedName("recommendation")
    public Recommendation recommendation;

    @Nullable
    @SerializedName("alternatives")
    public AlternativeSet alternatives;

    /** 지하철 경로 비교 — 조회 실패면 null */
    @Nullable
    @SerializedName("subway")
    public Subway subway;

    /** 화면 순서의 대안 카드 (없으면 빈 목록) */
    public List<Option> options() {
        return alternatives == null ? new ArrayList<>() : alternatives.inOrder();
    }

    /** 평소 버스 = '기다리기' (평소 정류장에서 평소 노선), 없으면 null */
    @Nullable
    public Option usual() {
        return alternatives == null ? null : alternatives.wait;
    }
}
