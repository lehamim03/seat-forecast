package kr.ac.kookmin.seatforecast.app;

import android.content.Context;
import android.content.Intent;

import java.util.Collections;
import java.util.List;

import kr.ac.kookmin.seatforecast.account.Account;
import kr.ac.kookmin.seatforecast.account.Place;
import kr.ac.kookmin.seatforecast.account.data.UserRepository;
import kr.ac.kookmin.seatforecast.account.ui.LoginActivity;
import kr.ac.kookmin.seatforecast.account.ui.MyPageActivity;

/** A · B 의 Host 가 함께 쓰는 D(계정) 연결 — 모듈마다 Callback · Place 클래스가 달라 결과만 넘김. */
final class AccountBridge {

    interface Result<T> {
        void ok(T value);

        void fail(String message);
    }

    private AccountBridge() {
    }

    static void openLogin(Context c) {
        c.startActivity(new Intent(c, LoginActivity.class));
    }

    /** 로그인했으면 마이페이지, 아니면 로그인 화면 */
    static void openMyPage(Context c) {
        if (Account.isSignedIn()) c.startActivity(new Intent(c, MyPageActivity.class));
        else openLogin(c);
    }

    /** 즐겨찾는 곳 — 로그인 전이면 빈 목록 */
    static void loadFavorites(Result<List<Place>> r) {
        String uid = Account.currentUid();
        if (uid == null) {
            r.ok(Collections.emptyList());
            return;
        }
        new UserRepository().loadFavorites(uid, wrap(r));
    }

    /** destination, 없으면 last_trip.to — 로그인 전이면 null */
    static void loadLastDestination(Result<Place> r) {
        String uid = Account.currentUid();
        if (uid == null) {
            r.ok(null);
            return;
        }
        new UserRepository().loadLastDestination(uid, wrap(r));
    }

    static void addFavorite(Place place, Result<Void> r) {
        String uid = Account.currentUid();
        if (uid == null) {
            r.fail("로그인이 필요합니다.");
            return;
        }
        new UserRepository().addFavorite(uid, place, wrap(r));
    }

    /** last_trip 저장 (E 돌발 알림 대상) — 로그인 전이면 아무것도 안 함 */
    static void saveLastTrip(Place from, Place to) {
        String uid = Account.currentUid();
        if (uid == null) return;
        new UserRepository().saveLastTrip(uid, from, to, wrap(new Result<Void>() {
            @Override
            public void ok(Void value) {
            }

            @Override
            public void fail(String message) {
                android.util.Log.w("TeamApp", "last_trip: " + message);
            }
        }));
    }

    /** 서버 요청 토큰. 둘러보기 중에는 토큰이 없으므로 null */
    static void idToken(Result<String> r) {
        if (Account.isPreview()) {
            r.ok(null);
            return;
        }
        Account.getIdToken(wrap(r));
    }

    private static <T> kr.ac.kookmin.seatforecast.account.Callback<T> wrap(Result<T> r) {
        return new kr.ac.kookmin.seatforecast.account.Callback<T>() {
            @Override
            public void onSuccess(T value) {
                r.ok(value);
            }

            @Override
            public void onError(String message) {
                r.fail(message);
            }
        };
    }
}
