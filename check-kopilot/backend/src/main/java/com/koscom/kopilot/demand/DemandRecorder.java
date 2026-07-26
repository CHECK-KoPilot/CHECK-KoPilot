package com.koscom.kopilot.demand;

/** ToolDispatcher가 의존하는 최소 인터페이스 — 단위 테스트에서 스텁으로 대체한다. */
public interface DemandRecorder {
    String AUTO = "AUTO";
    String EXPLICIT = "EXPLICIT";

    void record(String sessionId, String topic, String matchedApiIds, String source);
}
