package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.checkapi.StockInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorSupportTest {

    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), ApiSpecIndex.loadFromClasspath());

    /** 근거 패널의 API 링크는 실제 호출 엔드포인트를 따라간다 — 지수는 소속 시장으로 갈린다. */
    @Test
    void dailyApiId_matchesCalledEndpoint() {
        assertThat(support.dailyApiId(new StockInfo("005930", "삼성전자", "KOSPI", "STOCK")))
                .isEqualTo("stock-daily");
        assertThat(support.dailyApiId(new StockInfo("086520", "에코프로", "KOSDAQ", "STOCK")))
                .isEqualTo("kosdaq-daily");
        assertThat(support.dailyApiId(new StockInfo("KOSPI", "코스피", "KOSPI", "INDEX")))
                .isEqualTo("index-daily");
        assertThat(support.dailyApiId(new StockInfo("KOSDAQ", "코스닥", "KOSDAQ", "INDEX")))
                .isEqualTo("kosdaq-index-daily");
    }

    /** 명세 링크 공개는 제품 원칙이라 빈 링크가 카드에 실리면 안 된다. */
    @Test
    void everyDailyApiId_hasSpecUrl() {
        List<String> apiIds = List.of("stock-daily", "kosdaq-daily", "index-daily", "kosdaq-index-daily");

        assertThat(apiIds).allSatisfy(apiId ->
                assertThat(support.specUrl(apiId)).startsWith("https://checkapi.koscom.co.kr/stock/"));
    }

    // --- 기간 기본값 (parsePeriodOrRecent) ---

    private static com.fasterxml.jackson.databind.JsonNode json(String raw) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void periodOmitted_fallsBackToRecentWindow() {
        var p = support.parsePeriodOrRecent(json("{}"));

        assertThat(p.to()).isEqualTo(java.time.LocalDate.now());
        assertThat(p.from()).isEqualTo(java.time.LocalDate.now()
                .minusDays(ExecutorSupport.DEFAULT_PERIOD_DAYS));
    }

    @Test
    void onlyFromGiven_keepsItAndEndsToday() {
        // 준 값을 버리고 통째로 기본 기간을 쓰면 "묻지 않은 기간의 답"이 나간다
        var p = support.parsePeriodOrRecent(json("{\"from\":\"2026-01-05\"}"));

        assertThat(p.from()).isEqualTo(java.time.LocalDate.parse("2026-01-05"));
        assertThat(p.to()).isEqualTo(java.time.LocalDate.now());
    }

    @Test
    void onlyToGiven_keepsItAndBacksOffWindow() {
        var p = support.parsePeriodOrRecent(json("{\"to\":\"2026-06-30\"}"));

        assertThat(p.to()).isEqualTo(java.time.LocalDate.parse("2026-06-30"));
        assertThat(p.from()).isEqualTo(java.time.LocalDate.parse("2026-06-30")
                .minusDays(ExecutorSupport.DEFAULT_PERIOD_DAYS));
    }

    @Test
    void blankPeriodStringsCountAsOmitted() {
        var p = support.parsePeriodOrRecent(json("{\"from\":\"\",\"to\":\"  \"}"));

        assertThat(p.to()).isEqualTo(java.time.LocalDate.now());
    }

    @Test
    void oneSidedPeriodStillValidates() {
        assertThatThrownBy(() -> support.parsePeriodOrRecent(json("{\"to\":\"2099-01-01\"}")))
                .isInstanceOf(com.koscom.kopilot.domain.MetricException.class);
        assertThatThrownBy(() -> support.parsePeriodOrRecent(json("{\"from\":\"엉터리\"}")))
                .isInstanceOf(com.koscom.kopilot.domain.MetricException.class);
    }

    /**
     * 이슈 #58. Math.round(Infinity)는 예외가 아니라 Long.MAX_VALUE라서, 0으로 나눈 결과가
     * 922,337,203,685,477.63% 라는 그럴듯한 숫자로 둔갑해 근거 패널까지 달고 카드에 실렸다.
     */
    @Test
    void round4_rejectsNonFiniteInsteadOfPrintingHugeNumber() {
        for (double bad : new double[] {
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NaN }) {
            assertThatThrownBy(() -> ExecutorSupport.round4(bad))
                    .isInstanceOfSatisfying(com.koscom.kopilot.domain.MetricException.class,
                            e -> assertThat(e.code()).isEqualTo("CALCULATION_INVALID"));
        }
    }

    @Test
    void round4_roundsToFourDecimals() {
        assertThat(ExecutorSupport.round4(-22.755418)).isEqualTo(-22.7554);
    }
}
