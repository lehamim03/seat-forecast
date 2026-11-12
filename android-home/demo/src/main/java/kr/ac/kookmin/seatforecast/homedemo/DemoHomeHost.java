package kr.ac.kookmin.seatforecast.homedemo;

import android.content.Context;
import android.widget.Toast;

import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.UsualTrip;
import kr.ac.kookmin.seatforecast.home.model.Option;

/**
 * 가짜 값으로 채운 HomeHost — 항상 로그인 상태, 평소 정류장 203000063(삼성1차아파트) · 노선 8800.
 * 팀 앱에서는 D 의 Account · UserRepository 로 채웁니다 (android-home/README.md).
 */
class DemoHomeHost implements HomeHost {

    static final String HOME_STOP_ID = "203000063";
    static final String ROUTE = "8800";

    @Override
    public boolean isSignedIn() {
        return true;
    }

    @Override
    public void requestSignIn(Context context) {
        Toast.makeText(context, "로그인 화면 (D 담당)", Toast.LENGTH_SHORT).show();
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
        Toast.makeText(context, option.route + " · " + option.stop.name + " — 실시간 대안 (B 담당)",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onDepartureAlarmClicked(Context context, Option first) {
        Toast.makeText(context, "출발 알림: " + first.route + " · " + first.stop.name + " (E 담당)",
                Toast.LENGTH_SHORT).show();
    }
}
