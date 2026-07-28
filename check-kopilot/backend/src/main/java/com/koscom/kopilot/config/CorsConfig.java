package com.koscom.kopilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 프론트와 백엔드가 서로 다른 오리진(도메인/포트)으로 배포되므로 CORS를 명시적으로 허용한다.
 * 로컬 개발은 vite dev 서버의 프록시가 같은 오리진처럼 보이게 해주므로 이 설정이 없어도 동작한다.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public CorsConfig(@Value("${kopilot.cors.allowed-origins:}") String allowedOrigins) {
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        if (allowedOrigins.length == 0) return;    // 미설정 시 CORS 규칙 자체를 추가하지 않는다
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE")
                .allowedHeaders("*");
    }
}
