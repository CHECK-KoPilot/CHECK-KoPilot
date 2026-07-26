package com.koscom.kopilot.chat;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * EventSink → 서블릿 SSE 어댑터.
 * 송출 실패(클라이언트 이탈 등)는 삼킨다 — 이미 끊긴 연결에 예외를 던져봐야 대화만 중단된다.
 */
public class SseEmitterSink implements EventSink {

    private final SseEmitter emitter;

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void send(String event, String dataJson) {
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(dataJson, MediaType.APPLICATION_JSON));
        } catch (Exception ignored) {
            // 연결이 이미 닫힌 경우
        }
    }
}
