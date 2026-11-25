package kr.ac.kookmin.seatforecast.home.data;

import androidx.annotation.Nullable;

/** 추천 데이터를 가져오지 못했을 때. {@link #getMessage()} 는 화면에 그대로 보여 줄 수 있는 한국어 문장입니다. */
public class SourceException extends Exception {

    /** 13주차: 서버 응답 없이 실패한 경우의 {@link #code} — 시간 초과 */
    public static final String CODE_TIMEOUT = "timeout";
    /** 13주차: 서버 응답 없이 실패한 경우의 {@link #code} — 연결 안 됨 (인터넷 꺼짐 · 서버 꺼짐 · 주소 틀림) */
    public static final String CODE_OFFLINE = "offline";

    /** 서버 오류 코드 ({@code {"error": {"code": ...}}}) 또는 {@link #CODE_TIMEOUT} · {@link #CODE_OFFLINE}, 없으면 null */
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

    /** 서버가 401 — ID 토큰이 만료 · 무효. 홈 화면은 토큰을 새로 받아 한 번 더 요청합니다. */
    public boolean isUnauthorized() {
        return httpStatus == 401;
    }
}
