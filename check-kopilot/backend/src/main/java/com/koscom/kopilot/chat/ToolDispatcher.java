package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.koscom.kopilot.catalog.CatalogService;
import com.koscom.kopilot.checkapi.AmbiguousStockException;
import com.koscom.kopilot.checkapi.CheckApiException;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.checkapi.StockNotFoundException;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.demand.DemandRecorder;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.GuideService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ToolDispatcher {

    public static final String EXPLAIN_RECIPE = "explain_recipe";
    public static final String GET_API_SPEC = "get_api_spec";

    private final CatalogService catalog;
    private final GuideService guide;
    private final CardSink cards;
    private final DemandRecorder demand;
    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public ToolDispatcher(CatalogService catalog, GuideService guide, CardSink cards, DemandRecorder demand) {
        this.catalog = catalog;
        this.guide = guide;
        this.cards = cards;
        this.demand = demand;
    }

    public DispatchResult dispatch(String sessionId, String toolName, JsonNode args) {
        try {
            if (EXPLAIN_RECIPE.equals(toolName)) return explainRecipe(sessionId, args);
            if (GET_API_SPEC.equals(toolName)) return getApiSpec(args);
            return metric(sessionId, toolName, args);
        } catch (AmbiguousStockException e) {
            ObjectNode result = mapper.createObjectNode()
                    .put("status", "ambiguous").put("query", e.query());
            result.set("candidates", candidatesNode(e.candidates()));
            ObjectNode event = mapper.createObjectNode().put("query", e.query());
            event.set("candidates", candidatesNode(e.candidates()));
            return new DispatchResult(result.toString(), false,
                    new DispatchResult.SsePush("clarify", event.toString()));
        } catch (StockNotFoundException e) {
            ObjectNode result = mapper.createObjectNode()
                    .put("status", "not_found").put("query", e.query());
            result.set("suggestions", candidatesNode(e.suggestions()));
            return new DispatchResult(result.toString(), false, null);
        } catch (MetricException e) {
            String json = mapper.createObjectNode().put("status", "error")
                    .put("code", e.code()).put("message", e.getMessage()).toString();
            return new DispatchResult(json, true, null);
        } catch (CheckApiException e) {
            String json = mapper.createObjectNode().put("status", "error")
                    .put("code", "CHECK_API_ERROR").put("message", e.getMessage()).toString();
            return new DispatchResult(json, true, null);
        } catch (IllegalArgumentException e) {
            // 정의되지 않은 tool 이름(CatalogService.byName) — LLM 환각/오타 대비.
            // 예외로 루프를 깨는 대신 구조화 에러로 돌려 Claude가 복구/재시도하게 한다.
            String json = mapper.createObjectNode().put("status", "error")
                    .put("code", "UNKNOWN_TOOL").put("message", e.getMessage()).toString();
            return new DispatchResult(json, true, null);
        }
    }

    private DispatchResult metric(String sessionId, String toolName, JsonNode args) {
        MetricResult card = catalog.byName(toolName).execute(args);
        cards.save(sessionId, card);
        try {
            String cardJson = mapper.writeValueAsString(card);
            ObjectNode compact = mapper.createObjectNode()
                    .put("status", "ok")
                    .put("cardId", card.cardId())
                    .put("title", card.title())
                    .put("period", card.from() + " ~ " + card.to());
            ArrayNode headline = compact.putArray("headline");
            card.headline().forEach(h -> headline.addObject()
                    .put("label", h.label()).put("value", h.value()).put("unit", h.unit()));
            return new DispatchResult(compact.toString(), false,
                    new DispatchResult.SsePush("card", cardJson));
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("카드 직렬화 실패", e);
        }
    }

    private DispatchResult explainRecipe(String sessionId, JsonNode args) {
        String topic = args.path("topic").asText("");
        // LLM이 사용자 표현을 명세 용어로 확장해 넘긴 검색어 (예: "수급" → ["투자자별","순매수"])
        List<String> keywords = new ArrayList<>();
        args.path("keywords").forEach(n -> keywords.add(n.asText()));
        // 키워드 미지정 시 topic을 공백으로 토큰화한다. search는 term별 부분일치라
        // topic 통짜("외국인 순매수 수급")를 한 키워드로 쓰면 어떤 F코드 라벨과도 매칭되지 않는다.
        if (keywords.isEmpty() && !topic.isBlank()) {
            for (String w : topic.trim().split("\\s+")) keywords.add(w);
        }
        GuideService.GuideResult r = guide.recipeContext(topic, keywords);

        // 버튼 클릭 여부와 무관하게, 가이드 카드가 뜬 것 자체가 "카탈로그가 못 답한 수요"다
        String matchedIds = r.matched().stream()
                .map(com.koscom.kopilot.guide.ApiSpecEntry::apiId)
                .collect(java.util.stream.Collectors.joining(","));
        demand.record(sessionId, topic, matchedIds, DemandRecorder.AUTO);

        ObjectNode payload = mapper.createObjectNode().put("topic", topic);
        payload.set("matched", mapper.valueToTree(r.matched()));
        payload.set("catalog", mapper.valueToTree(r.catalog()));
        payload.set("usedKeywords", mapper.valueToTree(r.usedKeywords()));
        String json = payload.toString();
        return new DispatchResult(json, false, new DispatchResult.SsePush("guide", json));
    }

    private DispatchResult getApiSpec(JsonNode args) {
        List<String> ids = new ArrayList<>();
        args.path("apiIds").forEach(n -> ids.add(n.asText()));
        return new DispatchResult(mapper.valueToTree(guide.specs(ids)).toString(), false, null);
    }

    private ArrayNode candidatesNode(List<StockInfo> stocks) {
        ArrayNode arr = mapper.createArrayNode();
        stocks.forEach(s -> arr.addObject()
                .put("code", s.code()).put("name", s.name()).put("market", s.market()));
        return arr;
    }
}
