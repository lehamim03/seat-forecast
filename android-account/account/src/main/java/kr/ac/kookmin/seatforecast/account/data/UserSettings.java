package kr.ac.kookmin.seatforecast.account.data;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 마이페이지 설정 — Firestore users/{uid} 문서. 필드 이름은 docs/examples/settings.json 과 같습니다.
 */
public class UserSettings {

    public static final String HOME_STOP_ID = "home_stop_id";
    public static final String CANDIDATE_STOP_IDS = "candidate_stop_ids";
    public static final String MY_ROUTE = "my_route";
    public static final String DESTINATION = "destination";
    public static final String ARRIVE_BY = "arrive_by";
    public static final String NOTIFY = "notify";

    public String homeStopId = "203000063";
    public List<String> candidateStopIds = new ArrayList<>();
    public String myRoute = "8800";
    public String destinationName = Stops.DESTINATION_NAME;
    public double destinationX = Stops.DESTINATION_X;
    public double destinationY = Stops.DESTINATION_Y;
    public String arriveBy = "09:00";
    public boolean notifyDeparture = true;
    public boolean notifyIncident = true;
    public int minutesBefore = 10;

    /** 처음 가입한 사용자의 기본값: 주변 정류장 3곳 모두 비교 */
    public static UserSettings defaults() {
        UserSettings s = new UserSettings();
        for (Stops.Stop stop : Stops.ALL) s.candidateStopIds.add(stop.id);
        return s;
    }

    /** 저장 전 검사. 문제가 없으면 null. */
    public String validate() {
        if (Stops.find(homeStopId) == null) return "평소 정류장을 고르세요.";
        if (!Stops.routesAt(homeStopId).contains(myRoute)) {
            return Stops.nameOf(homeStopId) + " 정류장에는 " + myRoute + " 버스가 서지 않습니다.";
        }
        if (!candidateStopIds.contains(homeStopId)) return "비교할 정류장에 평소 정류장을 포함하세요.";
        if (!Validators.isTime(arriveBy)) return "도착 목표 시각을 HH:MM 으로 입력하세요.";
        if (minutesBefore < 0 || minutesBefore > 120) return "알림 시각은 0~120분 전으로 정하세요.";
        return null;
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> destination = new HashMap<>();
        destination.put("name", destinationName);
        destination.put("x", destinationX);
        destination.put("y", destinationY);

        Map<String, Object> notify = new HashMap<>();
        notify.put("departure", notifyDeparture);
        notify.put("incident", notifyIncident);
        notify.put("minutes_before", minutesBefore);

        Map<String, Object> m = new HashMap<>();
        m.put(HOME_STOP_ID, homeStopId);
        m.put(CANDIDATE_STOP_IDS, new ArrayList<>(candidateStopIds));
        m.put(MY_ROUTE, myRoute);
        m.put(DESTINATION, destination);
        m.put(ARRIVE_BY, arriveBy);
        m.put(NOTIFY, notify);
        return m;
    }

    /** Firestore 문서 → 설정. 없는 값은 기본값을 씁니다. */
    @NonNull
    @SuppressWarnings("unchecked")
    public static UserSettings fromMap(Map<String, Object> m) {
        UserSettings s = defaults();
        if (m == null) return s;
        if (m.get(HOME_STOP_ID) instanceof String) s.homeStopId = (String) m.get(HOME_STOP_ID);
        if (m.get(CANDIDATE_STOP_IDS) instanceof List) {
            s.candidateStopIds = new ArrayList<>();
            for (Object o : (List<Object>) m.get(CANDIDATE_STOP_IDS)) {
                if (o instanceof String) s.candidateStopIds.add((String) o);
            }
        }
        if (m.get(MY_ROUTE) instanceof String) s.myRoute = (String) m.get(MY_ROUTE);
        if (m.get(ARRIVE_BY) instanceof String) s.arriveBy = (String) m.get(ARRIVE_BY);
        if (m.get(DESTINATION) instanceof Map) {
            Map<String, Object> d = (Map<String, Object>) m.get(DESTINATION);
            if (d.get("name") instanceof String) s.destinationName = (String) d.get("name");
            if (d.get("x") instanceof Number) s.destinationX = ((Number) d.get("x")).doubleValue();
            if (d.get("y") instanceof Number) s.destinationY = ((Number) d.get("y")).doubleValue();
        }
        if (m.get(NOTIFY) instanceof Map) {
            Map<String, Object> n = (Map<String, Object>) m.get(NOTIFY);
            if (n.get("departure") instanceof Boolean) s.notifyDeparture = (Boolean) n.get("departure");
            if (n.get("incident") instanceof Boolean) s.notifyIncident = (Boolean) n.get("incident");
            if (n.get("minutes_before") instanceof Number) {
                s.minutesBefore = ((Number) n.get("minutes_before")).intValue();
            }
        }
        return s;
    }
}
