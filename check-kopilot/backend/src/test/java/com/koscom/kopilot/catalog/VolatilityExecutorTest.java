package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class VolatilityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());
    private final VolatilityExecutor executor = new VolatilityExecutor(support);

    // 에코프로 픽스처: 일간수익률 {+10%, −10%, +10%} → 연율화 변동성 = √(252/75)×100 = 183.30303%
    // 삼성전자 픽스처: closes 100,101,102,103,105 (계산은 코드가 수행 — 값 존재만 확인)
    @Test
    void computesAnnualizedVolatilityPerTarget() throws Exception {
        var args = mapper.readTree("""
            {"targets":["에코프로","삼성전자"],"from":"2026-07-13","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("VOLATILITY");
        assertThat(headlineValue(r, "에코프로 연율화 변동성")).isCloseTo(183.30303, within(0.001));
        assertThat(r.chart().chartType()).isEqualTo("bar");
        assertThat(r.chart().series()).hasSize(1);
        assertThat(r.chart().series().get(0).points()).hasSize(2);
        assertThat(r.evidence().formula()).contains("√252");
    }
}