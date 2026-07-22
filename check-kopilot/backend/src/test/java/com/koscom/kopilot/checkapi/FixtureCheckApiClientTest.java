package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class FixtureCheckApiClientTest {

    private final FixtureCheckApiClient client = new FixtureCheckApiClient();
    private final StockInfo samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
    private final StockInfo tigerSnp = new StockInfo("360750", "TIGER 미국S&P500", "KOSPI", "ETF");

    @Test
    void dailyQuotes_readsFixtureAndFiltersByDate() {
        List<DailyQuote> all = client.dailyQuotes(samsung,
                LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17"));
        assertThat(all).hasSize(5);
        assertThat(all.get(0).close()).isEqualTo(100.0);
        assertThat(all.get(4).close()).isEqualTo(105.0);

        List<DailyQuote> partial = client.dailyQuotes(samsung,
                LocalDate.parse("2026-07-15"), LocalDate.parse("2026-07-17"));
        assertThat(partial).hasSize(3);
    }

    @Test
    void etfNav_readsNavFixture() {
        List<NavQuote> navs = client.etfNav(tigerSnp,
                LocalDate.parse("2026-07-15"), LocalDate.parse("2026-07-17"));
        assertThat(navs).hasSize(3);
        assertThat(navs.get(2).marketPrice()).isEqualTo(10100.0);
        assertThat(navs.get(2).nav()).isEqualTo(10000.0);
    }

    @Test
    void unknownSymbol_throwsCheckApiException() {
        StockInfo ghost = new StockInfo("999999", "없는종목", "KOSPI", "STOCK");
        assertThatThrownBy(() -> client.dailyQuotes(ghost,
                LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17")))
                .isInstanceOf(CheckApiException.class);
    }
}
