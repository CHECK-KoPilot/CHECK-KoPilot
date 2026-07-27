package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.guide.ApiSpecIndex;
import org.junit.jupiter.api.Test;

import static com.koscom.kopilot.catalog.ReturnGapExecutorTest.headlineValue;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 픽스처 daily-005930: 종가 100, 101, 102, 103, 105 (2026-07-13 ~ 07-17).
 * 첫날 종가 100이 기준이므로 누적수익률은 0, 1, 2, 3, 5%이고 기간 누적수익률은 5%다.
 */
class CumulativeReturnExecutorTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());
    private final CumulativeReturnExecutor executor = new CumulativeReturnExecutor(support);

    @Test
    void computesCumulativeReturnSeriesForSingleTarget() throws Exception {
        var args = mapper.readTree("""
            {"target":"삼성전자","from":"2026-07-13","to":"2026-07-17"}""");

        MetricResult r = executor.execute(args);

        assertThat(r.metric()).isEqualTo("CUMULATIVE_RETURN");
        assertThat(r.targets()).singleElement()
                .satisfies(t -> assertThat(t.code()).isEqualTo("005930"));
        assertThat(headlineValue(r, "기간 누적수익률")).isCloseTo(5.0, within(1e-9));
        assertThat(headlineValue(r, "최고 누적수익률")).isCloseTo(5.0, within(1e-9));
        assertThat(headlineValue(r, "최저 누적수익률")).isCloseTo(0.0, within(1e-9));
    }

    @Test
    void chartIsCumulativeReturnNotClosePrice() throws Exception {
        // period_summary와 갈리는 지점 — 이 차트는 종가가 아니라 %다
        var args = mapper.readTree("""
            {"target":"삼성전자","from":"2026-07-13","to":"2026-07-17"}""");

        MetricResult r = executor.execute(args);

        assertThat(r.chart().chartType()).isEqualTo("line");
        assertThat(r.chart().series()).singleElement().satisfies(series ->
                assertThat(series.points()).extracting(MetricResult.ChartSpec.Point::value)
                        .containsExactly(0.0, 1.0, 2.0, 3.0, 5.0));
    }

    @Test
    void evidenceCarriesFormulaAndRawCloses() throws Exception {
        var args = mapper.readTree("""
            {"target":"삼성전자","from":"2026-07-13","to":"2026-07-17"}""");

        MetricResult r = executor.execute(args);

        assertThat(r.evidence().apiCalls()).singleElement()
                .satisfies(call -> assertThat(call.specUrl()).isNotBlank());
        assertThat(r.evidence().formula()).contains("기간 첫 종가");
        assertThat(r.evidence().steps()).extracting(MetricResult.Evidence.Step::label)
                .contains("기준 종가 (2026-07-13)", "기간 누적수익률", "최고 누적수익률", "최저 누적수익률");
        assertThat(r.evidence().rawData()).singleElement()
                .satisfies(raw -> assertThat(raw.rows()).hasSize(5));
    }
}
