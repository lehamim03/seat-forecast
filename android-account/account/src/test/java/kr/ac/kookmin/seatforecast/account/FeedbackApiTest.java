package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import kr.ac.kookmin.seatforecast.account.feedback.FeedbackApi;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackResponse;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import retrofit2.Response;

/** POST /api/v1/feedback 이 docs/api.md · docs/examples/feedback.json 형식대로 나가는지 */
public class FeedbackApiTest {

    private MockWebServer server;
    private FeedbackApi api;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        api = FeedbackApi.create(server.url("/").toString());
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    public void sendsDocsExampleBodyWithBearerToken() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\": \"fb_123\"}"));

        JsonObject example = new Gson().fromJson(UserSettingsTest.readExample("feedback.json"), JsonObject.class);
        FeedbackRequest req = new FeedbackRequest(
                example.get("uid").getAsString(),
                example.get("date").getAsString(),
                example.get("route").getAsString(),
                example.get("stop_id").getAsString(),
                example.get("boarded").getAsBoolean(),
                example.get("predicted_prob").getAsDouble(),
                example.get("memo").getAsString());

        Response<FeedbackResponse> res = api.send("Bearer test-token", req).execute();
        assertEquals(201, res.code());
        assertNotNull(res.body());
        assertEquals("fb_123", res.body().id);

        RecordedRequest recorded = server.takeRequest();
        assertEquals("POST", recorded.getMethod());
        assertEquals("/api/v1/feedback", recorded.getPath());
        assertEquals("Bearer test-token", recorded.getHeader("Authorization"));
        assertTrue(recorded.getHeader("Content-Type").startsWith("application/json"));

        JsonObject sent = new Gson().fromJson(recorded.getBody().readUtf8(), JsonObject.class);
        assertEquals(example, sent);   // 필드 이름 · 값 모두 예시 파일과 같음
    }

    @Test
    public void unknownProbabilityIsOmitted() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\": \"x\"}"));
        FeedbackRequest req = new FeedbackRequest("u", "2026-11-09", "8800", "203000063", false, null, null);
        api.send("Bearer t", req).execute();
        JsonObject sent = new Gson().fromJson(server.takeRequest().getBody().readUtf8(), JsonObject.class);
        assertFalse(sent.has("predicted_prob"));
        assertFalse(sent.get("boarded").getAsBoolean());
        assertEquals("", sent.get("memo").getAsString());
    }

    @Test
    public void serverErrorIsNotSuccessful() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\": {\"code\": \"unauthenticated\", \"message\": \"토큰 만료\"}}"));
        Response<FeedbackResponse> res = api.send("Bearer bad",
                new FeedbackRequest("u", "2026-11-09", "8800", "203000063", true, 0.5, "")).execute();
        assertFalse(res.isSuccessful());
        assertEquals(401, res.code());
    }

    @Test
    public void requestValidation() {
        assertNull(new FeedbackRequest("u", "2026-11-09", "8800", "203000063", true, 0.31, "").validate());
        assertNotNull(new FeedbackRequest("u", "2026-11-09", "8800", "203000063", true, 1.5, "").validate());
        assertNotNull(new FeedbackRequest("u", "11/09", "8800", "203000063", true, null, "").validate());
        assertNotNull(new FeedbackRequest("u", "2026-11-09", null, "203000063", true, null, "").validate());
        assertTrue(FeedbackRequest.today().matches("\\d{4}-\\d{2}-\\d{2}"));
    }
}
