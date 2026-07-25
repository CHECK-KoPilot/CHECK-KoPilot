package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("fixture")
class JdbcStockResolverTest {

    @Autowired StockResolver resolver;

    @Test
    void exactName_resolvesUniquely() {
        StockInfo s = resolver.resolve("삼성전자");
        assertThat(s.code()).isEqualTo("005930");
    }

    @Test
    void indexName_resolves() {
        StockInfo s = resolver.resolve("코스피");
        assertThat(s.isIndex()).isTrue();
    }

    // "에코프로"는 에코프로/에코프로비엠/에코프로에이치엔 3건 부분매칭이지만
    // 정확일치 "에코프로"가 존재하므로 정확일치 우선. "에코"는 정확일치가 없어 Ambiguous.
    @Test
    void exactMatchWinsOverPartialMatches() {
        assertThat(resolver.resolve("에코프로").code()).isEqualTo("086520");
    }

    @Test
    void partialOnly_throwsAmbiguousWithCandidates() {
        assertThatThrownBy(() -> resolver.resolve("에코"))
                .isInstanceOfSatisfying(AmbiguousStockException.class,
                        e -> assertThat(e.candidates()).hasSizeGreaterThanOrEqualTo(3));
    }

    @Test
    void unknownName_throwsNotFound() {
        assertThatThrownBy(() -> resolver.resolve("없는회사12345"))
                .isInstanceOf(StockNotFoundException.class);
    }
}
