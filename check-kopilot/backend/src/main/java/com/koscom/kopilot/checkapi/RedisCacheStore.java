package com.koscom.kopilot.checkapi;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Optional;

/** CHECK API 단기 응답 캐시 (Redis, TTL). 키 네임스페이스: checkapi: */
public class RedisCacheStore implements CachingCheckApiClient.KeyValueStore {

    private static final String PREFIX = "checkapi:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisCacheStore(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public void put(String key, String json) {
        try { redis.opsForValue().set(PREFIX + key, json, ttl); }
        catch (RuntimeException ignored) { /* 캐시 장애가 본 기능을 막지 않는다 */ }
    }

    @Override
    public Optional<String> get(String key) {
        try { return Optional.ofNullable(redis.opsForValue().get(PREFIX + key)); }
        catch (RuntimeException e) { return Optional.empty(); }
    }
}
