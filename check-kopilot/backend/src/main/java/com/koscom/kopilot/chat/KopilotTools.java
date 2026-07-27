package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.catalog.MetricExecutor;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 지표 실행기 7종 + 가이드 tool 2종을 Spring AI ToolCallback(스키마 전용)으로 변환한다.
 * 실제 실행은 ChatService의 수동 루프가 ToolDispatcher로 수행한다.
 */
public class KopilotTools {

    private final CatalogService catalog;
    private final ObjectMapper mapper = new ObjectMapper();

    public KopilotTools(CatalogService catalog) { this.catalog = catalog; }

    public List<ToolCallback> build() {
        List<ToolCallback> tools = new ArrayList<>();
        for (MetricExecutor e : catalog.all()) {
            tools.add(tool(e.toolName(), e.description(), e.inputSchemaProperties(), e.requiredParams()));
        }
        tools.add(tool(ToolDispatcher.EXPLAIN_RECIPE,
                "카탈로그 6개 지표로 답할 수 없는 데이터/지표 질문이거나, 사용자가 구현 방법·API 조합을 물을 때 사용. "
              + "관련 CHECK API 후보와 각 API가 반환하는 관련 필드를 찾아 반환한다. 이를 바탕으로 어떤 API를 어떤 "
              + "파라미터로 호출해 어떤 필드를 어떻게 조합·계산하면 되는지 레시피를 설명할 것. "
              + "거절 대신 항상 이 tool로 가이드를 제공한다.",
                Map.of(
                    "topic", Map.of("type", "string",
                        "description", "사용자가 원하는 데이터/지표에 대한 한국어 설명"),
                    "keywords", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "검색어. 사용자의 구어 표현이 아니라 **금융 데이터 명세에 쓰일 법한 용어**로 "
                            + "변환해 2~4개 넣을 것. 예: \"수급 어때?\" → [\"투자자별\",\"순매수\"], "
                            + "\"외인 물량\" → [\"외국인\",\"순매수\"], \"공매 얼마나 쌓였어\" → [\"공매도\",\"잔고\"]")),
                List.of("topic", "keywords")));
        tools.add(tool(ToolDispatcher.EXPLAIN_METRIC_RECIPE,
                "**이미 지표 카드로 답한** 지표를 사용자가 직접 구현하는 방법을 물을 때 사용. "
              + "카탈로그 밖 지표에는 explain_recipe를 쓸 것 — 이 tool은 카탈로그 안 지표 전용이다. "
              + "질문에 적힌 apiId를 그대로 넘기면 해당 API의 실제 경로·파라미터·필드를 돌려준다. "
              + "반환된 path와 F코드만 인용하고 지어내지 말 것.",
                Map.of(
                    "metric", Map.of("type", "string", "description", "지표 이름 (카드 제목)"),
                    "apiIds", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "그 카드가 실제로 호출한 apiId 목록. 질문에 괄호로 적혀 있다 "
                            + "(예: \"일별 시세 조회(stock-daily)\" → \"stock-daily\")")),
                List.of("metric", "apiIds")));
        tools.add(tool(ToolDispatcher.GET_API_SPEC,
                "explain_recipe 결과의 catalog 목록에서 상세 명세가 더 필요한 API가 있을 때, apiId 배열로 상세를 조회한다.",
                Map.of("apiIds", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "상세 명세를 조회할 apiId 목록")),
                List.of("apiIds")));
        return tools;
    }

    private ToolCallback tool(String name, String description,
                              Map<String, Object> props, List<String> required) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", mapper.valueToTree(props));
        ArrayNode req = schema.putArray("required");
        required.forEach(req::add);

        ToolDefinition definition = ToolDefinition.builder()
                .name(name)
                .description(description)
                .inputSchema(schema.toString())
                .build();
        return new SchemaOnlyToolCallback(definition);
    }

    /** 스키마만 제공하는 ToolCallback. 자동 실행이 켜져 있으면 조용히 우회되지 않고 즉시 터진다. */
    static final class SchemaOnlyToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        SchemaOnlyToolCallback(ToolDefinition definition) { this.definition = definition; }

        @Override public ToolDefinition getToolDefinition() { return definition; }

        @Override public String call(String toolInput) {
            throw new IllegalStateException(
                "tool 자동 실행이 활성화되어 있습니다. ToolCallingChatOptions.internalToolExecutionEnabled=false "
                + "설정을 확인하세요. tool=" + definition.name());
        }

        @Override public String call(String toolInput, ToolContext toolContext) { return call(toolInput); }
    }
}
