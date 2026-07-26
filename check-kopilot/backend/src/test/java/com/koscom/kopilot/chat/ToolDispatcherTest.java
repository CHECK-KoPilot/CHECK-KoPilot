package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.demand.DemandRecorder;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.guide.FieldDictionary;
import com.koscom.kopilot.guide.GuideService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ToolDispatcherTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<MetricResult> savedCards = new ArrayList<>();
    private final List<String> recordedDemand = new ArrayList<>();

    private ToolDispatcher dispatcher() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(
                new ReturnGapExecutor(support), new VolatilityExecutor(support),
                new NavDisparityExecutor(support), new MaDisparityExecutor(support),
                new ReturnRankingExecutor(support), new PeriodSummaryExecutor(support)));
        CardSink sink = (sessionId, r) -> savedCards.add(r);
        DemandRecorder demand = (sessionId, topic, apiIds, source) ->
                recordedDemand.add(source + "|" + topic + "|" + apiIds);
        return new ToolDispatcher(catalog, new GuideService(index, FieldDictionary.loadFromClasspath()),
                sink, demand);
    }

    @Test
    void metricTool_savesCard_emitsCardEvent_returnsCompactResult() throws Exception {
        var r = dispatcher().dispatch("sess-1", "return_gap", mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-13","to":"2026-07-17"}"""));

        assertThat(r.isError()).isFalse();
        assertThat(savedCards).hasSize(1);
        assertThat(r.push().event()).isEqualTo("card");
        // SSE에는 근거 포함 전문, tool_result에는 컴팩트 요약(rawData 미포함)
        assertThat(r.push().dataJson()).contains("evidence");
        assertThat(r.toolResultJson()).contains("\"status\":\"ok\"").contains("cardId")
                .doesNotContain("rawData");
    }

    @Test
    void ambiguousStock_emitsClarifyEvent() throws Exception {
        var r = dispatcher().dispatch("sess-1", "period_summary", mapper.readTree("""
            {"target":"에코","from":"2026-07-14","to":"2026-07-17"}"""));

        assertThat(r.isError()).isFalse();      // 에러가 아니라 되묻기 유도
        assertThat(r.push().event()).isEqualTo("clarify");
        assertThat(r.toolResultJson()).contains("\"status\":\"ambiguous\"").contains("에코프로비엠");
    }

    @Test
    void validationFailure_returnsStructuredErrorWithIsError() throws Exception {
        var r = dispatcher().dispatch("sess-1", "return_gap", mapper.readTree("""
            {"target_a":"삼성전자","target_b":"코스피","from":"2026-07-17","to":"2026-07-13"}"""));

        assertThat(r.isError()).isTrue();
        assertThat(r.toolResultJson()).contains("PERIOD_INVERTED");
        assertThat(r.push()).isNull();
    }

    @Test
    void explainRecipe_emitsGuideEvent_withCatalogAndMatches() throws Exception {
        // 운영 계약: LLM이 topic과 함께 확장 keywords를 넘긴다(스펙 8절). 검색 정밀도는 keywords가 좌우한다.
        var r = dispatcher().dispatch("sess-1", "explain_recipe", mapper.readTree("""
            {"topic":"외국인 순매수 수급","keywords":["외국인","순매수"]}"""));

        assertThat(r.isError()).isFalse();
        assertThat(r.push().event()).isEqualTo("guide");
        assertThat(r.toolResultJson()).contains("stock-investor").contains("catalog");

        // 버튼 클릭 없이도 수요가 적재된다(AUTO)
        assertThat(recordedDemand).hasSize(1);
        assertThat(recordedDemand.get(0)).startsWith("AUTO|외국인 순매수 수급|").contains("stock-investor");
    }

    @Test
    void getApiSpec_returnsFullEntries_noEvent() throws Exception {
        // 주의: apiIds는 api-aliases.yaml에 정의된 별칭이어야 한다 (미정의 id는 결과에서 누락됨)
        var r = dispatcher().dispatch("sess-1", "get_api_spec", mapper.readTree("""
            {"apiIds":["stock-daily","etf-code"]}"""));

        assertThat(r.isError()).isFalse();
        assertThat(r.push()).isNull();
        assertThat(r.toolResultJson()).contains("stock-daily").contains("etf-code");
    }

    @Test
    void unknownTool_returnsStructuredErrorNotException() throws Exception {
        // LLM이 정의되지 않은 tool을 부르면 예외로 루프가 깨지면 안 되고, 구조화 에러로 돌아와야 한다.
        var r = dispatcher().dispatch("sess-1", "does_not_exist", mapper.readTree("{}"));

        assertThat(r.isError()).isTrue();
        assertThat(r.toolResultJson()).contains("UNKNOWN_TOOL");
        assertThat(r.push()).isNull();
    }
}
