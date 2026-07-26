package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.koscom.kopilot.catalog.*;
import com.koscom.kopilot.checkapi.FixtureCheckApiClient;
import com.koscom.kopilot.domain.MetricResult;
import com.koscom.kopilot.export.CardSink;
import com.koscom.kopilot.guide.ApiSpecIndex;
import com.koscom.kopilot.guide.FieldDictionary;
import com.koscom.kopilot.guide.GuideService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ChatServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> events = new ArrayList<>();
    private final List<Prompt> seenPrompts = new ArrayList<>();
    private final List<Message> savedContext = new ArrayList<>();

    /** 스크립트된 응답을 순서대로 돌려주는 ChatModel 테스트 더블. 실 LLM 없이 루프를 검증한다. */
    private class ScriptedChatModel implements ChatModel {
        private final Deque<AssistantMessage> script = new ArrayDeque<>();

        ScriptedChatModel(AssistantMessage... responses) {
            for (AssistantMessage m : responses) script.add(m);
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            seenPrompts.add(prompt);
            AssistantMessage next = script.poll();
            if (next == null) throw new AssertionError("스크립트보다 많이 호출됨");
            return new ChatResponse(List.of(new Generation(next, ChatGenerationMetadata.NULL)));
        }
    }

    private final EventSink sink = (event, dataJson) -> events.add(event);

    private final ConversationStore conversations = new ConversationStore() {
        @Override public List<Message> load(String sessionId) { return List.of(); }
        @Override public void save(String sessionId, List<Message> messages) {
            savedContext.clear();
            savedContext.addAll(messages);
        }
        @Override public void clear(String sessionId) { }
    };

    private final List<String> logged = new ArrayList<>();
    private final ChatLogService logs = new ChatLogService(null) {
        @Override public void log(String sessionId, String role, String toolName, String content) {
            logged.add(role + ":" + toolName);
        }
    };

    private ToolDispatcher dispatcher() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support =
                new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        CatalogService catalog = new CatalogService(List.of(new ReturnGapExecutor(support)));
        CardSink cards = (sessionId, r) -> { };
        return new ToolDispatcher(catalog,
                new GuideService(index, FieldDictionary.loadFromClasspath()), cards);
    }

    private KopilotTools tools() {
        ApiSpecIndex index = ApiSpecIndex.loadFromClasspath();
        ExecutorSupport support =
                new ExecutorSupport(new FixtureCheckApiClient(), TestStocks.resolver(), index);
        return new KopilotTools(new CatalogService(List.of(new ReturnGapExecutor(support))));
    }

    /** 모델 ID·토큰 상한은 운영에서 yml이 주입한다 — 루프 동작과 무관하므로 테스트는 고정값을 쓴다. */
    private ChatService chatService(ChatModel model) {
        return new ChatService(model, tools(), dispatcher(), conversations, logs, "gpt-4o", 4096);
    }

    private static AssistantMessage toolCall(String id, String name, String args) {
        return new AssistantMessage("", Map.of(),
                List.of(new AssistantMessage.ToolCall(id, "function", name, args)));
    }

    @Test
    void dispatchesToolCall_thenEmitsCardTextDone() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_1", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}"),
                new AssistantMessage("삼성전자가 코스피를 앞섰습니다."));

        chatService(model)
                .handle("sess-1", "삼성전자랑 코스피 수익률 갭", sink);

        assertThat(events).containsExactly("card", "text", "done");
    }

    @Test
    void feedsToolResultBackWithMatchingToolUseId() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_42", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}"),
                new AssistantMessage("해설"));

        chatService(model)
                .handle("sess-1", "질문", sink);

        // 2회차 호출 프롬프트에 tool 호출 id가 그대로 실린 ToolResponseMessage가 있어야 한다.
        // OpenAI는 tool_calls/tool_call_id 불일치를 400으로 거부한다.
        List<Message> second = seenPrompts.get(1).getInstructions();
        ToolResponseMessage toolMsg = second.stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMsg.getResponses().get(0).id()).isEqualTo("call_42");
        assertThat(toolMsg.getResponses().get(0).name()).isEqualTo("return_gap");
        assertThat(toolMsg.getResponses().get(0).responseData()).contains("\"status\":\"ok\"");
    }

    @Test
    void noToolCall_emitsTextAndDoneOnly() {
        ChatModel model = new ScriptedChatModel(
                new AssistantMessage("투자 판단은 제공하지 않습니다. 대신 수익률·변동성을 확인하실 수 있습니다."));

        chatService(model)
                .handle("sess-1", "삼성전자 사야 돼?", sink);

        assertThat(events).containsExactly("text", "done");
    }

    @Test
    void validationFailure_doesNotBreakStream_returnsErrorToModel() {
        ChatModel model = new ScriptedChatModel(
                toolCall("call_1", "return_gap",
                        "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                        + "\"from\":\"2026-07-17\",\"to\":\"2026-07-13\"}"),   // 기간 역전
                new AssistantMessage("기간이 뒤집혀 있습니다. 시작일을 다시 알려주세요."));

        chatService(model)
                .handle("sess-1", "질문", sink);

        // 카드 이벤트 없이, error 이벤트로 스트림을 끊지도 않고, 되묻기 텍스트로 끝난다
        assertThat(events).containsExactly("text", "done");
        ToolResponseMessage toolMsg = seenPrompts.get(1).getInstructions().stream()
                .filter(ToolResponseMessage.class::isInstance)
                .map(ToolResponseMessage.class::cast)
                .findFirst().orElseThrow();
        assertThat(toolMsg.getResponses().get(0).responseData()).contains("PERIOD_INVERTED");
    }

    @Test
    void stopsAtIterationLimit_insteadOfLoopingForever() {
        AssistantMessage[] endless = new AssistantMessage[8];
        for (int i = 0; i < endless.length; i++) {
            endless[i] = toolCall("call_" + i, "return_gap",
                    "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                    + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}");
        }
        ChatModel model = new ScriptedChatModel(endless);

        chatService(model)
                .handle("sess-1", "질문", sink);

        // 스크립트가 8개뿐이므로 9번째 호출이 일어나면 AssertionError로 터진다 = 상한이 지켜졌다
        assertThat(seenPrompts).hasSize(8);
        assertThat(events).endsWith("done");
    }

    @Test
    void loggingFailure_stillEmitsErrorEvent() {
        // chat_log는 관측성 데이터지 대화의 하드 의존성이 아니다.
        // DB가 죽었을 때(= docker compose 없이 기동한 데모) 사용자가 빈 스트림을 받으면 안 된다.
        JdbcTemplate deadDb = new JdbcTemplate() {
            @Override public int update(String sql, Object... args) {
                throw new DataAccessResourceFailureException("DB 없음");
            }
        };
        ChatLogService dbBackedLogs = new ChatLogService(deadDb);
        ChatModel model = new ScriptedChatModel(new AssistantMessage("답변"));

        new ChatService(model, tools(), dispatcher(), conversations, dbBackedLogs, "gpt-4o", 4096)
                .handle("sess-1", "질문", sink);

        // 로그를 못 남겨도 대화는 정상 완주해야 한다
        assertThat(events).containsExactly("text", "done");
    }

    @Test
    void llmFailure_emitsErrorEvent() {
        ChatModel failing = prompt -> {
            throw new IllegalStateException("HTTP 401 - invalid_api_key");
        };

        new ChatService(failing, tools(), dispatcher(), conversations, logs, "gpt-4o", 4096)
                .handle("sess-1", "질문", sink);

        assertThat(events).containsExactly("error");
    }

    @Test
    void iterationLimitExhausted_sendsExplanatoryTextNotBlank() {
        AssistantMessage[] endless = new AssistantMessage[8];
        for (int i = 0; i < endless.length; i++) {
            endless[i] = toolCall("call_" + i, "return_gap",
                    "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                    + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}");
        }
        List<String> texts = new ArrayList<>();
        EventSink capturing = (event, dataJson) -> {
            events.add(event);
            if (event.equals("text")) texts.add(dataJson);
        };

        chatService(new ScriptedChatModel(endless)).handle("sess-1", "질문", capturing);

        // 카드만 8장 나오고 해설이 빈 문자열이면 사용자는 왜 멈췄는지 알 수 없다
        assertThat(texts).hasSize(1);
        assertThat(texts.get(0)).contains("좁혀");
    }

    @Test
    void stopsCallingLlmOnceReceiverIsGone() {
        AssistantMessage[] endless = new AssistantMessage[8];
        for (int i = 0; i < endless.length; i++) {
            endless[i] = toolCall("call_" + i, "return_gap",
                    "{\"target_a\":\"삼성전자\",\"target_b\":\"코스피\","
                    + "\"from\":\"2026-07-13\",\"to\":\"2026-07-17\"}");
        }
        // 첫 카드를 받은 직후 떠나는 수신자
        EventSink leaving = new EventSink() {
            private boolean open = true;
            @Override public void send(String event, String dataJson) {
                events.add(event);
                open = false;
            }
            @Override public boolean isOpen() { return open; }
        };

        chatService(new ScriptedChatModel(endless)).handle("sess-1", "질문", leaving);

        // 떠난 뒤에도 8회를 다 돌면 남은 LLM·CHECK API 호출이 그대로 비용이 된다
        assertThat(seenPrompts).hasSize(1);
    }

    @Test
    void savesContextWithoutSystemMessage() {
        ChatModel model = new ScriptedChatModel(new AssistantMessage("답변"));

        chatService(model)
                .handle("sess-1", "질문", sink);

        // 시스템 프롬프트는 매 턴 새로 만든다(오늘 날짜가 바뀌므로) — 저장하면 중복 누적된다
        assertThat(savedContext).noneMatch(m ->
                m instanceof org.springframework.ai.chat.messages.SystemMessage);
        assertThat(savedContext).hasSize(2);   // user + assistant
    }
}
