package com.koscom.kopilot.chat;

import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * 대화 컨텍스트 저장소.
 *
 * <p>Redis는 살아 있는 컨텍스트 전용이다. 영속 이력은 MySQL chat_log가 담당하므로
 * Redis 유실은 서비스 중단이 아니라 맥락 초기화로 처리한다.</p>
 */
public interface ConversationStore {

    List<Message> load(String sessionId);

    void save(String sessionId, List<Message> messages);

    void clear(String sessionId);
}
