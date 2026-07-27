package com.koscom.kopilot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * EventSink → 서블릿 SSE 어댑터.
 *
 * <p>송출 실패(클라이언트 이탈, 타임아웃)는 예외로 올리지 않는다 — 이미 끊긴 연결에 예외를 던져봐야
 * 대화만 중단된다. 대신 닫힘 상태를 기록해 {@link #isOpen()}이 false를 돌려주고,
 * 루프가 그걸 보고 조기에 빠져나온다(남은 LLM 호출이 그대로 나가는 것을 막는다).</p>
 */
public class SseEmitterSink implements EventSink {

    private static final Logger log = LoggerFactory.getLogger(SseEmitterSink.class);

    private final SseEmitter emitter;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public SseEmitterSink(SseEmitter emitter) {
        this.emitter = emitter;
    }

    /** 컨테이너가 스트림을 닫았을 때(완료·타임아웃·에러) 컨트롤러가 호출한다. */
    public void markClosed() {
        closed.set(true);
    }

    @Override
    public boolean isOpen() {
        return !closed.get();
    }

    @Override
    public void send(String event, String dataJson) {
        if (closed.get()) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(event)
                    .data(dataJson, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            closed.set(true);
            log.debug("SSE 송출 실패 — 수신자가 떠난 것으로 보고 이후 송출을 중단한다 (event={})", event, e);
        }
    }
}
