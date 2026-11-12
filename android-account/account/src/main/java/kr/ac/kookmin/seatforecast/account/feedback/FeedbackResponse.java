package kr.ac.kookmin.seatforecast.account.feedback;

import com.google.gson.annotations.SerializedName;

/** POST /api/v1/feedback 응답: 201 Created + {"id": "..."} */
public class FeedbackResponse {
    @SerializedName("id")
    public String id;
}
