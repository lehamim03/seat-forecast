package kr.ac.kookmin.seatforecast.account.feedback;

import androidx.annotation.NonNull;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Callback;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import retrofit2.Call;
import retrofit2.Response;

/**
 * 탑승 피드백 보내기.
 * 1) Firestore users/{uid}/feedback 에 먼저 기록 (마이페이지 기록 화면 · 서버가 꺼져 있어도 남도록)
 * 2) POST /api/v1/feedback 으로 서버에 전송 → 성공하면 기록에 sent=true
 * 서버 전송에 실패한 기록은 retryPending() 으로 다시 보냅니다.
 */
public class FeedbackRepository {

    public static final String SUBCOLLECTION = "feedback";
    static final String SENT = "sent";
    static final String SERVER_ID = "server_id";
    /** 서버가 요청 자체를 거절함 (다시 보내지 않음) — 13주차 */
    static final String REJECTED = "rejected";
    static final String REJECT_STATUS = "reject_status";
    static final String CREATED_AT = "created_at";

    /** 서버 전송 결과까지 포함한 제출 결과 */
    public static class SubmitResult {
        public final String recordId;
        public final boolean sentToServer;

        SubmitResult(String recordId, boolean sentToServer) {
            this.recordId = recordId;
            this.sentToServer = sentToServer;
        }
    }

    private final FirebaseFirestore db;
    private final FeedbackApi api;

    /** 둘러보기(Account.isPreview) 중 기록 — Firestore 대신 앱 메모리에만 둡니다 (최신순). */
    private static List<FeedbackRecord> previewRecords;

    public FeedbackRepository() {
        this(FirebaseFirestore.getInstance(), FeedbackApi.create(Account.getServerBaseUrl()));
    }

    public FeedbackRepository(FirebaseFirestore db, FeedbackApi api) {
        this.db = db;
        this.api = api;
    }

    private CollectionReference records(String uid) {
        return db.collection(UserRepository.COLLECTION).document(uid).collection(SUBCOLLECTION);
    }

    public void submit(@NonNull FeedbackRequest request, Callback<SubmitResult> callback) {
        String uid = Account.currentUid();
        if (uid == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }
        request.uid = uid;
        String problem = request.validate();
        if (problem != null) {
            callback.onError(problem);
            return;
        }
        if (Account.isPreview()) {
            FeedbackRecord r = record("preview-" + System.currentTimeMillis(), request.date, request.route,
                    request.stopId, request.boarded, request.predictedProb);
            r.memo = request.memo;
            previewRecords().add(0, r);
            callback.onSuccess(new SubmitResult(r.id, true));
            return;
        }
        Map<String, Object> data = request.toMap();
        data.put(SENT, false);
        data.put(CREATED_AT, FieldValue.serverTimestamp());
        DocumentReference doc = records(uid).document();
        doc.set(data)
                .addOnSuccessListener(v -> sendToServer(doc, request,
                        sent -> callback.onSuccess(new SubmitResult(doc.getId(), sent))))
                .addOnFailureListener(e -> callback.onError("피드백을 저장하지 못했습니다."));
    }

    /** 최근 기록 (최신순) */
    public void loadHistory(int limit, Callback<List<FeedbackRecord>> callback) {
        String uid = Account.currentUid();
        if (uid == null) {
            callback.onError("로그인이 필요합니다.");
            return;
        }
        if (Account.isPreview()) {
            List<FeedbackRecord> all = previewRecords();
            callback.onSuccess(new ArrayList<>(all.subList(0, Math.min(limit, all.size()))));
            return;
        }
        records(uid).orderBy(CREATED_AT, Query.Direction.DESCENDING).limit(limit).get()
                .addOnSuccessListener(snap -> {
                    List<FeedbackRecord> out = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) out.add(FeedbackRecord.from(d));
                    callback.onSuccess(out);
                })
                .addOnFailureListener(e -> callback.onError("피드백 기록을 불러오지 못했습니다."));
    }

    /** 서버에 못 보낸 기록을 다시 보냅니다. 결과는 새로 보낸 개수. */
    public void retryPending(Callback<Integer> callback) {
        String uid = Account.currentUid();
        if (uid == null || Account.isPreview()) {
            callback.onSuccess(0);
            return;
        }
        records(uid).whereEqualTo(SENT, false).get()
                .addOnSuccessListener(snap -> {
                    // 서버가 요청 자체를 거절한 기록은 다시 보내도 같으므로 빼고 보냄
                    List<DocumentSnapshot> docs = new ArrayList<>();
                    for (DocumentSnapshot d : snap.getDocuments()) {
                        if (!Boolean.TRUE.equals(d.getBoolean(REJECTED))) docs.add(d);
                    }
                    if (docs.isEmpty()) {
                        callback.onSuccess(0);
                        return;
                    }
                    int[] done = {0, 0};   // 끝난 수, 성공한 수
                    for (DocumentSnapshot d : docs) {
                        sendToServer(d.getReference(), FeedbackRecord.from(d).toRequest(uid), sent -> {
                            done[0]++;
                            if (sent) done[1]++;
                            if (done[0] == docs.size()) callback.onSuccess(done[1]);
                        });
                    }
                })
                .addOnFailureListener(e -> callback.onError("피드백 기록을 불러오지 못했습니다."));
    }

    /** 둘러보기 첫 화면이 비어 보이지 않도록 지난 며칠 예시 기록을 넣어 둡니다. */
    private static List<FeedbackRecord> previewRecords() {
        if (previewRecords == null) {
            previewRecords = new ArrayList<>();
            previewRecords.add(record("preview-3", daysAgo(1), "3007", "203000161", true, 0.95));
            previewRecords.add(record("preview-2", daysAgo(2), "8800", "203000063", false, 0.42));
            previewRecords.add(record("preview-1", daysAgo(3), "3008", "203000162", true, 0.81));
        }
        return previewRecords;
    }

    private static FeedbackRecord record(String id, String date, String route, String stopId,
                                         boolean boarded, Double prob) {
        FeedbackRecord r = new FeedbackRecord();
        r.id = id;
        r.date = date;
        r.route = route;
        r.stopId = stopId;
        r.boarded = boarded;
        r.predictedProb = prob;
        r.memo = "";
        r.sent = true;
        return r;
    }

    private static String daysAgo(int days) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"), Locale.KOREA);
        c.add(Calendar.DAY_OF_MONTH, -days);
        return String.format(Locale.US, "%04d-%02d-%02d",
                c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private interface SentListener {
        void onDone(boolean sent);
    }

    private void sendToServer(DocumentReference doc, FeedbackRequest request, SentListener listener) {
        Account.getIdToken(new Callback<String>() {
            @Override
            public void onSuccess(String token) {
                api.send("Bearer " + token, request).enqueue(new retrofit2.Callback<FeedbackResponse>() {
                    @Override
                    public void onResponse(@NonNull Call<FeedbackResponse> call,
                                           @NonNull Response<FeedbackResponse> response) {
                        if (!response.isSuccessful()) {
                            if (FeedbackResponse.isPermanentRejection(response.code())) {
                                Map<String, Object> rejected = new HashMap<>();
                                rejected.put(REJECTED, true);
                                rejected.put(REJECT_STATUS, response.code());
                                doc.update(rejected)
                                        .addOnSuccessListener(v -> listener.onDone(false))
                                        .addOnFailureListener(e -> listener.onDone(false));
                            } else {
                                listener.onDone(false);
                            }
                            return;
                        }
                        Map<String, Object> update = new HashMap<>();
                        update.put(SENT, true);
                        FeedbackResponse body = response.body();
                        if (body != null && body.id != null) update.put(SERVER_ID, body.id);
                        doc.update(update)
                                .addOnSuccessListener(v -> listener.onDone(true))
                                .addOnFailureListener(e -> listener.onDone(true));
                    }

                    @Override
                    public void onFailure(@NonNull Call<FeedbackResponse> call, @NonNull Throwable t) {
                        listener.onDone(false);
                    }
                });
            }

            @Override
            public void onError(String message) {
                listener.onDone(false);
            }
        });
    }
}
