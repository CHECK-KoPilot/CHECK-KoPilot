package com.koscom.kopilot.chat;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisConversationStore implements ConversationStore {

    private static final Logger log = LoggerFactory.getLogger(RedisConversationStore.class);

    private static final String PREFIX = "kopilot:session:";

    /**
     * 보존 메시지 수의 하한. 한 턴이 tool 반복 상한을 다 쓰면
     * user + 8×(assistant + tool 응답) = 17메시지가 된다. 그보다 작게 잡으면 창에서
     * UserMessage가 밀려나 {@code trimToRecent}가 빈 히스토리를 돌려주고 맥락이 통째로 사라진다.
     */
    private static final int MIN_HISTORY_MESSAGES = 17;

    private final StringRedisTemplate redis;
    private final ConversationCodec codec;
    private final Duration ttl;
    private final int maxMessages;

    public RedisConversationStore(StringRedisTemplate redis,
                                  @Value("${kopilot.session-ttl}") Duration ttl,
                                  @Value("${kopilot.max-history-messages}") int maxMessages) {
        this.redis = redis;
        this.codec = new ConversationCodec();
        this.ttl = ttl;
        if (maxMessages < MIN_HISTORY_MESSAGES) {
            log.warn("kopilot.max-history-messages={}는 하한 {}보다 작다 — tool을 많이 쓴 턴에서 "
                    + "대화 맥락이 조용히 초기화될 수 있어 하한으로 올려 쓴다", maxMessages, MIN_HISTORY_MESSAGES);
        }
        this.maxMessages = Math.max(maxMessages, MIN_HISTORY_MESSAGES);
    }

    @Override
    public List<Message> load(String sessionId) {
        String key = key(sessionId);
        try {
            return codec.decode(redis.opsForValue().get(key));
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    @Override
    public void save(String sessionId, List<Message> messages) {
        String key = key(sessionId);
        try {
            redis.opsForValue().set(key, codec.encode(codec.trimToRecent(messages, maxMessages)), ttl);
        } catch (RuntimeException ignored) {
            // Redis 장애는 살아 있는 맥락만 잃게 하고 요청 처리는 계속 진행한다.
        }
    }

    @Override
    public void clear(String sessionId) {
        String key = key(sessionId);
        try {
            redis.delete(key);
        } catch (RuntimeException ignored) {
            // Redis 장애는 컨텍스트 정리 실패로만 취급한다.
        }
    }

    private String key(String sessionId) {
        return PREFIX + SessionIds.requireValid(sessionId);
    }
}
