package com.koscom.kopilot.chat;

import com.koscom.kopilot.catalog.CatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatConfig {

    // ChatModel(AnthropicChatModel) 빈은 spring-ai-starter-model-anthropic 자동 구성이 제공한다.
    // API 키는 spring.ai.anthropic.api-key ← 환경변수 ANTHROPIC_API_KEY

    @Bean
    public KopilotTools kopilotTools(CatalogService catalog) { return new KopilotTools(catalog); }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
