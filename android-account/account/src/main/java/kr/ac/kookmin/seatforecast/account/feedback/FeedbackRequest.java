package kr.ac.kookmin.seatforecast.account.feedback;

import com.google.gson.annotations.SerializedName;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

/** POST /api/v1/feedback 요청 본문 — docs/examples/feedback.json 과 같은 형식 */
public class FeedbackRequest {

    @SerializedName("uid")
    public String uid;
    /** "2026-10-07" (한국 날짜) */
    @SerializedName("date")
    public String date;
    @SerializedName("route")
    public String route;
    @SerializedName("stop_id")
    public String stopId;
    @SerializedName("boarded")
    public boolean boarded;
    /** 그때 앱이 보여준 탑승 확률 0.0~1.0, 모르면 null */
    @SerializedName("predicted_prob")
    public Double predictedProb;
    @SerializedName("memo")
    public String memo = "";

    public FeedbackRequest() {
    }

    public FeedbackRequest(String uid, String date, String route, String stopId,
                           boolean boarded, Double predictedProb, String memo) {
        this.uid = uid;
        this.date = date;
        this.route = route;
        this.stopId = stopId;
        this.boarded = boarded;
        this.predictedProb = predictedProb;
        this.memo = memo == null ? "" : memo;
    }

    /** 오늘 날짜 (한국 시간) */
    public static String today() {
        SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
        return f.format(new Date());
    }

    /** 확률은 0~1 이어야 하고, 노선 · 정류장 · 날짜는 꼭 있어야 합니다. 문제가 없으면 null. */
    public String validate() {
        if (route == null || route.isEmpty()) return "노선을 고르세요.";
        if (stopId == null || stopId.isEmpty()) return "정류장을 고르세요.";
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) return "날짜 형식이 올바르지 않습니다.";
        if (predictedProb != null && (predictedProb < 0 || predictedProb > 1)) return "확률은 0~1 사이여야 합니다.";
        return null;
    }

    /** Firestore 기록용 (users/{uid}/feedback/{id}) */
    public Map<String, Object> toMap() {
        Map<String, Object> m = new HashMap<>();
        m.put("uid", uid);
        m.put("date", date);
        m.put("route", route);
        m.put("stop_id", stopId);
        m.put("boarded", boarded);
        m.put("predicted_prob", predictedProb);
        m.put("memo", memo);
        return m;
    }
}
