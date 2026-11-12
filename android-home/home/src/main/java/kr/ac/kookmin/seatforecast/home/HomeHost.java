package kr.ac.kookmin.seatforecast.home;

import android.content.Context;

import kr.ac.kookmin.seatforecast.home.model.Option;

/**
 * 홈 화면이 다른 화면(D 로그인 · 마이페이지, B 실시간 대안, E 출발 알림)에 부탁하는 일.
 * home 모듈은 다른 모듈에 직접 의존하지 않고, 팀 앱이 이 인터페이스를 구현해 {@link Home#init} 으로 넘깁니다.
 * 연결 예시는 android-home/README.md 참고.
 */
public interface HomeHost {

    /** 로그인했는지 (D: Account.isSignedIn()) */
    boolean isSignedIn();

    /** 로그인 화면 열기 (D: LoginActivity). 로그인 뒤 홈으로 돌아오면 화면이 다시 불러옵니다. */
    void requestSignIn(Context context);

    /** 평소 정류장 · 노선 (D: UserRepository.loadSettings → homeStopId, myRoute) */
    void loadUsualTrip(Callback<UsualTrip> callback);

    /** 서버 요청용 Firebase ID 토큰, "Bearer " 는 붙이지 않음 (D: Account.getIdToken) */
    void getIdToken(Callback<String> callback);

    /** 추천 목록 항목을 눌렀을 때 (B 실시간 대안 또는 D 탑승 피드백 FeedbackActivity.newIntent) */
    void onOptionClicked(Context context, Option option);

    /** 출발 알림 버튼 — 1순위 선택지 기준 (E 출발 알림) */
    void onDepartureAlarmClicked(Context context, Option first);
}
