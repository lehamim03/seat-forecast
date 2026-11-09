package kr.ac.kookmin.seatforecast.account;

/** 비동기 작업 결과. 항상 메인 스레드에서 불립니다. */
public interface Callback<T> {
    void onSuccess(T result);

    /** message 는 화면에 그대로 보여줄 수 있는 한국어 문장입니다. */
    void onError(String message);
}
