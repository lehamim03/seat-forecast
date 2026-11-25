package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.data.PlaceSearch;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** 즐겨찾는 곳 추가 — GET /api/v1/places?q= */
public class PlaceSearchTest {
    private MockWebServer server;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    public void searchesWithQueryAndToken() throws Exception {
        server.enqueue(new MockResponse().setBody("{\"query\": \"국민대\", \"places\": ["
                + "{\"name\": \"국민대학교\", \"address\": \"서울 성북구 정릉동\", \"lat\": 37.610977, \"lng\": 126.995185},"
                + "{\"name\": \"좌표 없음\"}]}"));
        List<Place> places = new PlaceSearch(server.url("/").toString()).searchNow(" 국민대 정문 ", "tok");
        RecordedRequest req = server.takeRequest();
        assertEquals("/api/v1/places", req.getRequestUrl().encodedPath());
        assertEquals("국민대 정문", req.getRequestUrl().queryParameter("q"));
        assertEquals("Bearer tok", req.getHeader("Authorization"));
        assertEquals(1, places.size());
        assertEquals("국민대학교", places.get(0).name);
        assertEquals("서울 성북구 정릉동", places.get(0).address);
    }

    @Test
    public void noTokenNoHeaderAndServerMessage() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400)
                .setBody("{\"error\": {\"code\": \"bad_request\", \"message\": \"검색어를 입력해 주세요.\"}}"));
        try {
            new PlaceSearch(server.url("/").toString()).searchNow("x", null);
            fail();
        } catch (IOException e) {
            assertEquals("검색어를 입력해 주세요.", e.getMessage());
        }
        assertNull(server.takeRequest().getHeader("Authorization"));
    }
}
