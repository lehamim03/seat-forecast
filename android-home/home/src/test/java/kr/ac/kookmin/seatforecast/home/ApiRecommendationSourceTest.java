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

import kr.ac.kookmin.seatforecast.home.data.ApiRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

public class ApiRecommendationSourceTest {

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

    @Test
    public void requestsRecommendationsWithBearerToken() throws Exception {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json; charset=utf-8")
                .setBody(RecommendationsParseTest.readExample("recommendations.json")));

        Recommendations r = source.load("203000063", "8800", "test-id-token");

        RecordedRequest req = server.takeRequest();
        assertEquals("GET", req.getMethod());
        assertEquals("/api/v1/recommendations", req.getRequestUrl().encodedPath());
        assertEquals("203000063", req.getRequestUrl().queryParameter("home_stop"));
        assertEquals("8800", req.getRequestUrl().queryParameter("route"));
        assertEquals("Bearer test-id-token", req.getHeader("Authorization"));

        assertEquals(11, r.ranking.size());
        assertEquals("매탄위브하늘채.경기아트센터", r.ranking.get(5).stop.name);
    }

    @Test
    public void baseUrlWithoutSlash() throws Exception {
        String url = server.url("/").toString();
        source = new ApiRecommendationSource(url.substring(0, url.length() - 1));
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load("203000063", "8800", "t");

        assertEquals("/api/v1/recommendations", server.takeRequest().getRequestUrl().encodedPath());
    }

    @Test
    public void noTokenNoAuthorizationHeader() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load("203000063", "8800", null);

        assertNull(server.takeRequest().getHeader("Authorization"));
    }

    @Test
    public void errorBodyMessage() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\": {\"code\": \"unauthorized\", \"message\": \"로그인이 만료되었습니다.\"}}"));
        try {
            source.load("203000063", "8800", "expired");
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
            source.load("203000063", "8800", "t");
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
            source.load("203000063", "8800", "t");
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
            source.load("203000063", "8800", "t");
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
            source.load("203000063", "8800", "t");
            fail();
        } catch (SourceException e) {
            assertEquals("추천 데이터 형식이 올바르지 않습니다.", e.getMessage());
        }
    }

    @Test
    public void emptyBodyWith200() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));
        try {
            source.load("203000063", "8800", "t");
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
            source.load("203000063", "8800", "expired");
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
            source.load("203000063", "8800", "t");
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

        source.load("203000063", "8800", "t");

        assertEquals("/api/v1/recommendations", server.takeRequest().getRequestUrl().encodedPath());
    }

    /** 정류장 ID · 노선이 쿼리에 그대로 (인코딩 포함) */
    @Test
    public void queryEncoding() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"ranking\":[]}"));

        source.load("203000063", "M5107 급행", "t");

        assertEquals("M5107 급행", server.takeRequest().getRequestUrl().queryParameter("route"));
    }
}
