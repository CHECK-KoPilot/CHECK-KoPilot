package com.koscom.kopilot.catalog;

import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.checkapi.StockInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutorSupportTest {

    private final ExecutorSupport support =
            new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver());

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
}
