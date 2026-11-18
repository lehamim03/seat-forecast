package kr.ac.kookmin.seatforecast.alternatives;

/** 평소 정류장 · 노선 — {@code GET /alternatives} 요청 값 (D 의 UserSettings.homeStopId · myRoute). */
public class UsualTrip {

    public final String homeStopId;
    public final String route;

    public UsualTrip(String homeStopId, String route) {
        this.homeStopId = homeStopId;
        this.route = route;
    }
}
