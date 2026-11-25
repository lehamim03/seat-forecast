package kr.ac.kookmin.seatforecast.account.feedback;

import com.google.gson.annotations.SerializedName;

/** POST /api/v1/feedback 응답: 201 Created + {"id": "..."} */
public class FeedbackResponse {
    @SerializedName("id")
    public String id;

    /**
     * 다시 보내도 같은 결과가 나오는 거절인지 (13주차 서버 연결).
     * 400 · 403 · 404 · 409 · 413 · 422 → true (기록을 '전송 안 됨'으로 두고 다시 보내지 않음).
     * 401(로그인 만료) · 408 · 429 · 5xx · 연결 실패 → false (다음에 다시 보냄).
     */
    public static boolean isPermanentRejection(int httpStatus) {
        switch (httpStatus) {
            case 400:
            case 403:
            case 404:
            case 409:
            case 413:
            case 422:
                return true;
            default:
                return false;
        }
    }
}
