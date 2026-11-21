package kr.ac.kookmin.seatforecast.alternativesdemo;

import android.content.Context;
import android.widget.Toast;

import kr.ac.kookmin.seatforecast.alternatives.AlternativesHost;
import kr.ac.kookmin.seatforecast.alternatives.Callback;
import kr.ac.kookmin.seatforecast.alternatives.UsualTrip;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 가짜 값으로 채운 AlternativesHost — 로그인 상태(demo_mode=signed_out 이면 로그아웃), 평소 정류장 203000063(삼성1차아파트) · 노선 8800.
 * 팀 앱에서는 D 의 Account · UserRepository 로 채웁니다 (android-alternatives/README.md).
 */
class DemoAltHost implements AlternativesHost {

    static final String HOME_STOP_ID = "203000063";
    static final String ROUTE = "8800";

    boolean signedIn = true;

    @Override
    public boolean isSignedIn() {
        return signedIn;
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
        Toast.makeText(context, option.kind + " · " + option.route + " — 지도에서 경로 보기 (C 담당)",
                Toast.LENGTH_SHORT).show();
    }
}
