package kr.ac.kookmin.seatforecast.account.feedback;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.List;

import kr.ac.kookmin.seatforecast.account.data.Stops;

/** 마이페이지 '탑승 피드백 기록' 한 줄 — Firestore users/{uid}/feedback/{id} */
public class FeedbackRecord {
    public String id;
    public String date;
    public String route;
    public String stopId;
    public boolean boarded;
    public Double predictedProb;
    public String memo;
    /** 서버(POST /feedback)에 보냈는지 */
    public boolean sent;

    static FeedbackRecord from(DocumentSnapshot doc) {
        FeedbackRecord r = new FeedbackRecord();
        r.id = doc.getId();
        r.date = doc.getString("date");
        r.route = doc.getString("route");
        r.stopId = doc.getString("stop_id");
        r.boarded = Boolean.TRUE.equals(doc.getBoolean("boarded"));
        r.predictedProb = doc.getDouble("predicted_prob");
        r.memo = doc.getString("memo");
        r.sent = Boolean.TRUE.equals(doc.getBoolean(FeedbackRepository.SENT));
        return r;
    }

    FeedbackRequest toRequest(String uid) {
        return new FeedbackRequest(uid, date, route, stopId, boarded, predictedProb, memo);
    }

    /** 이번 달 기록 수와 그중 탑승 성공 수: {전체, 성공}. month 는 "2026-11" */
    public static int[] monthStats(List<FeedbackRecord> records, String month) {
        int total = 0;
        int boarded = 0;
        for (FeedbackRecord r : records) {
            if (r.date == null || !r.date.startsWith(month)) continue;
            total++;
            if (r.boarded) boarded++;
        }
        return new int[]{total, boarded};
    }

    /** 같은 날 같은 노선 기록이 이미 있는지 (마이페이지 '오늘 탑승 결과' 카드를 숨길 때) */
    public static boolean hasRecord(List<FeedbackRecord> records, String date, String route) {
        for (FeedbackRecord r : records) {
            if (date.equals(r.date) && route != null && route.equals(r.route)) return true;
        }
        return false;
    }

    /** "2026-10-07 · 3007 · 자유총연맹 — 탔어요 (예측 100%)" */
    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append(date).append(" · ").append(route).append(" · ").append(Stops.nameOf(stopId))
                .append(" — ").append(boarded ? "탔어요" : "못 탔어요");
        if (predictedProb != null) sb.append(" (예측 ").append(Math.round(predictedProb * 100)).append("%)");
        if (!sent) sb.append(" · 전송 대기");
        return sb.toString();
    }
}
