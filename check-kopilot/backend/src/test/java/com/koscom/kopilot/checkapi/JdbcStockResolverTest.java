package com.koscom.kopilot.checkapi;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

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

    // --- 되묻기 후보 랭킹 (#91) ---
    // 마스터가 13종목 데모 풀에서 전 상장 4,392행으로 늘면서 후보 품질이 무너졌다.
    // 정렬이 이름 길이순이라 "삼성"의 첫 후보가 삼성공조였고, 사용자가 고를 수 없는 목록이 나갔다.

    @Test
    void candidatesLeadWithFlagshipStock() {
        // "삼성" 부분매칭 97건. 대표종목이 목록 맨 앞에 와야 칩 한 번으로 끝난다.
        assertThat(resolver.search("삼성")).first()
                .extracting(StockInfo::code).isEqualTo("005930");
    }

    @Test
    void candidatesRankStocksAboveDerivatives() {
        // "하이닉스"는 STOCK 1건 + 레버리지 ETF 4건이라, 정렬을 안 하면 후보 5칸이 ETF로 찬다.
        assertThat(resolver.search("하이닉스")).first()
                .extracting(StockInfo::code).isEqualTo("000660");
    }

    @Test
    void preferredSharesRankBelowCommonShares() {
        // 우선주는 종목코드 끝자리로 판정한다. 이름 정규식 '우$'는 성우·에코글로우를 오탐한다.
        List<StockInfo> candidates = resolver.search("두산");
        List<String> codes = candidates.stream().map(StockInfo::code).toList();
        assertThat(codes).contains("000150");
        if (codes.contains("000155")) {                       // 두산우
            assertThat(codes.indexOf("000150")).isLessThan(codes.indexOf("000155"));
        }
    }

    @Test
    void commonSharesEndingInWooAreNotTreatedAsPreferred() {
        // 성우(458650)는 이름이 '우'로 끝나지만 보통주다 — 코드 끝자리가 0이다.
        assertThat(resolver.resolve("성우").code()).isEqualTo("458650");
    }

    @Test
    void resolvesNameWithCodeInParentheses() {
        // 되묻기 칩을 누르면 프론트가 "삼성전자(005930) 기준으로 진행해줘"를 보낸다.
        // LLM이 문자열을 그대로 넘겨도 코드로 맞춰야 한다 — LIKE '%삼성전자(005930)%'는 0건이다.
        assertThat(resolver.resolve("삼성전자(005930)").code()).isEqualTo("005930");
    }

    @Test
    void notFoundSuggestsFromLongestMatchingSubstring() {
        // "이차전지"는 앞 2글자 "이차"로는 0건이라 제안이 비어 나갔다. "차전지"는 걸린다.
        assertThatThrownBy(() -> resolver.resolve("이차전지"))
                .isInstanceOfSatisfying(StockNotFoundException.class,
                        e -> assertThat(e.suggestions()).isNotEmpty());
    }
}
