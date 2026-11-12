package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.Nullable;

/** 추천 데이터를 가져오지 못했을 때. {@link #getMessage()} 는 화면에 그대로 보여 줄 수 있는 한국어 문장입니다. */
public class SourceException extends Exception {

    /** 서버 오류 코드 ({@code {"error": {"code": ...}}}), 없으면 null */
    @Nullable
    public final String code;

    /** HTTP 상태 코드, 서버 응답이 아니면 0 */
    public final int httpStatus;

    public SourceException(String message, @Nullable String code, int httpStatus, @Nullable Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public SourceException(String message, @Nullable Throwable cause) {
        this(message, null, 0, cause);
    }
}
