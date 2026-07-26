package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class NavDisparityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());
    private final NavDisparityExecutor executor = new NavDisparityExecutor(support);

    // 픽스처: (10000,10000),(10050,10000),(10100,10000) → 최신 +1.0%, 기간평균 +0.5%
    @Test
    void computesDisparityFromNav() throws Exception {
        var args = mapper.readTree("""
            {"target":"TIGER 미국S&P500","from":"2026-07-15","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("NAV_DISPARITY");
        assertThat(headlineValue(r, "최신 괴리율")).isCloseTo(1.0, within(1e-9));
        assertThat(headlineValue(r, "기간 평균 괴리율")).isCloseTo(0.5, within(1e-9));
        assertThat(r.chart().chartType()).isEqualTo("line");
    }

    @Test
    void nonEtfTarget_throwsNotEtf() throws Exception {
        var args = mapper.readTree("""
            {"target":"삼성전자","from":"2026-07-15","to":"2026-07-17"}""");
        assertThatThrownBy(() -> executor.execute(args))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("NOT_ETF"));
    }
}