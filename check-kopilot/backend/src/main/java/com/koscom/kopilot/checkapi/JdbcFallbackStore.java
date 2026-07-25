package com.koscom.kopilot.checkapi;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Optional;

/** CHECK API 장애 대비 영속 스냅샷 (MySQL check_fallback). 데모 종목 풀은 warmup 스크립트로 사전 적재한다. */
public class JdbcFallbackStore implements CachingCheckApiClient.KeyValueStore {

    private final JdbcTemplate jdbc;

    public JdbcFallbackStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void put(String key, String json) {
        jdbc.update("""
            INSERT INTO check_fallback(cache_key, payload, fetched_at) VALUES (?, ?, CURRENT_TIMESTAMP)
            ON DUPLICATE KEY UPDATE payload = VALUES(payload), fetched_at = CURRENT_TIMESTAMP
            """, key, json);
    }

    @Override
    public Optional<String> get(String key) {
        return jdbc.queryForList("SELECT payload FROM check_fallback WHERE cache_key = ?", String.class, key)
                .stream().findFirst();
    }
}
