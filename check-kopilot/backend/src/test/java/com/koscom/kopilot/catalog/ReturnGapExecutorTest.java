package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ReturnGapExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());
    private final ReturnGapExecutor executor = new ReturnGapExecutor(support);

    @Test
    void computesGapFromFixtures() throws Exception {
        var args = mapper.readTree("""
                {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-13","to":"2026-07-17"}""");

        MetricResult result = executor.execute(args);

        assertThat(result.metric()).isEqualTo("RETURN_GAP");
        assertThat(result.headline()).extracting(MetricResult.Headline::label)
                .contains("삼성전자 기간수익률", "코스피 기간수익률", "수익률 갭");
        assertThat(headlineValue(result, "수익률 갭")).isCloseTo(3.0, within(1e-9));
        assertThat(headlineValue(result, "삼성전자 기간수익률")).isCloseTo(5.0, within(1e-9));
        assertThat(result.evidence().apiCalls()).isNotEmpty();
        assertThat(result.evidence().rawData()).hasSize(2);
        assertThat(result.evidence().formula()).contains("기간수익률");
        assertThat(result.evidence().steps()).isNotEmpty();
        assertThat(result.chart().chartType()).isEqualTo("line");
        assertThat(result.chart().series()).hasSize(2);
    }

    @Test
    void invertedPeriod_throwsMetricException() throws Exception {
        var args = mapper.readTree("""
                {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-17","to":"2026-07-13"}""");

        assertThatThrownBy(() -> executor.execute(args))
                .isInstanceOfSatisfying(MetricException.class,
                        exception -> assertThat(exception.code()).isEqualTo("PERIOD_INVERTED"));
    }

    public static double headlineValue(MetricResult result, String label) {
        return result.headline().stream()
                .filter(headline -> headline.label().equals(label))
                .findFirst()
                .orElseThrow()
                .value();
    }
}
