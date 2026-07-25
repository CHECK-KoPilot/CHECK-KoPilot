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

    @Bean
    public VolatilityExecutor volatilityExecutor(ExecutorSupport s) {
        return new VolatilityExecutor(s);
    }

    @Bean
    public PeriodSummaryExecutor periodSummaryExecutor(ExecutorSupport s) {
        return new PeriodSummaryExecutor(s);
    }

    @Bean
    public NavDisparityExecutor navDisparityExecutor(ExecutorSupport s) {
        return new NavDisparityExecutor(s);
    }

    @Bean
    public MaDisparityExecutor maDisparityExecutor(ExecutorSupport s) {
        return new MaDisparityExecutor(s);
    }

    @Bean
    public ReturnRankingExecutor returnRankingExecutor(ExecutorSupport s) {
        return new ReturnRankingExecutor(s);
    }
}
