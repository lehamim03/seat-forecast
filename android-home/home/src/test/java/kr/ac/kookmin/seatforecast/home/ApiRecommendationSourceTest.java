package kr.ac.kookmin.seatforecast.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import java.util.List;

import kr.ac.kookmin.seatforecast.home.data.ApiRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class ApiRecommendationSourceTest {

    /** 에뮬레이터 위치 시험 좌표 (분당 서현역 → 국민대학교) */
    static final Place FROM = new Place(Place.CURRENT_LOCATION, 37.3850, 127.1233);
    static final Place TO = new Place("국민대학교", 37.6110, 126.9952);

    private MockWebServer server;
    private ApiRecommendationSource source;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        source = new ApiRecommendationSource(server.url("/").toString());
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    /** 출발지 · 도착지 모드: from_* · to_* 6개가 붙고, 예전 home_stop · route 는 보내지 않음 */
    @Test
    public void requestsWithFromAndTo() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(RecommendationsParseTest.readTripExample("trip_recommendations.json")));

        Recommendations r = source.load(FROM, TO, "test-id-token");

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/recommendations", req.getRequestUrl().encodedPath());
        assertEquals(37.385, Double.parseDouble(req.getRequestUrl().queryParameter("from_lat")), 1e-9);
        assertEquals(127.1233, Double.parseDouble(req.getRequestUrl().queryParameter("from_lng")), 1e-9);
        assertEquals("현재 위치", req.getRequestUrl().queryParameter("from_name"));
        assertEquals(37.611, Double.parseDouble(req.getRequestUrl().queryParameter("to_lat")), 1e-9);
        assertEquals(126.9952, Double.parseDouble(req.getRequestUrl().queryParameter("to_lng")), 1e-9);
        assertEquals("국민대학교", req.getRequestUrl().queryParameter("to_name"));
        assertNull(req.getRequestUrl().queryParameter("home_stop"));
        assertNull(req.getRequestUrl().queryParameter("route"));
        assertEquals("Bearer test-id-token", req.getHeader("Authorization"));

        assertEquals("국민대학교", r.destination.name);
        assertEquals(3, r.ranking.size());
    }

    /** 좌표는 지역 설정과 관계없이 점(.) 소수 — 쉼표를 쓰는 지역에서도 서버가 읽을 수 있게 */
    @Test
    public void coordinatesUseDot() throws Exception {
        java.util.Locale saved = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.GERMANY);
        try {
            server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));
            source.load(FROM, TO, "t");
            assertEquals("37.385", server.takeRequest().getRequestUrl().queryParameter("from_lat"));
        } finally {
            java.util.Locale.setDefault(saved);
        }
    }

    @Test
    public void baseUrlWithoutSlash() throws Exception {
        String url = server.url("/").toString();
        source = new ApiRecommendationSource(url.substring(0, url.length() - 1));
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load(FROM, TO, "t");

        assertEquals("/api/v1/recommendations", server.takeRequest().getRequestUrl().encodedPath());
    }

    @Test
    public void noTokenNoAuthorizationHeader() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load(FROM, TO, null);

        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void errorBodyMessage() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\": {\"code\": \"unauthorized\", \"message\": \"로그인이 만료되었습니다.\"}}"));
        try {
            source.load(FROM, TO, "expired");
            fail();
        } catch (SourceException e) {
            assertEquals(401, e.httpStatus);
            assertEquals("unauthorized", e.code);
            assertEquals("로그인이 만료되었습니다.", e.getMessage());
        }
    }

    @Test
    public void errorWithoutBody() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals(500, e.httpStatus);
            assertNull(e.code);
            assertEquals("추천 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요. (500)", e.getMessage());
        }
    }

    @Test
    public void serverDown() throws IOException {
        server.shutdown();
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals(0, e.httpStatus);
            assertEquals(SourceException.CODE_OFFLINE, e.code);
            assertEquals("서버에 연결할 수 없습니다. 인터넷 연결을 확인하고 다시 시도해 주세요.", e.getMessage());
        }
    }

    // ── 13주차: 서버 연결 ──

    @Test
    public void slowServerTimesOut() {
        OkHttpClient quick = new OkHttpClient.Builder().readTimeout(300, TimeUnit.MILLISECONDS).build();
        source = new ApiRecommendationSource(server.url("/").toString(), quick);
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}").setHeadersDelay(2, TimeUnit.SECONDS));
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals(0, e.httpStatus);
            assertEquals(SourceException.CODE_TIMEOUT, e.code);
            assertEquals("서버 응답이 늦어요. 잠시 후 다시 시도해 주세요.", e.getMessage());
        }
    }

    @Test
    public void defaultClientTimeouts() {
        OkHttpClient c = ApiRecommendationSource.defaultClient();
        assertEquals(10_000, c.connectTimeoutMillis());
        assertEquals(20_000, c.readTimeoutMillis());
        assertEquals(30_000, c.callTimeoutMillis());
    }

    /** 프록시 · 잘못된 주소가 200 으로 HTML 을 돌려줄 때 '연결할 수 없음' 이 아니라 형식 오류 */
    @Test
    public void htmlWith200IsFormatError() {
        server.enqueue(new MockResponse().setHeader("Content-Type", "text/html")
                .setBody("<html><body>로그인 페이지</body></html>"));
        assertFormatError();
    }

    @Test
    public void truncatedJsonIsFormatError() {
        server.enqueue(new MockResponse().setBody("{\"ranking\": [ {\"route\": \"3007\""));
        assertFormatError();
    }

    @Test
    public void wrongTypeIsFormatError() {
        server.enqueue(new MockResponse().setBody("{\"ranking\": \"없음\"}"));
        assertFormatError();
    }

    private void assertFormatError() {
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("추천 데이터 형식이 올바르지 않습니다.", e.getMessage());
        }
    }

    @Test
    public void emptyBodyWith200() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            // Gson 은 빈 본문을 null 로 읽음 → 비어 있음 / 형식 오류 중 하나 (연결 실패로 보이지 않으면 됨)
            assertTrue(e.getMessage(), e.getMessage().startsWith("추천 데이터"));
        }
    }

    @Test
    public void unauthorizedFlag() {
        server.enqueue(new MockResponse().setResponseCode(401).setBody(""));
        try {
            source.load(FROM, TO, "expired");
            fail();
        } catch (SourceException e) {
            assertTrue(e.isUnauthorized());
            assertEquals("로그인이 필요합니다.", e.getMessage());
        }
    }

    @Test
    public void serverErrorsHaveFriendlyMessages() {
        assertEquals("추천 서버에 문제가 있어요. 잠시 후 다시 시도해 주세요. (503)", statusMessage(503, ""));
        assertEquals("서버 응답이 늦어요. 잠시 후 다시 시도해 주세요.", statusMessage(504, ""));
        assertEquals("추천 서버 주소가 올바르지 않습니다. (404)", statusMessage(404, "<html>Not Found</html>"));
        assertEquals("요청이 많아요. 잠시 후 다시 시도해 주세요. (429)", statusMessage(429, ""));
        // 서버 문장이 있으면 그대로, 비었거나 null 이면 기본 문구
        assertEquals("정류장 ID 가 올바르지 않습니다.",
                statusMessage(400, "{\"error\": {\"code\": \"bad_stop\", \"message\": \"정류장 ID 가 올바르지 않습니다.\"}}"));
        assertEquals("추천을 불러오지 못했습니다. (400)",
                statusMessage(400, "{\"error\": {\"code\": \"bad_stop\", \"message\": null}}"));
        assertEquals("추천을 불러오지 못했습니다. (400)", statusMessage(400, "{\"error\": {\"message\": \"  \"}}"));
    }

    private String statusMessage(int status, String body) {
        server.enqueue(new MockResponse().setResponseCode(status).setBody(body));
        try {
            source.load(FROM, TO, "t");
            fail();
            return null;
        } catch (SourceException e) {
            assertFalse(e.isUnauthorized());
            assertEquals(status, e.httpStatus);
            return e.getMessage();
        }
    }

    @Test
    public void baseUrlWithSpaces() throws Exception {
        source = new ApiRecommendationSource("  " + server.url("/") + "  ");
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load(FROM, TO, "t");

        assertEquals("/api/v1/recommendations", server.takeRequest().getRequestUrl().encodedPath());
    }

    /** 장소 이름(띄어쓰기 · 괄호 · &)이 쿼리에 그대로 (인코딩 포함) */
    @Test
    public void queryEncoding() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load(new Place("서현역 (AK&플라자)", 37.385, 127.1233), TO, "t");

        assertEquals("서현역 (AK&플라자)", server.takeRequest().getRequestUrl().queryParameter("from_name"));
    }

    /** 503 route_unavailable · 400 bad_request — 서버 문장을 그대로 */
    @Test
    public void tripErrorsShowServerMessage() {
        assertEquals("경로를 찾지 못했습니다. 잠시 후 다시 시도해 주세요.", statusMessage(503,
                "{\"error\": {\"code\": \"route_unavailable\", \"message\": \"경로를 찾지 못했습니다. 잠시 후 다시 시도해 주세요.\"}}"));
        assertEquals("출발지와 도착지 좌표(from_lat · from_lng · to_lat · to_lng)가 모두 필요합니다", statusMessage(400,
                "{\"error\": {\"code\": \"bad_request\", \"message\": \"출발지와 도착지 좌표(from_lat · from_lng · to_lat · to_lng)가 모두 필요합니다\"}}"));
    }

    @Test
    public void routeUnavailableCode() {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\": {\"code\": \"route_unavailable\", \"message\": \"경로를 찾지 못했습니다.\"}}"));
        try {
            source.load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("route_unavailable", e.code);
            assertEquals(503, e.httpStatus);
            assertFalse(e.isUnauthorized());
        }
    }

    // ── 장소 검색 GET /api/v1/places ──

    @Test
    public void searchesPlaces() throws Exception {
        server.enqueue(new MockResponse().setBody(RecommendationsParseTest.readTripExample("trip_places.json")));

        List<Place> places = source.search("  국민대 정문 ", "tok");

        RecordedRequest req = server.takeRequest();
        assertEquals("/api/v1/places", req.getRequestUrl().encodedPath());
        assertEquals("국민대 정문", req.getRequestUrl().queryParameter("q"));
        assertEquals("Bearer tok", req.getHeader("Authorization"));
        assertEquals(2, places.size());
        assertEquals("국민대학교", places.get(0).name);
        assertEquals("서울 성북구 정릉동", places.get(0).address);
        assertEquals(37.610977, places.get(0).lat, 1e-9);
        assertEquals(126.995185, places.get(0).lng, 1e-9);
    }

    /** places 가 없거나 좌표 없는 항목은 빼고 빈 목록 */
    @Test
    public void placesMissingOrWithoutCoords() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"query\": \"x\"}"));
        assertTrue(source.search("x", null).isEmpty());
        assertNull(server.takeRequest().getHeader("Authorization"));

        server.enqueue(new MockResponse().setBody("{\"places\": [{\"name\": \"좌표 없음\"}, null,"
                + " {\"name\": \"역\", \"lat\": 37.5, \"lng\": 127.0}]}"));
        List<Place> places = source.search("역", null);
        assertEquals(1, places.size());
        assertEquals("역", places.get(0).name);
    }

    @Test
    public void placesError() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\": {\"code\": \"bad_request\", \"message\": \"검색어를 입력해 주세요.\"}}"));
        try {
            source.search("x", null);
            fail();
        } catch (SourceException e) {
            assertEquals("검색어를 입력해 주세요.", e.getMessage());
        }
    }

    // ── 지난 출근길 재생 replay=… ──

    /** replay 만 보내고 from_* · to_* 는 보내지 않음 (함께 쓰면 서버가 400) */
    @Test
    public void replayRequest() throws Exception {
        server.enqueue(new MockResponse().setBody(RecommendationsParseTest.readTripExample("replay_recommendations.json")));

        Recommendations r = source.replay("2026-11-12T07:40", "tok");

        RecordedRequest req = server.takeRequest();
        assertEquals("/api/v1/recommendations", req.getRequestUrl().encodedPath());
        assertEquals("2026-11-12T07:40", req.getRequestUrl().queryParameter("replay"));
        assertNull(req.getRequestUrl().queryParameter("from_lat"));
        assertNull(req.getRequestUrl().queryParameter("to_lat"));
        assertEquals("Bearer tok", req.getHeader("Authorization"));
        assertEquals("2026-11-12", r.replay.date);
    }

    @Test
    public void replayDays() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"days\": [\"2026-10-07\", \"2026-11-12\"]}"));
        assertEquals(java.util.Arrays.asList("2026-10-07", "2026-11-12"), source.replayDays(null));
        assertEquals("/api/v1/replay/days", server.takeRequest().getRequestUrl().encodedPath());

        server.enqueue(new MockResponse().setBody("{}"));
        assertTrue(source.replayDays(null).isEmpty());
    }

    @Test
    public void replayNoData() {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\": {\"code\": \"replay_no_data\", \"message\": \"그날 수집 기록이 없습니다.\"}}"));
        try {
            source.replay("2026-01-01T07:40", null);
            fail();
        } catch (SourceException e) {
            assertEquals("그날 수집 기록이 없습니다.", e.getMessage());
            assertEquals("replay_no_data", e.code);
        }
    }
}
