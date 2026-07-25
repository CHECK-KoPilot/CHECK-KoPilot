package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.CheckApiClient;
import com.koscom.kopilot.checkapi.StockResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfig {

    @Bean
    public ExecutorSupport executorSupport(CheckApiClient checkApi, StockResolver stocks) {
        return new ExecutorSupport(checkApi, stocks);
    }

    @Bean
    public ReturnGapExecutor returnGapExecutor(ExecutorSupport support) {
        return new ReturnGapExecutor(support);
    }
}
