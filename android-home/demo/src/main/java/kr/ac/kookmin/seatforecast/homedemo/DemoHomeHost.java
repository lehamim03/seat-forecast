package kr.ac.kookmin.seatforecast.homedemo;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.TripExtras;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;

/**
 * 가짜 값으로 채운 HomeHost — 로그인 상태(demo_mode=signed_out 이면 로그아웃) · 즐겨찾는 곳 2곳 (시연용 가짜 값).
 * 팀 앱에서는 D 의 Account · UserRepository(favorites · destination · last_trip)로 채웁니다 (android-home/README.md).
 * 카드 · 버튼을 누르면 다른 담당(B · D · E) 화면이 열릴 자리라, 시연 앱에서는 넘길 값을 대화상자로 보여 줍니다.
 */
class DemoHomeHost implements HomeHost {

    /** 시연용 즐겨찾는 곳 (에뮬레이터 위치 시험 좌표와 같은 곳) */
    static final Place HOME = new Place("집", "수원 영통구 매탄동 삼성1차아파트", 37.2726, 127.0436);
    static final Place SCHOOL = new Place("학교", "서울 성북구 정릉동 국민대학교", 37.610977, 126.995185);

    boolean signedIn = true;
    private final List<Place> favorites = new ArrayList<>();

    DemoHomeHost() {
        favorites.add(HOME);
        favorites.add(SCHOOL);
    }

    @Override
    public boolean isSignedIn() {
        return signedIn;
    }

    @Override
    public void requestSignIn(Context context) {
        show(context, "로그인", "실제 앱에서는 D 담당의 로그인 화면이 열립니다.");
    }

    /** 시연용 가짜 토큰 — 서버의 로그인 확인이 꺼져 있으면 그대로 통과 */
    static final String TOKEN = "demo-id-token";
    /** 13주차: 401 뒤 새로 받은 토큰 */
    static final String REFRESHED_TOKEN = "demo-id-token-refreshed";

    @Override
    public void getIdToken(Callback<String> callback) {
        callback.onSuccess(TOKEN);
    }

    @Override
    public void getIdToken(boolean forceRefresh, Callback<String> callback) {
        callback.onSuccess(forceRefresh ? REFRESHED_TOKEN : TOKEN);
    }

    @Override
    public void loadFavorites(Callback<List<Place>> callback) {
        callback.onSuccess(new ArrayList<>(favorites));
    }

    /** 처음 열 때 도착지 — 시연 앱은 '학교' (실제 앱은 D 의 destination · last_trip.to) */
    @Override
    public void loadLastDestination(Callback<Place> callback) {
        callback.onSuccess(SCHOOL);
    }

    @Override
    public void addFavorite(Context context, Place place) {
        for (Place p : favorites) {
            if (p.key().equals(place.key())) {
                Toast.makeText(context, place.name + " 은(는) 이미 즐겨찾는 곳이에요", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        favorites.add(place);
        Toast.makeText(context, place.name + " 즐겨찾기에 추가 (실제 앱: D favorites)", Toast.LENGTH_SHORT).show();
    }

    /** 검색할 때마다 — 실제 앱은 D 가 Firestore last_trip 에 저장 (시연 앱은 마지막 값만 기억) */
    Place lastFrom;
    Place lastTo;

    @Override
    public void saveLastTrip(Place from, Place to) {
        lastFrom = from;
        lastTo = to;
    }

    @Override
    public void onOptionClicked(Context context, Option option, Place from, Place to) {
        show(context, option.route + " · " + (option.stop == null ? "" : option.stop.name), detail(option)
                + "\n\nB 실시간 대안 화면에 넘길 extra (TripExtras)\n"
                + String.format(Locale.US, "%s=%.5f  %s=%.5f  %s=%s\n%s=%.5f  %s=%.5f  %s=%s",
                TripExtras.FROM_LAT, from.lat, TripExtras.FROM_LNG, from.lng, TripExtras.FROM_NAME, from.name,
                TripExtras.TO_LAT, to.lat, TripExtras.TO_LNG, to.lng, TripExtras.TO_NAME, to.name));
    }

    @Override
    public void onDepartureAlarmClicked(Context context, Option first) {
        show(context, "출발 알림", "1순위 " + first.route + " · " + (first.stop == null ? "" : first.stop.name)
                + " 기준으로 출발 시각을 알려 줍니다.\n\n실제 앱에서는 E 담당의 출발 알림이 설정됩니다.");
    }

    private static void show(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** 카드 내용 요약: 도착 · 탑승 확률 · 경로 · 다가오는 차 */
    private static String detail(Option o) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.skipped ? "우회 중이라 탈 수 없음" : "도착 " + o.arriveAt + " (" + o.totalMin + "분)")
                .append("\n").append(o.kind).append(" · 도보 ").append(o.walkMin).append("분 · ")
                .append(o.isStandingOk() ? "입석 가능" : o.isSeatUnknown() ? "좌석 정보 없음"
                        : "탑승 확률 " + Math.round(o.boardProb * 100) + "%");
        if (o.summary != null) sb.append("\n").append(o.summary);
        if (o.buses != null && !o.buses.isEmpty()) {
            sb.append("\n\n다가오는 차");
            for (Bus b : o.buses) {
                sb.append("\n· ").append(b.etaMin).append("분 뒤 · ")
                        .append(b.seatsAtStop == null ? "좌석 정보 없음" : "약 " + b.seatsAtStop + "석")
                        .append(b.reachable ? "" : " (걸어가는 동안 지나감)");
            }
        }
        return sb.toString();
    }
}
