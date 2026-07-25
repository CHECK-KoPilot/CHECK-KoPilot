package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class PeriodSummaryExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final PeriodSummaryExecutor executor = new PeriodSummaryExecutor(support);

    // 현대차 픽스처: high 최대 110, low 최소 95, close 100→105 (+5.0%)
    @Test
    void summarizesOhlcOverPeriod() throws Exception {
        var args = mapper.readTree("""
            {"target":"현대차","from":"2026-07-16","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("PERIOD_SUMMARY");
        assertThat(headlineValue(r, "기간 최고가")).isCloseTo(110.0, within(1e-9));
        assertThat(headlineValue(r, "기간 최저가")).isCloseTo(95.0, within(1e-9));
        assertThat(headlineValue(r, "기간수익률")).isCloseTo(5.0, within(1e-9));
        assertThat(r.chart().chartType()).isEqualTo("line");
    }
}