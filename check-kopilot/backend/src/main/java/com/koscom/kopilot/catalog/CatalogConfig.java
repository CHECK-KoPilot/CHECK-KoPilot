package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.CheckApiClient;
import com.koscom.kopilot.checkapi.StockResolver;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.guide.FieldDictionary;
import com.koscom.kopilot.guide.GuideService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CatalogConfig {

    @Bean
    public FieldDictionary fieldDictionary() {
        return FieldDictionary.loadFromClasspath();
    }

    @Bean
    public ApiSpecIndex apiSpecIndex() {
        return ApiSpecIndex.loadFromClasspath();
    }

    @Bean
    public GuideService guideService(ApiSpecIndex index, FieldDictionary dict) {
        return new GuideService(index, dict);
    }

    @Bean
    public ExecutorSupport executorSupport(CheckApiClient checkApi, StockResolver stocks, ApiSpecIndex specIndex) {
        return new ExecutorSupport(checkApi, stocks, specIndex);
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
}
