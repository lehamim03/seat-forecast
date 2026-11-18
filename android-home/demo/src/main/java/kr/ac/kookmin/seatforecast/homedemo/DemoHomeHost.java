package kr.ac.kookmin.seatforecast.homedemo;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.UsualTrip;
import kr.ac.kookmin.seatforecast.home.model.Bus;
import kr.ac.kookmin.seatforecast.home.model.Option;
import kr.ac.kookmin.seatforecast.home.model.RouteChoice;

/**
 * 가짜 값으로 채운 HomeHost — 로그인 상태(demo_mode=signed_out 이면 로그아웃), 평소 정류장 203000063(삼성1차아파트) · 노선 8800.
 * 팀 앱에서는 D 의 Account · UserRepository 로 채웁니다 (android-home/README.md).
 * 버튼 · 카드를 누르면 다른 담당(B · D · E) 화면이 열릴 자리라, 시연 앱에서는 눌린 내용을 대화상자로 보여 줍니다.
 */
class DemoHomeHost implements HomeHost {

    static final String HOME_STOP_ID = "203000063";
    static final String ROUTE = "8800";

    boolean signedIn = true;

    @Override
    public boolean isSignedIn() {
        return signedIn;
    }

    @Override
    public void requestSignIn(Context context) {
        show(context, "로그인", "실제 앱에서는 D 담당의 로그인 화면이 열립니다.");
    }

    @Override
    public void loadUsualTrip(Callback<UsualTrip> callback) {
        callback.onSuccess(new UsualTrip(HOME_STOP_ID, ROUTE));
    }

    @Override
    public void getIdToken(Callback<String> callback) {
        callback.onSuccess("demo-id-token");
    }

    @Override
    public void onOptionClicked(Context context, Option option) {
        show(context, option.route + "번 · " + (option.stop == null ? "" : option.stop.name), detail(option)
                + "\n\n실제 앱에서는 B 담당의 실시간 대안 화면이 열립니다.");
    }

    @Override
    public void onDepartureAlarmClicked(Context context, Option first) {
        show(context, "출발 알림", "1순위 " + first.route + "번 · " + (first.stop == null ? "" : first.stop.name)
                + " 기준으로 출발 시각을 알려 줍니다.\n\n실제 앱에서는 E 담당의 출발 알림이 설정됩니다.");
    }

    @Override
    public void onTripClicked(Context context) {
        show(context, "평소 정류장 · 노선", "삼성1차아파트 · " + ROUTE + "번 (시연용 고정 값)"
                + "\n\n실제 앱에서는 D 담당의 마이페이지 설정 화면이 열립니다.");
    }

    private static void show(Context context, String title, String message) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    /** 카드 내용 요약: 도착 · 탑승 확률 · 다가오는 차 · 서울에서 국민대까지 경로 */
    private static String detail(Option o) {
        StringBuilder sb = new StringBuilder();
        sb.append(o.skipped ? "우회 중이라 탈 수 없음" : "국민대 도착 " + o.arriveAt + " (" + o.totalMin + "분)")
                .append("\n도보 ").append(o.walkMin).append("분 · 탑승 확률 ")
                .append(Math.round(o.boardProb * 100)).append("%");
        if (o.buses != null && !o.buses.isEmpty()) {
            sb.append("\n\n다가오는 차");
            for (Bus b : o.buses) {
                sb.append("\n· ").append(b.etaMin).append("분 뒤 · ")
                        .append(b.seatsAtStop == null ? "좌석 정보 없음" : "약 " + b.seatsAtStop + "석")
                        .append(" · ").append(Math.round(b.boardProb * 100)).append("%")
                        .append(b.reachable ? "" : " (걸어가는 동안 지나감)");
            }
        }
        if (o.routeChoices != null && !o.routeChoices.isEmpty()) {
            sb.append("\n\n").append(o.alightStop).append(" 하차 후 국민대까지");
            for (RouteChoice ch : o.routeChoices) {
                sb.append("\n· ").append(ch.arriveAt).append(" 도착 · 환승 ").append(ch.transfers).append("회")
                        .append(ch.tags == null || ch.tags.isEmpty() ? "" : " [" + String.join("·", ch.tags) + "]");
            }
        }
        return sb.toString();
    }
}
