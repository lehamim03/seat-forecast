package kr.ac.kookmin.seatforecast.alternatives;

import android.content.Context;

import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 실시간 대안 화면이 다른 화면(D 로그인 · 마이페이지 · 탑승 피드백, C 지도)에 부탁하는 일.
 * alternatives 모듈은 다른 모듈에 직접 의존하지 않고, 팀 앱이 이 인터페이스를 구현해 {@link Alternatives#init} 으로 넘깁니다.
 * 이름은 A 홈의 HomeHost 와 같게 맞췄습니다 (팀 앱 · D 가 같은 구현을 쓸 수 있게).
 */
public interface AlternativesHost {

    /** 로그인했는지 (D: Account.isSignedIn()) */
    boolean isSignedIn();

    /** 로그인 화면 열기 (D: LoginActivity). 로그인 뒤 돌아오면 화면이 다시 불러옵니다. */
    void requestSignIn(Context context);

    /** 서버 요청용 Firebase ID 토큰, "Bearer " 는 붙이지 않음 (D: Account.getIdToken). 없으면 null 로 성공. */
    void getIdToken(Callback<String> callback);

    /**
     * extra 로 도착지를 받지 못했을 때 쓸 마지막 도착지 (D: destination, 없으면 last_trip.to). 없으면 null 로 성공
     * → "홈에서 도착지를 정해 주세요".
     */
    default void loadLastDestination(Callback<Place> callback) {
        callback.onSuccess(null);
    }

    /**
     * 대안 카드를 눌렀을 때 (D 탑승 피드백, C 지도에서 경로 보기 등) — 그 출발지 · 도착지와 함께.
     * 기본은 예전 {@link #onOptionClicked(Context, Option)} 을 부릅니다.
     */
    default void onOptionClicked(Context context, Option option, Place from, Place to) {
        onOptionClicked(context, option);
    }

    /** 예전 방식 (출발지 · 도착지 없이). 기본은 아무것도 하지 않음. */
    default void onOptionClicked(Context context, Option option) {
    }

    /** 예전 평소 정류장 · 노선 — 출발지 · 도착지 모드로 바뀌어 더 이상 부르지 않음 (컴파일 호환) */
    @Deprecated
    default void loadUsualTrip(Callback<UsualTrip> callback) {
        callback.onError("출발지 · 도착지 모드에서는 쓰지 않습니다.");
    }
}
