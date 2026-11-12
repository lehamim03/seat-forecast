package kr.ac.kookmin.seatforecast.account;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import kr.ac.kookmin.seatforecast.account.data.AuthRepository;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.data.UserSettings;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackApi;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRecord;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRepository;
import kr.ac.kookmin.seatforecast.account.feedback.FeedbackRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * 실제 Firebase SDK 로 로컬 Firebase 에뮬레이터(Auth 9099 · Firestore 8080)에 붙어
 * 회원가입 → 로그인 → 설정 저장/읽기 → 탑승 피드백(POST /feedback) 흐름을 확인합니다.
 *
 * 실행 전: android-account/firebase 에서 firebase emulators:start --project demo-seat-forecast
 */
@RunWith(AndroidJUnit4.class)
public class FirebaseEmulatorTest {

    private static final String PASSWORD = "test1234";

    private final AuthRepository auth = new AuthRepository();
    private final UserRepository users = new UserRepository();
    private MockWebServer server;

    @BeforeClass
    public static void connectEmulator() {
        Context ctx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(ctx, new FirebaseOptions.Builder()
                    .setProjectId("demo-seat-forecast")
                    .setApplicationId("1:000000000000:android:0000000000000000")
                    .setApiKey("demo-api-key")
                    .build());
            Account.useEmulator("10.0.2.2");
        }
    }

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @After
    public void tearDown() throws Exception {
        Account.signOut();
        server.shutdown();
    }

    // ---------- 결과 기다리기 ----------

    private static class Result<T> implements Callback<T> {
        final CountDownLatch latch = new CountDownLatch(1);
        T value;
        String error;

        @Override
        public void onSuccess(T result) {
            value = result;
            latch.countDown();
        }

        @Override
        public void onError(String message) {
            error = message;
            latch.countDown();
        }

        Result<T> await() throws InterruptedException {
            assertTrue("시간 초과", latch.await(20, TimeUnit.SECONDS));
            return this;
        }

        T ok() throws InterruptedException {
            await();
            assertNull("오류: " + error, error);
            return value;
        }
    }

    private static String newEmail() {
        return "test-" + UUID.randomUUID().toString().substring(0, 8) + "@kookmin.ac.kr";
    }

    private FirebaseUser signUp(String email, String nickname) throws InterruptedException {
        Result<FirebaseUser> r = new Result<>();
        auth.signUp(email, PASSWORD, nickname, r);
        return r.ok();
    }

    private DocumentSnapshot userDoc(String uid) throws ExecutionException, InterruptedException, TimeoutException {
        return Tasks.await(FirebaseFirestore.getInstance().collection("users").document(uid).get(),
                20, TimeUnit.SECONDS);
    }

    private FeedbackRepository feedbackRepo() {
        return new FeedbackRepository(FirebaseFirestore.getInstance(), FeedbackApi.create(server.url("/").toString()));
    }

    // ---------- 테스트 ----------

    @Test
    public void signUpCreatesUserDocumentWithProfileAndDefaults() throws Exception {
        String email = newEmail();
        FirebaseUser user = signUp(email, "창록");
        assertTrue(Account.isSignedIn());
        assertEquals(user.getUid(), Account.currentUid());

        DocumentSnapshot doc = userDoc(user.getUid());
        assertTrue(doc.exists());
        assertEquals(user.getUid(), doc.getString("uid"));
        assertEquals(email, doc.getString("email"));
        assertEquals("창록", doc.getString("nickname"));
        assertNotNull(doc.getTimestamp("created_at"));
        assertEquals("203000063", doc.getString("home_stop_id"));
        assertEquals("8800", doc.getString("my_route"));
        assertEquals("09:00", doc.getString("arrive_by"));
    }

    @Test
    public void signInAndErrors() throws Exception {
        String email = newEmail();
        signUp(email, "로그인테스트");
        Account.signOut();
        assertFalse(Account.isSignedIn());

        Result<FirebaseUser> wrong = new Result<>();
        auth.signIn(email, "wrong-pass", wrong);
        wrong.await();
        assertEquals("이메일 또는 비밀번호가 올바르지 않습니다.", wrong.error);

        Result<FirebaseUser> again = new Result<>();
        auth.signUp(email, PASSWORD, "중복", again);
        again.await();
        assertEquals("이미 가입된 이메일입니다.", again.error);
        Account.signOut();

        Result<FirebaseUser> ok = new Result<>();
        auth.signIn(email, PASSWORD, ok);
        assertEquals(email, ok.ok().getEmail());

        Result<String> token = new Result<>();
        Account.getIdToken(token);
        assertFalse(token.ok().isEmpty());
    }

    @Test
    public void saveAndLoadSettings() throws Exception {
        FirebaseUser user = signUp(newEmail(), "설정");
        // 다른 화면(E)이 같은 문서에 넣은 필드는 지워지지 않아야 함
        Tasks.await(users.userDoc(user.getUid()).update("fcm_token", "token-from-E"), 20, TimeUnit.SECONDS);

        UserSettings s = UserSettings.defaults();
        s.homeStopId = "203000161";
        s.myRoute = "7001";
        s.candidateStopIds = Arrays.asList("203000161", "203000162");
        s.arriveBy = "08:40";
        s.notifyDeparture = false;
        s.notifyIncident = true;
        s.minutesBefore = 20;

        Result<Void> saved = new Result<>();
        users.saveSettings(user.getUid(), s, saved);
        saved.ok();

        Result<UserSettings> loaded = new Result<>();
        users.loadSettings(user.getUid(), loaded);
        assertEquals(s.toMap(), loaded.ok().toMap());

        DocumentSnapshot doc = userDoc(user.getUid());
        assertEquals("token-from-E", doc.getString("fcm_token"));
        assertEquals("설정", doc.getString("nickname"));
        assertEquals(20L, doc.get("notify.minutes_before"));

        // 잘못된 설정은 저장하지 않음
        UserSettings bad = UserSettings.defaults();
        bad.homeStopId = "203000161";
        bad.myRoute = "8800";
        Result<Void> rejected = new Result<>();
        users.saveSettings(user.getUid(), bad, rejected);
        rejected.await();
        assertNotNull(rejected.error);
        Result<UserSettings> still = new Result<>();
        users.loadSettings(user.getUid(), still);
        assertEquals("7001", still.ok().myRoute);
    }

    @Test
    public void securityRulesBlockOtherUsersDocument() throws Exception {
        FirebaseUser a = signUp(newEmail(), "A");
        String uidA = a.getUid();
        Account.signOut();
        signUp(newEmail(), "B");
        try {
            userDoc(uidA);
            fail("다른 사용자의 문서를 읽을 수 있으면 안 됨");
        } catch (ExecutionException e) {
            assertTrue(e.getCause() instanceof FirebaseFirestoreException);
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    ((FirebaseFirestoreException) e.getCause()).getCode());
        }
    }

    @Test
    public void feedbackIsStoredAndPostedToServer() throws Exception {
        FirebaseUser user = signUp(newEmail(), "피드백");
        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\": \"srv-1\"}"));

        Result<FeedbackRepository.SubmitResult> r = new Result<>();
        feedbackRepo().submit(new FeedbackRequest(null, "2026-10-07", "3007", "203000161", true, 1.0, ""), r);
        FeedbackRepository.SubmitResult result = r.ok();
        assertTrue(result.sentToServer);

        RecordedRequest req = server.takeRequest(10, TimeUnit.SECONDS);
        assertNotNull(req);
        assertEquals("/api/v1/feedback", req.getPath());
        assertTrue(req.getHeader("Authorization").startsWith("Bearer "));
        JsonObject body = new Gson().fromJson(req.getBody().readUtf8(), JsonObject.class);
        assertEquals(user.getUid(), body.get("uid").getAsString());
        assertEquals("3007", body.get("route").getAsString());
        assertEquals("203000161", body.get("stop_id").getAsString());
        assertTrue(body.get("boarded").getAsBoolean());
        assertEquals(1.0, body.get("predicted_prob").getAsDouble(), 1e-9);

        DocumentSnapshot rec = Tasks.await(users.userDoc(user.getUid()).collection("feedback")
                .document(result.recordId).get(), 20, TimeUnit.SECONDS);
        assertEquals(Boolean.TRUE, rec.getBoolean("sent"));
        assertEquals("srv-1", rec.getString("server_id"));

        Result<List<FeedbackRecord>> history = new Result<>();
        feedbackRepo().loadHistory(20, history);
        List<FeedbackRecord> list = history.ok();
        assertEquals(1, list.size());
        assertTrue(list.get(0).summary().contains("탔어요"));
    }

    @Test
    public void feedbackKeptWhenServerDownAndRetriedLater() throws Exception {
        signUp(newEmail(), "재전송");
        server.enqueue(new MockResponse().setResponseCode(503));

        Result<FeedbackRepository.SubmitResult> r = new Result<>();
        feedbackRepo().submit(new FeedbackRequest(null, "2026-11-09", "8800", "203000063", false, 0.31, "만석"), r);
        assertFalse(r.ok().sentToServer);

        Result<List<FeedbackRecord>> before = new Result<>();
        feedbackRepo().loadHistory(20, before);
        assertFalse(before.ok().get(0).sent);
        assertTrue(before.value.get(0).summary().contains("전송 대기"));

        server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\": \"srv-2\"}"));
        Result<Integer> retried = new Result<>();
        feedbackRepo().retryPending(retried);
        assertEquals(Integer.valueOf(1), retried.ok());

        Result<List<FeedbackRecord>> after = new Result<>();
        feedbackRepo().loadHistory(20, after);
        assertTrue(after.ok().get(0).sent);
        assertFalse(after.value.get(0).boarded);
    }

    @Test
    public void feedbackNeedsLogin() throws Exception {
        Account.signOut();
        Result<FeedbackRepository.SubmitResult> r = new Result<>();
        feedbackRepo().submit(new FeedbackRequest(null, "2026-11-09", "8800", "203000063", true, null, ""), r);
        r.await();
        assertEquals("로그인이 필요합니다.", r.error);
        assertEquals(0, server.getRequestCount());
    }
}
