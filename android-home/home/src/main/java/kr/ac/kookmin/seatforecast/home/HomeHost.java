package kr.ac.kookmin.seatforecast.home;

import android.content.Context;

import java.util.Collections;
import java.util.List;

import kr.ac.kookmin.seatforecast.home.model.Option;

/**
 * 홈 화면이 다른 화면(D 로그인 · 마이페이지, B 실시간 대안, E 출발 알림)에 부탁하는 일.
 * home 모듈은 다른 모듈에 직접 의존하지 않고, 팀 앱이 이 인터페이스를 구현해 {@link Home#init} 으로 넘깁니다.
 * 연결 예시는 android-home/README.md 참고.
 *
 * <p>12월 출발지 · 도착지 모드: 즐겨찾는 곳 · 최근 도착지 · last_trip 저장 · 즐겨찾기 추가는 기본 구현이 있어
 * (빈 목록 · 아무것도 안 함) 예전 구현도 그대로 컴파일됩니다.
 */
public interface HomeHost {

    /** 로그인했는지 (D: Account.isSignedIn()) */
    boolean isSignedIn();

    /** 로그인 화면 열기 (D: LoginActivity). 로그인 뒤 홈으로 돌아오면 화면이 다시 불러옵니다. */
    void requestSignIn(Context context);

    /** 서버 요청용 Firebase ID 토큰, "Bearer " 는 붙이지 않음 (D: Account.getIdToken). 없으면 null 로 성공. */
    void getIdToken(Callback<String> callback);

    /**
     * 13주차: 서버가 401(토큰 만료 · 무효)을 돌려주면 홈 화면이 {@code forceRefresh = true} 로 한 번 더 부르고 다시 요청합니다.
     * Firebase 라면 {@code user.getIdToken(forceRefresh)}. 기본은 {@link #getIdToken(Callback)} 그대로라
     * 구현하지 않아도 기존 코드가 컴파일됩니다 (그 경우 재시도도 같은 토큰).
     */
    default void getIdToken(boolean forceRefresh, Callback<String> callback) {
        getIdToken(callback);
    }

    // ── 출발지 · 도착지 (D: Firestore users/{uid}) ──

    /** 즐겨찾는 곳 (D: {@code favorites [{name, lat, lng}]}) — 검색 화면 · 즐겨찾기 버튼에 보여 줌 */
    default void loadFavorites(Callback<List<Place>> callback) {
        callback.onSuccess(Collections.emptyList());
    }

    /**
     * 처음 열 때 쓸 도착지 (D: {@code destination}, 없으면 {@code last_trip.to}). 없으면 null 로 성공
     * → 홈 화면이 "도착지를 검색해 주세요" 를 보여 줍니다. 이 기기에서 마지막으로 고른 도착지가 있으면 그쪽이 먼저입니다.
     */
    default void loadLastDestination(Callback<Place> callback) {
        callback.onSuccess(null);
    }

    /** 즐겨찾기 버튼 → "이 도착지를 즐겨찾기에 추가" (D: favorites 에 추가) */
    default void addFavorite(Context context, Place place) {
    }

    /**
     * 출발지 · 도착지로 추천을 검색할 때마다 (자동 새로고침 제외) — D: {@code last_trip {from, to, at}} 저장
     * (E 돌발 알림 대상). 출발지가 "현재 위치" 면 그때의 GPS 좌표가 들어 있습니다.
     */
    default void saveLastTrip(Place from, Place to) {
    }

    // ── 다른 화면 열기 ──

    /**
     * 추천 카드를 눌렀을 때 — B 실시간 대안을 그 출발지 · 도착지로 엽니다:
     * {@code TripExtras.put(AlternativesActivity.newIntent(...), from, to)} (extra 6개).
     * 기본은 예전 {@link #onOptionClicked(Context, Option)} 을 부릅니다.
     */
    default void onOptionClicked(Context context, Option option, Place from, Place to) {
        onOptionClicked(context, option);
    }

    /** 예전 방식 (출발지 · 도착지 없이). 새 코드는 {@link #onOptionClicked(Context, Option, Place, Place)} 를 구현하세요. */
    default void onOptionClicked(Context context, Option option) {
    }

    /** 출발 알림 버튼 — 1순위 선택지 기준 (E 출발 알림) */
    void onDepartureAlarmClicked(Context context, Option first);

    /** 예전 '우리집 → 국민대' 카드 — 출발지 · 도착지 입력으로 바뀌어 더 이상 부르지 않음 */
    @Deprecated
    default void onTripClicked(Context context) {
    }

    /** 예전 평소 정류장 · 노선 — 출발지 · 도착지 모드로 바뀌어 더 이상 부르지 않음 (D 가 바꿀 때까지 컴파일 호환) */
    @Deprecated
    default void loadUsualTrip(Callback<UsualTrip> callback) {
        callback.onError("출발지 · 도착지 모드에서는 쓰지 않습니다.");
    }
}
