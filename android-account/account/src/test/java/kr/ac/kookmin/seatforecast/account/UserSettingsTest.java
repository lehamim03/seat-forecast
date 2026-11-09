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
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import kr.ac.kookmin.seatforecast.account.data.UserSettings;

public class UserSettingsTest {

    /** 저장소의 docs/examples 파일 (테스트는 account/ 폴더에서 실행됨) */
    static String readExample(String name) throws IOException {
        File f = new File("../../docs/examples/" + name);
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void defaultsAreValid() {
        UserSettings s = UserSettings.defaults();
        assertNull(s.validate());
        assertEquals(3, s.candidateStopIds.size());
    }

    @Test
    public void readsDocsExampleSettingsJson() throws IOException {
        Map<String, Object> json = new Gson().fromJson(readExample("settings.json"),
                new TypeToken<Map<String, Object>>() { }.getType());
        UserSettings s = UserSettings.fromMap(json);
        assertEquals("203000063", s.homeStopId);
        assertEquals(Arrays.asList("203000063", "203000161", "203000162"), s.candidateStopIds);
        assertEquals("8800", s.myRoute);
        assertEquals("국민대학교", s.destinationName);
        assertEquals(126.9971, s.destinationX, 1e-9);
        assertEquals("09:00", s.arriveBy);
        assertTrue(s.notifyDeparture);
        assertTrue(s.notifyIncident);
        assertEquals(10, s.minutesBefore);
        assertNull(s.validate());
    }

    @Test
    public void writesSameFieldNamesAsDocsExample() throws IOException {
        Map<String, Object> json = new Gson().fromJson(readExample("settings.json"),
                new TypeToken<Map<String, Object>>() { }.getType());
        Set<String> expected = new HashSet<>(json.keySet());
        expected.remove("uid");   // uid 는 저장할 때 UserRepository 가 넣음
        Map<String, Object> written = UserSettings.defaults().toMap();
        assertEquals(expected, written.keySet());

        @SuppressWarnings("unchecked")
        Map<String, Object> notify = (Map<String, Object>) written.get("notify");
        assertEquals(new HashSet<>(Arrays.asList("departure", "incident", "minutes_before")), notify.keySet());
        @SuppressWarnings("unchecked")
        Map<String, Object> dest = (Map<String, Object>) written.get("destination");
        assertEquals(new HashSet<>(Arrays.asList("name", "x", "y")), dest.keySet());
    }

    @Test
    public void roundTrip() {
        UserSettings s = UserSettings.defaults();
        s.homeStopId = "203000161";
        s.myRoute = "7001";
        s.candidateStopIds = Arrays.asList("203000161", "203000162");
        s.arriveBy = "08:40";
        s.notifyDeparture = false;
        s.minutesBefore = 20;
        UserSettings back = UserSettings.fromMap(s.toMap());
        assertEquals(s.toMap(), back.toMap());
    }

    @Test
    public void firestoreLongNumbersAreAccepted() {
        // Firestore 는 정수를 Long 으로 돌려줌
        Map<String, Object> m = UserSettings.defaults().toMap();
        @SuppressWarnings("unchecked")
        Map<String, Object> notify = (Map<String, Object>) m.get("notify");
        notify.put("minutes_before", 15L);
        assertEquals(15, UserSettings.fromMap(m).minutesBefore);
    }

    @Test
    public void rejectsRouteThatDoesNotStopThere() {
        UserSettings s = UserSettings.defaults();
        s.homeStopId = "203000161";   // 자유총연맹
        s.myRoute = "8800";
        assertNotNull(s.validate());
    }

    @Test
    public void rejectsBadTimeAndMissingHomeStop() {
        UserSettings s = UserSettings.defaults();
        s.arriveBy = "9시";
        assertNotNull(s.validate());

        UserSettings t = UserSettings.defaults();
        t.candidateStopIds = Arrays.asList("203000161");
        assertFalse(t.candidateStopIds.contains(t.homeStopId));
        assertNotNull(t.validate());
    }

    @Test
    public void missingDocumentGivesDefaults() {
        assertEquals(UserSettings.defaults().toMap(), UserSettings.fromMap(null).toMap());
    }
}
