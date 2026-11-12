package kr.ac.kookmin.seatforecast.account.data;

import java.util.regex.Pattern;

/** 입력 화면 검사. 문제가 없으면 null, 있으면 화면에 보여줄 문장을 돌려줍니다. */
public final class Validators {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern HH_MM = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    /** Firebase Auth 비밀번호 최소 길이 */
    public static final int MIN_PASSWORD = 6;
    public static final int MAX_NICKNAME = 20;

    private Validators() {
    }

    public static String email(String email) {
        if (email == null || email.trim().isEmpty()) return "이메일을 입력하세요.";
        if (!EMAIL.matcher(email.trim()).matches()) return "이메일 형식이 올바르지 않습니다.";
        return null;
    }

    public static String password(String password) {
        if (password == null || password.isEmpty()) return "비밀번호를 입력하세요.";
        if (password.length() < MIN_PASSWORD) return "비밀번호는 " + MIN_PASSWORD + "자 이상이어야 합니다.";
        return null;
    }

    public static String passwordConfirm(String password, String confirm) {
        if (confirm == null || !confirm.equals(password)) return "비밀번호가 서로 다릅니다.";
        return null;
    }

    public static String nickname(String nickname) {
        if (nickname == null || nickname.trim().isEmpty()) return "닉네임을 입력하세요.";
        if (nickname.trim().length() > MAX_NICKNAME) return "닉네임은 " + MAX_NICKNAME + "자 이하로 입력하세요.";
        return null;
    }

    /** "HH:MM" (한국 시간, docs/api.md 공통 규칙) */
    public static boolean isTime(String hhmm) {
        return hhmm != null && HH_MM.matcher(hhmm).matches();
    }
}
