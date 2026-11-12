package kr.ac.kookmin.seatforecast.home.model;

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

    /** 요청한 평소 노선 */
    @SerializedName("my_route")
    public String myRoute;

    /** 요청한 평소 정류장 ({@code id}, {@code name} 만 옴) */
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
