package com.koscom.kopilot.shortcut;

/** 단축키 프리셋 1개. targets는 "이름(코드)"를 콤마로 이은 문자열, period는 없을 수 있다. */
public record Shortcut(String id, String deviceId, String keyCombo, String toolName,
                       String targets, String period, String prompt) {}
