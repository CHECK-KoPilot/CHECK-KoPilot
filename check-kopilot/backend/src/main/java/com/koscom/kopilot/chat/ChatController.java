package com.koscom.kopilot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private static final long TIMEOUT_MS = 180_000L;

    /** 익명 엔드포인트라 길이 상한이 없으면 LLM 비용이 그대로 샌다 */
    private static final int MAX_MESSAGE_LENGTH = 2_000;

    private final ChatService chatService;
    private final ExecutorService chatExecutor;

    public ChatController(ChatService chatService, ExecutorService chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping(value = "/api/chat/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String safeSessionId = requireValidSession(sessionId);
        String message = requireMessage(body.get("message"));

        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        SseEmitterSink sink = new SseEmitterSink(emitter);

        // 컨테이너가 스트림을 닫으면(완료·타임아웃·에러) 워커가 그걸 보고 루프를 조기 종료한다
        emitter.onCompletion(sink::markClosed);
        emitter.onTimeout(sink::markClosed);
        emitter.onError(e -> sink.markClosed());

        chatExecutor.execute(() -> {
            try {
                chatService.handle(safeSessionId, message, sink);
            } catch (Throwable t) {
                // ChatService가 Exception을 삼키므로 여기 오는 건 Error 계열뿐이다.
                // submit()이 아니라 execute()를 쓰는 이유 — Future에 담겨 조용히 사라지지 않게.
                log.error("채팅 워커가 비정상 종료했다 (session={})", safeSessionId, t);
            } finally {
                emitter.complete();
            }
        });
        return emitter;
    }

    private String requireValidSession(String sessionId) {
        try {
            return SessionIds.requireValid(sessionId);
        } catch (IllegalArgumentException e) {
            // 예외 핸들러가 없으면 500으로 나간다 — docs/api.md는 요청 오류를 400으로 규정한다
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "잘못된 세션 식별자");
        }
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message가 비어 있습니다");
        }
        if (message.length() > MAX_MESSAGE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "message가 너무 깁니다 (최대 " + MAX_MESSAGE_LENGTH + "자)");
        }
        return message;
    }
}
