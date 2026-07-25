package com.koscom.kopilot.checkapi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

@Configuration
public class CheckApiConfig {

    @Bean
    public RedisCacheStore checkApiShortTermCache(StringRedisTemplate redis,
                                                  @Value("${kopilot.cache-ttl}") Duration ttl) {
        return new RedisCacheStore(redis, ttl);
    }

    @Bean
    public JdbcFallbackStore checkApiFallbackStore(JdbcTemplate jdbc) {
        return new JdbcFallbackStore(jdbc);
    }

    @Bean
    @Profile("!fixture")
    public CheckApiClient checkApiClient(CheckApiProperties props,
                                         RedisCacheStore shortTerm,
                                         JdbcFallbackStore fallback) {
        return new CachingCheckApiClient(new RestCheckApiClient(props), shortTerm, fallback);
    }

    @Bean
    @Profile("fixture")
    public CheckApiClient fixtureCheckApiClient() {
        return new FixtureCheckApiClient();
    }
}
