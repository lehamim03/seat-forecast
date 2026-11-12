package kr.ac.kookmin.seatforecast.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import kr.ac.kookmin.seatforecast.home.data.ApiRecommendationSource;
import kr.ac.kookmin.seatforecast.home.data.SourceException;
import kr.ac.kookmin.seatforecast.home.model.Recommendations;
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
            assertEquals("추천을 불러오지 못했습니다. (500)", e.getMessage());
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
            assertEquals("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
        }
    }
}
