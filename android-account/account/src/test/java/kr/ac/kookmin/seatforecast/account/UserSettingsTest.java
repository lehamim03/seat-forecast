package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import kr.ac.kookmin.seatforecast.account.data.UserSettings;

/** Firestore users/{uid} — 출발지 · 도착지 모드 필드 (favorites · destination · last_trip) + 예전 필드 읽기 호환 */
public class UserSettingsTest {

    /** 저장소의 docs/examples 파일 (테스트는 account/ 폴더에서 실행됨) */
    static String readExample(String name) throws IOException {
        File f = new File("../../docs/examples/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    private static final Place HOME = new Place("집", 37.2726, 127.0436);
    private static final Place SCHOOL = new Place("학교", 37.610977, 126.995185);

    /** 처음 가입: 즐겨찾는 곳 · 도착지 · 평소 정류장 없음 (고정 기본값 없음) */
    @Test
    public void defaultsHaveNoFixedPlaces() {
        UserSettings s = UserSettings.defaults();
        assertNull(s.validate());
        assertTrue(s.favorites.isEmpty());
        assertNull(s.destination);
        assertNull(s.lastDestination());
        assertNull(s.homeStopId);
        assertNull(s.myRoute);
        assertFalse(s.toMap().containsKey("home_stop_id"));
        assertFalse(s.toMap().containsKey("my_route"));
    }

    @Test
    public void favoritesAndDestinationRoundTrip() {
        UserSettings s = UserSettings.defaults();
        s.favorites.add(HOME);
        s.favorites.add(SCHOOL);
        s.destination = SCHOOL;
        s.arriveBy = "08:40";
        s.minutesBefore = 20;
        assertNull(s.validate());

        Map<String, Object> m = s.toMap();
        assertEquals(new HashSet<>(Arrays.asList("favorites", "destination", "arrive_by", "notify")), m.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> dest = (Map<String, Object>) m.get("destination");
        assertEquals(new HashSet<>(Arrays.asList("name", "lat", "lng")), dest.keySet());

        UserSettings back = UserSettings.fromMap(m);
        assertEquals(2, back.favorites.size());
        assertTrue(back.favorites.get(1).samePlace(SCHOOL));
        assertTrue(back.destination.samePlace(SCHOOL));
        assertEquals(m, back.toMap());
    }

    /** destination 이 없으면 last_trip.to 를 처음 도착지로 */
    @Test
    public void lastDestinationFallsBackToLastTrip() {
        Map<String, Object> m = new HashMap<>();
        m.put("last_trip", UserSettings.lastTripMap(new Place(Place.CURRENT_LOCATION, 37.385, 127.1233), SCHOOL));
        UserSettings s = UserSettings.fromMap(m);
        assertNotNull(s.lastTrip);
        assertEquals("현재 위치", s.lastTrip.from.name);
        assertTrue(s.lastDestination().samePlace(SCHOOL));
        // last_trip 은 saveSettings 로 쓰지 않음 (UserRepository.saveLastTrip 에서만)
        assertFalse(s.toMap().containsKey("last_trip"));
    }

    /** 예전 문서: home_stop_id · my_route 는 읽기만, destination {name, x, y} 는 lat/lng 로 */
    @Test
    public void readsOldSettingsJson() throws IOException {
        Map<String, Object> json = new Gson().fromJson(readExample("settings.json"),
                new TypeToken<Map<String, Object>>() { }.getType());
        UserSettings s = UserSettings.fromMap(json);
        assertEquals("203000063", s.homeStopId);
        assertEquals("8800", s.myRoute);
        assertEquals(3, s.candidateStopIds.size());
        assertEquals("국민대학교", s.destination.name);
        assertEquals(126.9971, s.destination.lng, 1e-9);
        assertEquals(37.6108, s.destination.lat, 1e-9);
        assertEquals("09:00", s.arriveBy);
        assertNull(s.validate());
        // 다시 저장하면 새 형식만
        Map<String, Object> written = s.toMap();
        assertFalse(written.containsKey("home_stop_id"));
        @SuppressWarnings("unchecked")
        Map<String, Object> dest = (Map<String, Object>) written.get("destination");
        assertEquals(37.6108, (Double) dest.get("lat"), 1e-9);
    }

    @Test
    public void rejectsBadFavorites() {
        UserSettings s = UserSettings.defaults();
        s.favorites.add(new Place("", 37.5, 127.0));
        assertNotNull(s.validate());

        UserSettings out = UserSettings.defaults();
        out.favorites.add(new Place("뉴욕", 40.7, -74.0));   // 한국 밖
        assertNotNull(out.validate());

        UserSettings dup = UserSettings.defaults();
        dup.favorites.add(HOME);
        dup.favorites.add(new Place("집", 37.3, 127.1));
        assertNotNull(dup.validate());

        UserSettings many = UserSettings.defaults();
        for (int i = 0; i <= UserSettings.MAX_FAVORITES; i++) many.favorites.add(new Place("곳" + i, 37.5, 127.0));
        assertNotNull(many.validate());
    }

    @Test
    public void rejectsBadTime() {
        UserSettings s = UserSettings.defaults();
        s.arriveBy = "9시";
        assertNotNull(s.validate());
    }

    @Test
    public void firestoreLongNumbersAreAccepted() {
        // Firestore 는 정수를 Long 으로 돌려줌 — 좌표 · 분
        Map<String, Object> m = UserSettings.defaults().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> notify = (Map<String, Object>) m.get("notify");
        notify.put("minutes_before", 15L);
        Map<String, Object> fav = new HashMap<>();
        fav.put("name", "역");
        fav.put("lat", 37L);
        fav.put("lng", 127L);
        List<Object> favs = new ArrayList<>();
        favs.add(fav);
        favs.add("깨진 값");
        m.put("favorites", favs);
        UserSettings s = UserSettings.fromMap(m);
        assertEquals(15, s.minutesBefore);
        assertEquals(1, s.favorites.size());
        assertEquals(37.0, s.favorites.get(0).lat, 1e-9);
    }

    @Test
    public void missingDocumentGivesDefaults() {
        assertEquals(UserSettings.defaults().toMap(), UserSettings.fromMap(null).toMap());
    }
}
