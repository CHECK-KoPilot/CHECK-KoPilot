package com.koscom.kopilot.chat;

import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.guide.ApiSpecIndex;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class KopilotToolsTest {

    @Test
    void buildsEightToolDefinitionsWithNamesAndJsonSchema() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(
                new ReturnGapExecutor(support), new VolatilityExecutor(support),
                new NavDisparityExecutor(support), new MaDisparityExecutor(support),
                new ReturnRankingExecutor(support), new PeriodSummaryExecutor(support)));

        List<ToolCallback> tools = new KopilotTools(catalog).build();

        assertThat(tools).hasSize(8);
        assertThat(tools).extracting(t -> t.getToolDefinition().name()).containsExactlyInAnyOrder(
                "return_gap", "volatility", "nav_disparity", "ma_disparity",
                "return_ranking", "period_summary", "explain_recipe", "get_api_spec");

        String returnGapSchema = tools.stream()
                .filter(t -> t.getToolDefinition().name().equals("return_gap"))
                .findFirst().orElseThrow().getToolDefinition().inputSchema();
        assertThat(returnGapSchema)
                .contains("\"type\":\"object\"").contains("target_a").contains("required");
    }

    @Test
    void toolCallbacksRefuseAutomaticExecution() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support = new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(new ReturnGapExecutor(support)));

        ToolCallback any = new KopilotTools(catalog).build().get(0);
        // 실행은 ToolDispatcher 전담 — 자동 실행이 켜지면 조용히 우회되는 대신 즉시 실패해야 한다
        assertThatThrownBy(() -> any.call("{}")).isInstanceOf(IllegalStateException.class);
    }
}
