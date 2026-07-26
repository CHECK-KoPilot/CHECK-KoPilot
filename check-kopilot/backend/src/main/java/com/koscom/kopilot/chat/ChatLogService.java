package com.koscom.kopilot.chat;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatLogService {

    private final JdbcTemplate jdbc;

    public ChatLogService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public void log(String sessionId, String role, String toolName, String content) {
        // 관측성(스펙 10절): 대화·tool 호출·에러 전부 기록 — 평가셋 원천 데이터이자 영속 대화 이력
        jdbc.update("INSERT INTO chat_log(session_id, role, tool_name, content) VALUES (?,?,?,?)",
                sessionId, role, toolName, content == null ? "" : content);
    }
}
