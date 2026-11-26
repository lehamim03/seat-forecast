package kr.ac.kookmin.seatforecast.alternatives;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kr.ac.kookmin.seatforecast.alternatives.data.ApiAlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** 지난 출근길 재생 — GET /api/v1/alternatives?replay=… (docs/api.md '지난 출근길 재생') */
public class ReplayApiTest {

    /** 재생 응답 — ③ 형식 + replay + 선택지마다 actual (걸어가기는 actual 없음, 다른 버스는 모두 만석) */
    static final String REPLAY_JSON = "{"
            + "\"generated_at\": \"2026-11-11T08:20:00+09:00\","
            + "\"my_route\": \"8800\","
            + "\"home_stop\": {\"id\": \"203000063\", \"name\": \"삼성1차아파트\"},"
            + "\"recommendation\": {\"kind\": \"기다리기\", \"route\": \"8800\", \"stop_name\": \"삼성1차아파트\", \"message\": \"8800 기다리기\"},"
            + "\"alternatives\": {"
            + "  \"기다리기\": {\"kind\": \"기다리기\", \"route\": \"8800\", \"stop\": {\"id\": \"203000063\", \"name\": \"삼성1차아파트\"},"
            + "    \"walk_min\": 0, \"board_prob\": 0.82, \"arrive_at\": \"09:50\", \"buses\": [],"
            + "    \"actual\": {\"boarded_at\": \"08:31\", \"full_skipped\": 1, \"arrive_at\": \"09:44\", \"total_min\": 84}},"
            + "  \"걸어가기\": {\"kind\": \"걸어가기\", \"route\": \"8800\", \"stop\": {\"id\": \"203000161\", \"name\": \"자유총연맹\"},"
            + "    \"walk_min\": 6, \"board_prob\": 0.95, \"arrive_at\": \"09:55\", \"buses\": []},"
            + "  \"다른 버스\": {\"kind\": \"다른 버스\", \"route\": \"3007\", \"stop\": {\"id\": \"203000063\", \"name\": \"삼성1차아파트\"},"
            + "    \"walk_min\": 0, \"board_prob\": 0.2, \"arrive_at\": \"10:05\", \"buses\": [],"
            + "    \"actual\": {\"boarded_at\": null, \"full_skipped\": 3, \"arrive_at\": null, \"total_min\": null}}"
            + "},"
            + "\"subway\": null,"
            + "\"replay\": {\"at\": \"2026-11-11T08:20:00+09:00\", \"date\": \"2026-11-11\","
            + "  \"message\": \"그날 1순위(8800번 · 삼성1차아파트)대로 했다면 08:31에 타서 09:44 국민대 도착, 만석으로 1대 보냄\","
            + "  \"note\": \"수집 기록으로 다시 계산\"}"
            + "}";

    private MockWebServer server;
    private ApiAlternativesSource source;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        source = new ApiAlternativesSource(server.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void sendsOnlyReplayQuery() throws Exception {
        server.enqueue(new MockResponse().setBody(REPLAY_JSON));
        source.loadReplay("2026-11-11T08:20", null);

        RecordedRequest req = server.takeRequest();
        assertEquals("/api/v1/alternatives", req.getRequestUrl().encodedPath());
        assertEquals("2026-11-11T08:20", req.getRequestUrl().queryParameter("replay"));
        // from_* · to_* 와 함께 보내면 400 이므로 replay 하나만
        assertEquals(1, req.getRequestUrl().querySize());
        assertNull(req.getHeader("Authorization"));
    }

    @Test
    public void sendsBearerWhenSignedIn() throws Exception {
        server.enqueue(new MockResponse().setBody(REPLAY_JSON));
        source.loadReplay("2026-11-11T08:20", "tok");
        assertEquals("Bearer tok", server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void parsesReplayAndActual() throws Exception {
        server.enqueue(new MockResponse().setBody(REPLAY_JSON));
        AlternativesResult r = source.loadReplay("2026-11-11T08:20", null);

        assertNotNull(r.replay);
        assertEquals("2026-11-11", r.replay.date);
        assertTrue(r.replay.message.startsWith("그날 1순위"));
        assertEquals("수집 기록으로 다시 계산", r.replay.note);

        Option wait = r.alternatives.wait;
        assertNotNull(wait.actual);
        assertEquals("08:31", wait.actual.boardedAt);
        assertEquals(1, wait.actual.fullSkipped);
        assertEquals("09:44", wait.actual.arriveAt);
        assertEquals(Integer.valueOf(84), wait.actual.totalMin);

        assertNull("actual 이 없으면 null", r.alternatives.walk.actual);

        Option other = r.alternatives.otherBus;
        assertNotNull(other.actual);
        assertNull(other.actual.boardedAt);
        assertEquals(3, other.actual.fullSkipped);
        assertNull(other.actual.arriveAt);
        assertNull(other.actual.totalMin);
    }

    @Test
    public void liveResponseHasNoReplay() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"alternatives\": {\"기다리기\": {\"kind\": \"기다리기\", \"route\": \"8800\"}}}"));
        AlternativesResult r = source.load(ApiAlternativesSourceTest.FROM, ApiAlternativesSourceTest.TO, null);
        assertNull(r.replay);
        assertNull(r.alternatives.wait.actual);
    }

    @Test
    public void replayNoDataMessageAsIs() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"error\": {\"code\": \"replay_no_data\", \"message\": \"2026-11-29 기록이 없습니다\"}}"));
        try {
            source.loadReplay("2026-11-29T08:00", null);
            fail();
        } catch (SourceException e) {
            assertEquals("2026-11-29 기록이 없습니다", e.getMessage());
            assertEquals("replay_no_data", e.code);
            assertEquals(400, e.httpStatus);
        }
    }

    /** 오류 표시 점검 — 503 route_unavailable 은 서버 문장 그대로 (다시 시도 버튼은 화면이 붙임) */
    @Test
    public void routeUnavailableMessageAsIs() {
        server.enqueue(new MockResponse().setResponseCode(503).setBody(
                "{\"error\": {\"code\": \"route_unavailable\", \"message\": \"경로 조회 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.\"}}"));
        try {
            source.load(ApiAlternativesSourceTest.FROM, ApiAlternativesSourceTest.TO, null);
            fail();
        } catch (SourceException e) {
            assertEquals("경로 조회 한도를 넘었습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
            assertEquals(503, e.httpStatus);
        }
    }
}
