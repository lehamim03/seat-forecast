package kr.ac.kookmin.seatforecast.alternatives;

import android.content.Context;

import kr.ac.kookmin.seatforecast.alternatives.model.Option;

/**
 * 실시간 대안 화면이 다른 화면(D 로그인 · 마이페이지, C 지도)에 부탁하는 일.
 * alternatives 모듈은 다른 모듈에 직접 의존하지 않고, 팀 앱이 이 인터페이스를 구현해 {@link Alternatives#init} 으로 넘깁니다.
 * 연결 예시는 android-alternatives/README.md 참고.
 */
public interface AlternativesHost {

    /** 로그인했는지 (D: Account.isSignedIn()) */
    boolean isSignedIn();

    /** 로그인 화면 열기 (D: LoginActivity). 로그인 뒤 돌아오면 화면이 다시 불러옵니다. */
    void requestSignIn(Context context);

    /** 평소 정류장 · 노선 (D: UserRepository.loadSettings → homeStopId, myRoute) */
    void loadUsualTrip(Callback<UsualTrip> callback);

    /** 서버 요청용 Firebase ID 토큰, "Bearer " 는 붙이지 않음 (D: Account.getIdToken) */
    void getIdToken(Callback<String> callback);

    /** 대안 카드를 눌렀을 때 (C 지도에서 경로 보기, D 탑승 피드백 등). 기본은 아무것도 하지 않음. */
    default void onOptionClicked(Context context, Option option) {
    }
}
