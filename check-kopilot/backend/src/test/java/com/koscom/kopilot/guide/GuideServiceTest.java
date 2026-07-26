package com.koscom.kopilot.guide;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class GuideServiceTest {

    private final ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
    private final GuideService guide = new GuideService(index, FieldDictionary.loadFromClasspath());

    @Test
    void indexLoadsAllApisAndResolvesAliases() {
        assertThat(index.all()).hasSizeGreaterThan(700);
        assertThat(index.byId("stock-daily")).isPresent()
                .get().extracting(ApiSpecEntry::path).isEqualTo("/stock/m001/hist_info");
        assertThat(index.byId("stock-m001-hist_info")).isPresent();   // 파생 id도 받는다
        assertThat(index.docUrl("stock-daily")).startsWith("https://checkapi.koscom.co.kr");
    }

    @Test
    void narrowsInvestorFlowQuestionToStockInvestHist() {
        GuideService.GuideResult r = guide.recipeContext("외국인 순매수 동향", List.of("외국인", "순매수"));

        assertThat(r.matched()).hasSizeLessThanOrEqualTo(5);
        // 104개 후보 중 주식 정본 모듈이 1위여야 한다 (파생 m238 등이 아니라)
        assertThat(r.matched().get(0).path()).isEqualTo("/stock/m001/invest_hist");
        // 매칭된 필드만 담는다 — 122개 전체가 아니라
        assertThat(r.matched().get(0).fields()).hasSizeLessThan(20);
        assertThat(r.matched().get(0).fields()).extracting(ApiSpecEntry.Field::label)
                .anySatisfy(l -> assertThat(l).contains("외국인"));
    }

    @Test
    void prefersCanonicalModuleOverNxtDuplicates() {
        GuideService.GuideResult r = guide.recipeContext("공매도 잔고", List.of("공매도"));
        // m222~m225(NXT/통합)는 m001/m003과 필드가 같으므로 뒤로 밀려야 한다
        assertThat(r.matched()).extracting(ApiSpecEntry::path)
                .noneMatch(p -> p.startsWith("/stock/m22"));
    }

    @Test
    void returnsRunnerUpsAsCatalogForCollapsibleUi() {
        GuideService.GuideResult r = guide.recipeContext("공매도 잔고", List.of("공매도"));
        assertThat(r.catalog()).isNotEmpty().hasSizeLessThanOrEqualTo(10);
        assertThat(r.usedKeywords()).contains("공매도");
    }

    @Test
    void noMatchReturnsEmptyMatchedNotException() {
        GuideService.GuideResult r = guide.recipeContext("존재하지 않는 지표", List.of("존재하지않는지표명xyz"));
        assertThat(r.matched()).isEmpty();   // LLM이 "제공 범위 밖"이라고 안내하도록
    }

    @Test
    void specsReturnsFullFieldListForRequestedApis() {
        List<ApiSpecEntry> specs = guide.specs(List.of("stock-investor"));
        assertThat(specs).hasSize(1);
        // get_api_spec은 전체 필드를 채운다
        assertThat(specs.get(0).fields()).hasSizeGreaterThan(100);
    }
}
