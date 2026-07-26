package com.koscom.kopilot.chat;

import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class RedisConversationStore implements ConversationStore {

    private static final String PREFIX = "kopilot:session:";

    private final StringRedisTemplate redis;
    private final ConversationCodec codec;
    private final Duration ttl;
    private final int maxTurns;

    public RedisConversationStore(StringRedisTemplate redis,
                                  @Value("${kopilot.session-ttl}") Duration ttl,
                                  @Value("${kopilot.max-history-turns}") int maxTurns) {
        this.redis = redis;
        this.codec = new ConversationCodec();
        this.ttl = ttl;
        this.maxTurns = maxTurns;
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
            redis.opsForValue().set(key, codec.encode(codec.trimToRecent(messages, maxTurns)), ttl);
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
