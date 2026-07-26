package com.koscom.kopilot.guide;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 근거 패널의 명세 링크는 제품 원칙(CLAUDE.md "근거는 계약")이라 카테고리 목록으로 뭉개지면 안 된다.
 * 기대값은 docs/check-api/README.md에 실호출로 확정된 URL이다.
 */
class ApiSpecIndexDocUrlTest {

    @Test
    void derivesRealSpecPageForExecutorApis() {
        assertThat(ApiSpecIndex.docUrlOf("/stock/m001/hist_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m001hist");
        assertThat(ApiSpecIndex.docUrlOf("/stock/m002/hist_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m002hist");
        assertThat(ApiSpecIndex.docUrlOf("/stock/m003/hist_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m003hist");
        assertThat(ApiSpecIndex.docUrlOf("/stock/m004/hist_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m004hist");
        assertThat(ApiSpecIndex.docUrlOf("/stock/m001/invest_hist_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m001investhist");
    }

    @Test
    void collapsesUnderscoresAndDropsInfoSuffix() {
        assertThat(ApiSpecIndex.docUrlOf("/stock/m001/code_etf_info"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m001codeetf");
        assertThat(ApiSpecIndex.docUrlOf("/stock/m001/basic_info_all"))
                .isEqualTo("https://checkapi.koscom.co.kr/stock/m001basicall");
    }

    @Test
    void fallsBackToCategoryPageWhenNoSpecPageExists() {
        // 문서에 개별 페이지가 없는 경로(776건 중 62건)
        assertThat(ApiSpecIndex.docUrlOf("/stock/m222/member_date"))
                .isEqualTo("https://checkapi.koscom.co.kr/#/stock");
    }

    @Test
    void everyIndexedApiResolvesToAStockOrCategoryUrl() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();

        // 링크가 비거나 형식이 깨진 API가 하나도 없어야 한다
        assertThat(index.raws()).allSatisfy(raw ->
                assertThat(ApiSpecIndex.docUrlOf(raw.path()))
                        .startsWith("https://checkapi.koscom.co.kr/"));
    }
}
