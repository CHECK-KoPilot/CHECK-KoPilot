package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class ReturnRankingExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());
    private final ReturnRankingExecutor executor = new ReturnRankingExecutor(support);

    // 픽스처 수익률: 에코프로 픽스처(100→108.9, +8.9%), 엘앤에프(200→204, +2%), 포스코퓨처엠(100→99, −1%)
    @Test
    void ranksTargetsByPeriodReturnDescending() throws Exception {
        var args = mapper.readTree("""
            {"targets":["에코프로","엘앤에프","포스코퓨처엠"],"from":"2026-07-14","to":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("RETURN_RANKING");
        // headline은 수익률 내림차순으로 "1위 …" 형식
        assertThat(r.headline().get(0).label()).startsWith("1위 에코프로");
        assertThat(r.headline().get(1).label()).startsWith("2위 엘앤에프");
        assertThat(r.headline().get(2).label()).startsWith("3위 포스코퓨처엠");
        assertThat(r.chart().chartType()).isEqualTo("bar");
    }
}