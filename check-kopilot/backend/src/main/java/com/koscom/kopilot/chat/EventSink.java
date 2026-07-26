package com.koscom.kopilot.chat;

/**
 * 채팅 진행 상황을 프론트로 밀어내는 통로.
 * ChatService가 서블릿 SseEmitter를 직접 알지 않게 하는 경계 — 루프 로직을 인프라 없이 테스트하기 위함이다.
 * 송출 실패는 대화 자체를 중단시킬 이유가 아니므로 구현체가 삼킨다.
 */
public interface EventSink {

    void send(String event, String dataJson);

    /**
     * 수신자가 아직 붙어 있는가. 클라이언트가 떠난 뒤에도 루프를 계속 돌리면
     * 남은 LLM·CHECK API 호출이 그대로 나가 비용만 새므로, 루프가 매 반복마다 확인한다.
     */
    default boolean isOpen() {
        return true;
    }
}
