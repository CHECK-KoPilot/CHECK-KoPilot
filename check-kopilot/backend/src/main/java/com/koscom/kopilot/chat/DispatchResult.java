package com.koscom.kopilot.chat;

public record DispatchResult(String toolResultJson, boolean isError, SsePush push) {
    public record SsePush(String event, String dataJson) {}
}
