package com.koscom.kopilot.chat;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ExecutorService;

@RestController
public class ChatController {

    private static final long TIMEOUT_MS = 180_000L;

    private final ChatService chatService;
    private final ExecutorService chatExecutor;

    public ChatController(ChatService chatService, ExecutorService chatExecutor) {
        this.chatService = chatService;
        this.chatExecutor = chatExecutor;
    }

    @PostMapping(value = "/api/chat/{sessionId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@PathVariable String sessionId, @RequestBody Map<String, String> body) {
        String safeSessionId = SessionIds.requireValid(sessionId);   // 익명 세션 ID 형식 검증
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        String message = body.getOrDefault("message", "");
        chatExecutor.submit(() -> {
            try {
                chatService.handle(safeSessionId, message, new SseEmitterSink(emitter));
            } finally {
                emitter.complete();   // ChatService는 예외를 삼키므로 여기서 항상 닫는다
            }
        });
        return emitter;
    }
}
