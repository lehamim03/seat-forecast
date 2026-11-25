package kr.ac.kookmin.seatforecast.alternatives;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import kr.ac.kookmin.seatforecast.alternatives.data.ApiAlternativesSource;
import kr.ac.kookmin.seatforecast.alternatives.data.SourceException;
import kr.ac.kookmin.seatforecast.alternatives.model.AlternativesResult;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** GET /api/v1/alternatives — 요청 주소 · 쿼리 · 헤더, 오류 응답 (docs/api.md 공통 규칙) */
public class ApiAlternativesSourceTest {

    /** 에뮬레이터 위치 시험 좌표 (분당 서현역 → 국민대학교) */
    static final Place FROM = new Place(Place.CURRENT_LOCATION, 37.3850, 127.1233);
    static final Place TO = new Place("국민대학교", 37.6110, 126.9952);

    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private String example() throws Exception {
        return new String(Files.readAllBytes(AlternativesParseTest.DOCS_EXAMPLE.toPath()), StandardCharsets.UTF_8);
    }

    @Test
    public void requestsWithQueryAndBearer() throws Exception {
        server.enqueue(new MockResponse().setBody(example()).setHeader("Content-Type", "application/json; charset=utf-8"));
        AlternativesResult r = new ApiAlternativesSource(server.url("/").toString())
                .load(FROM, TO, "id-token-123");

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/alternatives", req.getRequestUrl().encodedPath());
        // 출발지 · 도착지 모드: from_* · to_* 6개, 예전 home_stop · route 는 보내지 않음
        assertEquals(37.385, Double.parseDouble(req.getRequestUrl().queryParameter("from_lat")), 1e-9);
        assertEquals(127.1233, Double.parseDouble(req.getRequestUrl().queryParameter("from_lng")), 1e-9);
        assertEquals("현재 위치", req.getRequestUrl().queryParameter("from_name"));
        assertEquals(37.611, Double.parseDouble(req.getRequestUrl().queryParameter("to_lat")), 1e-9);
        assertEquals(126.9952, Double.parseDouble(req.getRequestUrl().queryParameter("to_lng")), 1e-9);
        assertEquals("국민대학교", req.getRequestUrl().queryParameter("to_name"));
        assertNull(req.getRequestUrl().queryParameter("home_stop"));
        assertNull(req.getRequestUrl().queryParameter("route"));
        assertEquals("Bearer id-token-123", req.getHeader("Authorization"));

        assertEquals(3, r.options().size());
        assertEquals("매탄위브하늘채.경기아트센터", r.alternatives.walk.stop.name);
    }

    /** 13주차: 응답 헤더 X-Data-Source 로 실시간 계산인지 예시 응답인지 구분 */
    @Test
    public void dataSourceHeader() throws Exception {
        server.enqueue(new MockResponse().setBody(example()).setHeader("X-Data-Source", "example"));
        server.enqueue(new MockResponse().setBody(example()).setHeader("X-Data-Source", "live"));
        server.enqueue(new MockResponse().setBody(example()));
        ApiAlternativesSource src = new ApiAlternativesSource(server.url("/").toString());
        assertEquals(AlternativesResult.SOURCE_EXAMPLE, src.load(FROM, TO, "t").source);
        assertEquals(AlternativesResult.SOURCE_LIVE, src.load(FROM, TO, "t").source);
        assertNull("헤더가 없는 서버", src.load(FROM, TO, "t").source);
    }

    @Test
    public void noTokenNoHeader() throws Exception {
        server.enqueue(new MockResponse().setBody(example()));
        new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, null);
        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void baseUrlWithoutSlash() throws Exception {
        server.enqueue(new MockResponse().setBody(example()));
        String url = server.url("/").toString();
        new ApiAlternativesSource(url.substring(0, url.length() - 1)).load(FROM, TO, "t");
        assertEquals("/api/v1/alternatives", server.takeRequest().getRequestUrl().encodedPath());
    }

    @Test
    public void errorBodyMessageIsShown() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503)
                .setBody("{\"error\": {\"code\": \"upstream_unavailable\", \"message\": \"버스 도착 정보를 가져오지 못했습니다.\"}}"));
        try {
            new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("버스 도착 정보를 가져오지 못했습니다.", e.getMessage());
            assertEquals("upstream_unavailable", e.code);
            assertEquals(503, e.httpStatus);
        }
    }

    @Test
    public void errorWithoutBodyHasDefaultMessage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500));
        try {
            new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("실시간 대안을 불러오지 못했습니다. (500)", e.getMessage());
            assertNull(e.code);
        }
        server.enqueue(new MockResponse().setResponseCode(401).setBody("unauthorized"));
        try {
            new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("로그인이 필요합니다.", e.getMessage());
        }
    }

    @Test
    public void serverDown() throws Exception {
        String url = server.url("/").toString();
        server.shutdown();
        try {
            new ApiAlternativesSource(url).load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
            assertEquals(0, e.httpStatus);
        }
    }

    /** 503 route_unavailable · 400 bad_request — 서버 문장 그대로 */
    @Test
    public void tripErrors() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(503).setBody(
                "{\"error\": {\"code\": \"route_unavailable\", \"message\": \"경로를 찾지 못했습니다. 잠시 후 다시 시도해 주세요.\"}}"));
        try {
            new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, null);
            fail();
        } catch (SourceException e) {
            assertEquals("경로를 찾지 못했습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
            assertEquals("route_unavailable", e.code);
        }
        assertNull(server.takeRequest().getHeader("Authorization"));
        server.enqueue(new MockResponse().setResponseCode(400).setBody(
                "{\"error\": {\"code\": \"bad_request\", \"message\": \"출발지와 도착지 좌표가 모두 필요합니다\"}}"));
        try {
            new ApiAlternativesSource(server.url("/").toString()).load(FROM, TO, "t");
            fail();
        } catch (SourceException e) {
            assertEquals("출발지와 도착지 좌표가 모두 필요합니다", e.getMessage());
        }
    }

    /** 장소 이름(띄어쓰기 · &)이 그대로 */
    @Test
    public void nameEncoding() throws Exception {
        server.enqueue(new MockResponse().setBody("{}"));
        new ApiAlternativesSource(server.url("/").toString())
                .load(new Place("서현역 (AK&플라자)", 37.385, 127.1233), TO, "t");
        assertEquals("서현역 (AK&플라자)", server.takeRequest().getRequestUrl().queryParameter("from_name"));
    }
}
