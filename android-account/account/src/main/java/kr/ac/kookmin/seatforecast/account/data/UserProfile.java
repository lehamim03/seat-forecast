package kr.ac.kookmin.seatforecast.account.data;

import com.google.firebase.Timestamp;

/** 회원 정보 (기획서: 이메일, 닉네임, 가입일) — users/{uid} 문서의 일부 */
public class UserProfile {
    public static final String UID = "uid";
    public static final String EMAIL = "email";
    public static final String NICKNAME = "nickname";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    public String uid;
    public String email;
    public String nickname;
    public Timestamp createdAt;
}
