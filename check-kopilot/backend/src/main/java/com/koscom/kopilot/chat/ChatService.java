package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring AI 수동 tool 루프.
 * 자동 tool 실행을 끄는 이유: tool 실행 도중 SSE로 카드를 밀어내야 하는데 자동 실행은 그 훅 지점을 주지 않는다.
 */
@Service
public class ChatService {

    private static final int MAX_TOOL_ITERATIONS = 8;

    private final ChatModel chatModel;
    private final KopilotTools tools;
    private final ToolDispatcher dispatcher;
    private final ConversationStore conversations;
    private final ChatLogService logs;
    private final String model;
    private final int maxTokens;
    private final ObjectMapper mapper = new ObjectMapper();

    public ChatService(ChatModel chatModel, KopilotTools tools, ToolDispatcher dispatcher,
                       ConversationStore conversations, ChatLogService logs,
                       @Value("${spring.ai.openai.chat.options.model}") String model,
                       @Value("${spring.ai.openai.chat.options.max-tokens}") int maxTokens) {
        this.chatModel = chatModel;
        this.tools = tools;
        this.dispatcher = dispatcher;
        this.conversations = conversations;
        this.logs = logs;
        this.model = model;
        this.maxTokens = maxTokens;
    }

    public void handle(String sessionId, String userMessage, EventSink sink) {
        try {
            logs.log(sessionId, "user", null, userMessage);

            // 대화 컨텍스트: Redis에서 복원 → 이번 턴 진행 → 종료 시 통째로 저장
            List<Message> messages = new ArrayList<>();
            messages.add(new SystemMessage(SystemPrompt.render(LocalDate.now())));
            messages.addAll(conversations.load(sessionId));
            messages.add(new UserMessage(userMessage));

            // 핵심: 자동 tool 실행 OFF — tool 호출은 아래 루프가 직접 디스패치한다.
            // 모델·토큰은 yml 병합에 기대지 않고 빌더에 직접 싣되, 값은 yml을 단일 출처로 읽는다.
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(tools.build())
                    .internalToolExecutionEnabled(false)
                    .model(model)
                    .maxTokens(maxTokens)
                    .build();

            String finalText = "";
            for (int iteration = 0; iteration < MAX_TOOL_ITERATIONS; iteration++) {
                ChatResponse response = chatModel.call(new Prompt(messages, options));
                AssistantMessage assistant = response.getResult().getOutput();
                messages.add(assistant);

                if (assistant.getText() != null && !assistant.getText().isBlank()) {
                    finalText = assistant.getText();
                }
                if (!assistant.hasToolCalls()) break;      // tool 호출 없음 → 최종 답변

                List<ToolResponseMessage.ToolResponse> toolResponses = new ArrayList<>();
                for (AssistantMessage.ToolCall call : assistant.getToolCalls()) {
                    DispatchResult r = runTool(sessionId, call.name(), call.arguments(), sink);
                    toolResponses.add(new ToolResponseMessage.ToolResponse(
                            call.id(), call.name(), r.toolResultJson()));
                }
                messages.add(new ToolResponseMessage(toolResponses));
            }

            conversations.save(sessionId, messages.subList(1, messages.size()));  // system 제외
            logs.log(sessionId, "assistant", null, finalText);
            sink.send("text", mapper.createObjectNode().put("text", finalText).toString());
            sink.send("done", "{}");
        } catch (Exception e) {
            logs.log(sessionId, "error", null, String.valueOf(e));
            sink.send("error", mapper.createObjectNode()
                    .put("message", "요청 처리에 실패했습니다. 다시 시도해 주세요.").toString());
        }
    }

    /** tool 실행 실패는 스트림을 끊지 않는다 — 구조화 에러로 되돌려 LLM이 자연어로 되묻게 한다. */
    private DispatchResult runTool(String sessionId, String name, String argumentsJson, EventSink sink) {
        try {
            String raw = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            JsonNode args = mapper.readTree(raw);
            logs.log(sessionId, "tool_call", name, args.toString());
            DispatchResult r = dispatcher.dispatch(sessionId, name, args);
            logs.log(sessionId, "tool_result", name, r.toolResultJson());
            if (r.push() != null) sink.send(r.push().event(), r.push().dataJson());
            return r;
        } catch (Exception e) {
            logs.log(sessionId, "error", name, String.valueOf(e));
            return new DispatchResult(
                    "{\"status\":\"error\",\"code\":\"INTERNAL\",\"message\":\"tool 실행 실패\"}",
                    true, null);
        }
    }
}
