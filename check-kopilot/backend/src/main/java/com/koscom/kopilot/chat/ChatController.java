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
    private final ConversationStore conversations;

    public ChatController(ChatService chatService, ExecutorService chatExecutor,
                          ConversationStore conversations) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
        this.conversations = conversations;
    }

    /**
     * "새 대화" — 서버가 들고 있는 대화 컨텍스트를 즉시 버린다.
     *
     * <p>프론트가 localStorage의 세션 UUID만 새로 만들면 이전 컨텍스트는 TTL(2시간) 동안 Redis에 남는다.
     * 데모 중 세션을 반복해서 갈아엎으면 그만큼 키가 쌓이므로, 떠날 때 명시적으로 정리한다.
     * 영속 이력은 {@code chat_log}가 갖고 있으므로 여기서 지우는 것은 "살아 있는 맥락"뿐이다.</p>
     */
    @DeleteMapping("/api/chat/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void endSession(@PathVariable String sessionId) {
        conversations.clear(requireValidSession(sessionId));
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
