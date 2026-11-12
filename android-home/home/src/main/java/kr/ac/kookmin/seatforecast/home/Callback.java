package kr.ac.kookmin.seatforecast.home;

/** 비동기 작업 결과. D 모듈의 account.Callback 과 같은 모양이며, 항상 메인 스레드에서 불러 주세요. */
public interface Callback<T> {
    void onSuccess(T result);

    /** message 는 화면에 그대로 보여 줄 수 있는 한국어 문장입니다. */
    void onError(String message);
}
