package com.koscom.kopilot.chat;

import com.koscom.kopilot.catalog.CatalogService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class ChatConfig {

    // ChatModel(OpenAiChatModel) 빈은 spring-ai-starter-model-openai 자동 구성이 제공한다.
    // API 키는 spring.ai.openai.api-key ← 환경변수 OPENAI_API_KEY (check-kopilot/.env)

    @Bean
    public KopilotTools kopilotTools(CatalogService catalog) { return new KopilotTools(catalog); }

    @Bean(destroyMethod = "shutdown")
    public ExecutorService chatExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
