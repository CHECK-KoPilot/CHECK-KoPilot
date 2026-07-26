package com.koscom.kopilot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 대화·tool 호출·에러를 {@code chat_log}에 적재한다(스펙 10절 관측성, Task 14 평가셋 원천 데이터).
 *
 * <p>적재 실패는 삼킨다. 이건 관측성 데이터지 대화의 하드 의존성이 아니다 —
 * DB가 죽었다고 사용자 응답까지 죽으면 안 된다. 특히 {@code ChatService}의 예외 처리 경로에서도
 * 호출되므로, 여기서 예외가 나가면 원래 예외를 덮어써 에러 이벤트 송출 자체를 막아버린다.</p>
 */
@Service
public class ChatLogService {

    private static final Logger log = LoggerFactory.getLogger(ChatLogService.class);

    private final JdbcTemplate jdbc;

    public ChatLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(String sessionId, String role, String toolName, String content) {
        try {
            jdbc.update("INSERT INTO chat_log(session_id, role, tool_name, content) VALUES (?,?,?,?)",
                    sessionId, role, toolName, content == null ? "" : content);
        } catch (RuntimeException e) {
            log.warn("chat_log 적재 실패 (session={}, role={}, tool={}) — 대화는 계속 진행한다",
                    sessionId, role, toolName, e);
        }
    }
}
