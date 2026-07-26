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

    // --- 별칭 ---
    // 공식 상장명이 "NAVER"라 "네이버"로는 못 찾았다. 자연어 제품에서 이건 버그에 가깝다.

    @Test
    void colloquialAlias_resolvesToOfficialListing() {
        assertThat(resolver.resolve("네이버").code()).isEqualTo("035420");
        assertThat(resolver.resolve("포스코").code()).isEqualTo("005490");
        assertThat(resolver.resolve("한전").code()).isEqualTo("015760");
    }

    @Test
    void spacelessIndexAlias_resolves() {
        // 마스터 이름은 "코스피 200"이라 "코스피200"은 LIKE로도 안 걸린다
        assertThat(resolver.resolve("코스피200").code()).isEqualTo("KOSPI-51");
        assertThat(resolver.resolve("코스닥150").code()).isEqualTo("KOSDAQ-203");
    }

    @Test
    void officialNameStillWinsOverAlias() {
        // 별칭은 정확일치 이름을 밀어내지 않는다
        assertThat(resolver.resolve("NAVER").code()).isEqualTo("035420");
        assertThat(resolver.resolve("KT").code()).isEqualTo("030200");
    }

    @Test
    void aliasAppearsInPartialSearchCandidates() {
        assertThat(resolver.search("네이버")).extracting(StockInfo::code).contains("035420");
    }
}
