package kr.ac.kookmin.seatforecast.app;

import android.content.Context;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.alternatives.ui.AlternativesActivity;
import kr.ac.kookmin.seatforecast.home.Callback;
import kr.ac.kookmin.seatforecast.home.HomeHost;
import kr.ac.kookmin.seatforecast.home.Place;
import kr.ac.kookmin.seatforecast.home.model.Option;

/** A 홈 → D 계정(즐겨찾기 · 도착지 · last_trip) · B 실시간 대안 · C 지도 · (E 출발 알림) 연결 */
class TeamHomeHost implements HomeHost {

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

    @Override
    public void loadFavorites(Callback<List<Place>> callback) {
        AccountBridge.loadFavorites(new AccountBridge.Result<List<kr.ac.kookmin.seatforecast.account.Place>>() {
            @Override
            public void ok(List<kr.ac.kookmin.seatforecast.account.Place> list) {
                List<Place> out = new ArrayList<>();
                for (kr.ac.kookmin.seatforecast.account.Place p : list) out.add(LastTrip.toHome(p));
                callback.onSuccess(out);
            }

            @Override
            public void fail(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void loadLastDestination(Callback<Place> callback) {
        AccountBridge.loadLastDestination(new AccountBridge.Result<kr.ac.kookmin.seatforecast.account.Place>() {
            @Override
            public void ok(kr.ac.kookmin.seatforecast.account.Place p) {
                callback.onSuccess(LastTrip.toHome(p));
            }

            @Override
            public void fail(String message) {
                callback.onError(message);
            }
        });
    }

    @Override
    public void addFavorite(Context context, Place place) {
        if (!Account.isSignedIn()) {
            AccountBridge.openLogin(context);
            return;
        }
        Context app = context.getApplicationContext();
        AccountBridge.addFavorite(LastTrip.toAccount(place), new AccountBridge.Result<Void>() {
            @Override
            public void ok(Void value) {
                Toast.makeText(app, app.getString(R.string.app_favorite_added, place.name), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void fail(String message) {
                Toast.makeText(app, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 검색할 때마다 — 대안 · 지도 탭이 같은 경로를 쓰게 기억하고, 로그인했으면 D last_trip 에도 저장 */
    @Override
    public void saveLastTrip(Place from, Place to) {
        LastTrip.set(from, to);
        AccountBridge.saveLastTrip(LastTrip.toAccount(from), LastTrip.toAccount(to));
    }

    /** 추천 카드 → B 실시간 대안 (그 출발지 · 도착지, extra 6개) */
    @Override
    public void onOptionClicked(Context context, Option option, Place from, Place to) {
        context.startActivity(AlternativesActivity.newIntent(context,
                from == null ? null : LastTrip.toAlt(from), to == null ? null : LastTrip.toAlt(to)));
    }

    /** 출발 알림 → E (E 는 서버 쪽 FCM 전송만 있고 안드로이드 모듈이 없음) */
    @Override
    public void onDepartureAlarmClicked(Context context, Option first) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.app_alarm_title)
                .setMessage(context.getString(R.string.app_alarm_missing,
                        first.route, first.stop == null ? "" : first.stop.name))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }
}
