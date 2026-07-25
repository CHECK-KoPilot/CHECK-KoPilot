package com.koscom.kopilot.chat;

import java.util.regex.Pattern;

/**
 * 로그인 없는 MVP의 익명 세션 식별자 검증.
 * Redis 키와 DB 컬럼에 그대로 쓰이므로 허용 문자를 제한한다.
 */
public final class SessionIds {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");

    private SessionIds() {
    }

    public static String requireValid(String sessionId) {
        if (sessionId == null || !ALLOWED.matcher(sessionId).matches()) {
            throw new IllegalArgumentException("잘못된 세션 식별자");
        }
        return sessionId;
    }
}
