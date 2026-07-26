package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;
import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;

class MaDisparityExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());
    private final MaDisparityExecutor executor = new MaDisparityExecutor(support);

    // 카카오 픽스처: closes 100..104 → MA5 = 102, 이격도 = (104/102 − 1)×100 = 1.960784…%
    @Test
    void computesDisparityAgainstMovingAverage() throws Exception {
        var args = mapper.readTree("""
            {"target":"카카오","window":5,"as_of":"2026-07-17"}""");
        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("MA_DISPARITY");
        assertThat(headlineValue(r, "5일선 이격도")).isCloseTo(1.960784, within(1e-4));
        assertThat(headlineValue(r, "현재가")).isCloseTo(104.0, within(1e-9));
        assertThat(headlineValue(r, "5일 이동평균")).isCloseTo(102.0, within(1e-9));
    }
}