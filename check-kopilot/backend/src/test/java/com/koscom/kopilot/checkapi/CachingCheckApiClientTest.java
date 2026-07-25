package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;

class CachingCheckApiClientTest {

    /** Redis/MySQL 없이 단위 테스트하기 위한 인메모리 저장소 */
    static class MapStore implements CachingCheckApiClient.KeyValueStore {
        final Map<String, String> map = new HashMap<>();
        public void put(String key, String json) { map.put(key, json); }
        public Optional<String> get(String key) { return Optional.ofNullable(map.get(key)); }
    }

    static CheckApiClient broken() {
        return new CheckApiClient() {
            public List<DailyQuote> dailyQuotes(StockInfo i, LocalDate f, LocalDate t) {
                throw new CheckApiException("down");
            }
            public List<NavQuote> etfNav(StockInfo e, LocalDate f, LocalDate t) {
                throw new CheckApiException("down");
            }
        };
    }

    private final StockInfo samsung = new StockInfo("005930", "삼성전자", "KOSPI", "STOCK");
    private final LocalDate from = LocalDate.parse("2026-07-13");
    private final LocalDate to = LocalDate.parse("2026-07-17");

    @Test
    void success_populatesShortTermCacheAndFallbackSnapshot() {
        MapStore hot = new MapStore();
        MapStore cold = new MapStore();

        CachingCheckApiClient c = new CachingCheckApiClient(new FixtureCheckApiClient(), hot, cold);
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
        assertThat(hot.map).isNotEmpty();
        assertThat(cold.map).isNotEmpty();
    }

    @Test
    void shortTermCacheHit_skipsDelegate() {
        MapStore hot = new MapStore();
        MapStore cold = new MapStore();
        new CachingCheckApiClient(new FixtureCheckApiClient(), hot, cold).dailyQuotes(samsung, from, to);

        // 델리게이트가 죽어도 단기 캐시가 살아 있으면 정상 응답
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), hot, new MapStore());
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
    }

    @Test
    void delegateFailure_fallsBackToPersistentSnapshot() {
        MapStore cold = new MapStore();
        new CachingCheckApiClient(new FixtureCheckApiClient(), new MapStore(), cold).dailyQuotes(samsung, from, to);

        // 단기 캐시는 비어 있고(TTL 만료 가정) 델리게이트도 실패 → MySQL 폴백 스냅샷 사용
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), new MapStore(), cold);
        assertThat(c.dailyQuotes(samsung, from, to)).hasSize(5);
    }

    @Test
    void failureWithoutAnyCache_rethrows() {
        CachingCheckApiClient c = new CachingCheckApiClient(broken(), new MapStore(), new MapStore());
        assertThatThrownBy(() -> c.dailyQuotes(samsung, from, to))
                .isInstanceOf(CheckApiException.class);
    }
}
