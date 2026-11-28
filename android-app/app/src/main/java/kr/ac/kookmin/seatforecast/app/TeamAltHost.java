package kr.ac.kookmin.seatforecast.app;

import android.content.Context;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.ui.FeedbackActivity;
import kr.ac.kookmin.seatforecast.alternatives.AlternativesHost;
import kr.ac.kookmin.seatforecast.alternatives.Callback;
import kr.ac.kookmin.seatforecast.alternatives.Place;
import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/** B 실시간 대안 → D 계정 · 마지막 도착지 · 탑승 피드백 연결 */
class TeamAltHost implements AlternativesHost {

    @Override
    public boolean isSignedIn() {
        return Account.isSignedIn();
    }

    @Override
    public void requestSignIn(Context context) {
        AccountBridge.openLogin(context);
    }

    @Override
    public void getIdToken(Callback<String> callback) {
        AccountBridge.idToken(new AccountBridge.Result<String>() {
            @Override
            public void ok(String token) {
                callback.onSuccess(token);
            }

            @Override
            public void fail(String message) {
                callback.onError(message);
            }
        });
    }

    /** 이번 실행에서 홈이 검색한 도착지가 먼저, 없으면 D destination · last_trip.to */
    @Override
    public void loadLastDestination(Callback<Place> callback) {
        Place mem = LastTrip.altTo();
        if (mem != null) {
            callback.onSuccess(mem);
            return;
        }
        AccountBridge.loadLastDestination(new AccountBridge.Result<kr.ac.kookmin.seatforecast.account.Place>() {
            @Override
            public void ok(kr.ac.kookmin.seatforecast.account.Place p) {
                callback.onSuccess(LastTrip.toAlt(p));
            }

            @Override
            public void fail(String message) {
                callback.onError(message);
            }
        });
    }

    /**
     * 대안 카드 → D 탑승 피드백 (그 버스를 탔는지) — 어떤 노선 · 정류장이든.
     * 확률은 좌석제 · 좌석 수를 아는 카드만 (FeedbackProb), 아니면 null.
     */
    @Override
    public void onOptionClicked(Context context, Option option, Place from, Place to) {
        open(context, option);
    }

    /** 출발지 · 도착지 없이 열린 대안 화면 (재생 등) 의 카드도 같은 피드백 */
    @Override
    public void onOptionClicked(Context context, Option option) {
        open(context, option);
    }

    private static void open(Context context, Option option) {
        if (option.stop == null || option.stop.id == null || option.route == null) return;
        context.startActivity(FeedbackActivity.newIntent(context, option.route, option.stop.id,
                option.stop.name, FeedbackProb.of(option)));
    }
}
