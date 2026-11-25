package kr.ac.kookmin.seatforecast.account.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import kr.ac.kookmin.seatforecast.account.Place;

/**
 * 마이페이지 설정 — Firestore users/{uid} 문서 (docs/api.md '출발지 · 도착지 모드').
 * <ul>
 *   <li>{@code favorites} [{name, lat, lng}] — 즐겨찾는 곳 (집 · 학교 등)</li>
 *   <li>{@code destination} {name, lat, lng} — 기본(최근) 도착지</li>
 *   <li>{@code last_trip} {from, to, at} — 마지막으로 검색한 출발지 · 도착지 (E 돌발 알림 대상, {@link UserRepository#saveLastTrip})</li>
 *   <li>{@code arrive_by} · {@code notify} — 예전과 같음</li>
 * </ul>
 * 예전 필드 {@code home_stop_id} · {@code my_route} · {@code candidate_stop_ids} 는 읽기만 합니다 (쓰지 않음, 기본값 없음).
 */
public class UserSettings {

    public static final String FAVORITES = "favorites";
    public static final String DESTINATION = "destination";
    public static final String LAST_TRIP = "last_trip";
    public static final String ARRIVE_BY = "arrive_by";
    public static final String NOTIFY = "notify";
    /** 예전 필드 — 읽기만 */
    public static final String HOME_STOP_ID = "home_stop_id";
    public static final String CANDIDATE_STOP_IDS = "candidate_stop_ids";
    public static final String MY_ROUTE = "my_route";

    /** 즐겨찾는 곳 최대 수 (Firestore 규칙과 같음) */
    public static final int MAX_FAVORITES = 20;

    public List<Place> favorites = new ArrayList<>();
    /** 기본(최근) 도착지, 없으면 null */
    @Nullable
    public Place destination;
    /** 마지막 검색 (읽기 전용 — 쓰기는 UserRepository.saveLastTrip), 없으면 null */
    @Nullable
    public LastTrip lastTrip;
    public String arriveBy = "09:00";
    public boolean notifyDeparture = true;
    public boolean notifyIncident = true;
    public int minutesBefore = 10;

    /** 예전 평소 정류장 · 노선 (읽기만, 없으면 null) */
    @Nullable
    public String homeStopId;
    @Nullable
    public String myRoute;
    public List<String> candidateStopIds = new ArrayList<>();

    /** 마지막 검색 {from, to, at} */
    public static class LastTrip {
        public Place from;
        public Place to;
        /** 저장한 시각 (서버 시각), 모르면 null */
        @Nullable
        public Timestamp at;
    }

    /** 처음 가입한 사용자: 즐겨찾는 곳 · 도착지 없음 (고정 기본값 없음) */
    public static UserSettings defaults() {
        return new UserSettings();
    }

    /** 처음 열 때 쓸 도착지 — destination, 없으면 last_trip.to, 둘 다 없으면 null */
    @Nullable
    public Place lastDestination() {
        if (destination != null) return destination;
        return lastTrip == null ? null : lastTrip.to;
    }

    /** 저장 전 검사. 문제가 없으면 null. */
    @Nullable
    public String validate() {
        if (favorites.size() > MAX_FAVORITES) return "즐겨찾는 곳은 " + MAX_FAVORITES + "곳까지 저장할 수 있습니다.";
        Set<String> names = new HashSet<>();
        for (Place p : favorites) {
            if (p == null || !p.isValid()) return "즐겨찾는 곳의 이름이나 위치가 올바르지 않습니다.";
            if (!names.add(p.name.trim())) return "같은 이름(" + p.name.trim() + ")의 즐겨찾는 곳이 이미 있습니다.";
        }
        if (destination != null && !destination.isValid()) return "기본 도착지의 위치가 올바르지 않습니다.";
        if (!Validators.isTime(arriveBy)) return "도착 목표 시각을 HH:MM 으로 입력하세요.";
        if (minutesBefore < 0 || minutesBefore > 120) return "알림 시각은 0~120분 전으로 정하세요.";
        return null;
    }

    /** 저장할 필드 — 예전 필드 · last_trip 은 쓰지 않음 (merge 라 문서에 남아 있어도 그대로) */
    @NonNull
    public Map<String, Object> toMap() {
        List<Map<String, Object>> favs = new ArrayList<>();
        for (Place p : favorites) favs.add(p.toMap());
        Map<String, Object> notify = new HashMap<>();
        notify.put("departure", notifyDeparture);
        notify.put("incident", notifyIncident);
        notify.put("minutes_before", minutesBefore);
        Map<String, Object> m = new HashMap<>();
        m.put(FAVORITES, favs);
        m.put(DESTINATION, destination == null ? null : destination.toMap());
        m.put(ARRIVE_BY, arriveBy);
        m.put(NOTIFY, notify);
        return m;
    }

    /** Firestore 문서 → 설정. 없는 값은 기본값, 예전 destination {name, x, y} 도 읽음. */
    @NonNull
    @SuppressWarnings("unchecked")
    public static UserSettings fromMap(@Nullable Map<String, Object> m) {
        UserSettings s = defaults();
        if (m == null) return s;
        if (m.get(FAVORITES) instanceof List) {
            for (Object o : (List<Object>) m.get(FAVORITES)) {
                Place p = Place.fromMap(o);
                if (p != null) s.favorites.add(p);
            }
        }
        s.destination = Place.fromMap(m.get(DESTINATION));
        if (m.get(LAST_TRIP) instanceof Map) {
            Map<String, Object> t = (Map<String, Object>) m.get(LAST_TRIP);
            Place from = Place.fromMap(t.get("from"));
            Place to = Place.fromMap(t.get("to"));
            if (from != null && to != null) {
                s.lastTrip = new LastTrip();
                s.lastTrip.from = from;
                s.lastTrip.to = to;
                if (t.get("at") instanceof Timestamp) s.lastTrip.at = (Timestamp) t.get("at");
            }
        }
        if (m.get(ARRIVE_BY) instanceof String) s.arriveBy = (String) m.get(ARRIVE_BY);
        if (m.get(NOTIFY) instanceof Map) {
            Map<String, Object> n = (Map<String, Object>) m.get(NOTIFY);
            if (n.get("departure") instanceof Boolean) s.notifyDeparture = (Boolean) n.get("departure");
            if (n.get("incident") instanceof Boolean) s.notifyIncident = (Boolean) n.get("incident");
            if (n.get("minutes_before") instanceof Number) {
                s.minutesBefore = ((Number) n.get("minutes_before")).intValue();
            }
        }
        // 예전 필드 (읽기만)
        if (m.get(HOME_STOP_ID) instanceof String) s.homeStopId = (String) m.get(HOME_STOP_ID);
        if (m.get(MY_ROUTE) instanceof String) s.myRoute = (String) m.get(MY_ROUTE);
        if (m.get(CANDIDATE_STOP_IDS) instanceof List) {
            for (Object o : (List<Object>) m.get(CANDIDATE_STOP_IDS)) {
                if (o instanceof String) s.candidateStopIds.add((String) o);
            }
        }
        return s;
    }

    /** last_trip 저장 값 {from, to} — at 은 UserRepository 가 서버 시각으로 넣음 */
    @NonNull
    public static Map<String, Object> lastTripMap(Place from, Place to) {
        Map<String, Object> t = new HashMap<>();
        t.put("from", from.toMap());
        t.put("to", to.toMap());
        return t;
    }
}
