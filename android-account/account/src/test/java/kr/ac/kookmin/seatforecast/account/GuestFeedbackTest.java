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

import java.util.concurrent.TimeUnit;

import kr.ac.kookmin.seatforecast.account.feedback.FeedbackApi;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;
import kr.ac.kookmin.seatforecast.account.feedback.GuestFeedbackSender;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/** 둘러보기 · 로그아웃 탑승 피드백 — 헤더 없이, uid 없이, feedback.json 형식으로 서버에 보내는지 (현장 시험) */
public class GuestFeedbackTest {

    private MockWebServer server;
    private GuestFeedbackSender sender;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        sender = new GuestFeedbackSender(FeedbackApi.create(server.url("/").toString()));
    }

    @After
    public void tearDown() throws Exception {
        server.shutdown();
    }

    private static FeedbackRequest fromExample(JsonObject example) {
        return new FeedbackRequest(
                example.get("uid").getAsString(),   // 예시의 uid 가 들어 있어도 보내지 않아야 함
                example.get("date").getAsString(),
                example.get("route").getAsString(),
                example.get("stop_id").getAsString(),
                example.get("boarded").getAsBoolean(),
                example.get("predicted_prob").getAsDouble(),
                "앞에 10명, 만석 통과");
    }

    @Test
    public void sendsWithoutAuthorizationAndWithoutUid() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\": \"fb_1\"}"));
        JsonObject example = new Gson().fromJson(UserSettingsTest.readExample("feedback.json"), JsonObject.class);

        assertEquals(GuestFeedbackSender.Result.SENT, sender.sendNow(fromExample(example)));

        RecordedRequest r = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(r);
        assertEquals("POST", r.getMethod());
        assertEquals("/api/v1/feedback", r.getPath());
        assertNull("둘러보기는 Authorization 헤더 없음", r.getHeader("Authorization"));

        JsonObject sent = new Gson().fromJson(r.getBody().readUtf8(), JsonObject.class);
        // uid 는 빠지거나 null (서버 기본값 None) — 나머지 키 · 값은 feedback.json 과 같음
        assertTrue(!sent.has("uid") || sent.get("uid").isJsonNull());
        for (String key : new String[]{"date", "route", "stop_id", "boarded", "predicted_prob"}) {
            assertEquals(key, example.get(key), sent.get(key));
        }
        assertEquals("앞에 10명, 만석 통과", sent.get("memo").getAsString());
        for (String key : sent.keySet()) assertTrue("예시에 없는 키: " + key, example.has(key));
    }

    @Test
    public void unauthorizedFallsBackWithMessage() {
        server.enqueue(new MockResponse().setResponseCode(401)
                .setBody("{\"error\": {\"code\": \"unauthorized\", \"message\": \"로그인이 필요합니다\"}}"));
        FeedbackRequest req = new FeedbackRequest(null, "2026-11-27", "M4102", "206000299", false, null, "");
        GuestFeedbackSender.Result result = sender.sendNow(req);
        assertEquals(GuestFeedbackSender.Result.UNAUTHORIZED, result);
        assertNotNull(FeedbackRepository.guestMessage(result));
    }

    @Test
    public void serverDownIsFailedNotSent() throws Exception {
        server.shutdown();
        FeedbackRequest req = new FeedbackRequest(null, "2026-11-27", "M4102", "206000299", true, 0.8, "");
        GuestFeedbackSender.Result result = sender.sendNow(req);
        assertEquals(GuestFeedbackSender.Result.FAILED, result);
        assertNotNull(FeedbackRepository.guestMessage(result));
        assertNull(FeedbackRepository.guestMessage(GuestFeedbackSender.Result.SENT));
    }

    @Test
    public void badRequestIsRejected() {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{}"));
        FeedbackRequest req = new FeedbackRequest(null, "2026-11-27", "M4102", "206000299", true, 0.8, "");
        assertEquals(GuestFeedbackSender.Result.REJECTED, sender.sendNow(req));
        assertFalse(server.getRequestCount() == 0);
    }
}
