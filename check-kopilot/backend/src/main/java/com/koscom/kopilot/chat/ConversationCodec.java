package com.koscom.kopilot.chat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Spring AI Message와 안정적인 JSON DTO 사이의 변환기.
 *
 * <p>Spring AI 구현체를 직접 직렬화하지 않는다. 라이브러리 내부 구조 변경에 저장 포맷이
 * 끌려가지 않게 하기 위함이다. Anthropic tool_use/tool_result 매칭에 필요한 id는 보존한다.</p>
 */
public class ConversationCodec {

    public record Turn(String role, String text, List<Call> calls, List<Result> results) {
        public record Call(String id, String name, String arguments) {
        }

        public record Result(String id, String name, String data) {
        }
    }

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 최근 max개 메시지만 남기되 tool 호출 짝을 깨뜨리지 않는다.
     * Anthropic은 대응하는 tool_use 없는 tool_result(또는 그 반대)를 400으로 거부하므로,
     * "히스토리는 항상 UserMessage로 시작하고 tool_use로 끝나지 않는다"를 불변식으로 강제한다.
     */
    public List<Message> trimToRecent(List<Message> messages, int max) {
        if (messages == null || messages.isEmpty() || max <= 0) {
            return new ArrayList<>();
        }

        List<Message> window = messages.size() <= max
                ? new ArrayList<>(messages)
                : new ArrayList<>(messages.subList(messages.size() - max, messages.size()));

        // 앞: 첫 UserMessage 전까지 버린다 (고아 tool_result·assistant 제거)
        int start = 0;
        while (start < window.size() && !(window.get(start) instanceof UserMessage)) {
            start++;
        }
        List<Message> trimmed = new ArrayList<>(window.subList(start, window.size()));

        // 뒤: 응답 없는 tool_use로 끝나면 그 assistant까지 버린다
        while (!trimmed.isEmpty()) {
            Message last = trimmed.get(trimmed.size() - 1);
            if (last instanceof AssistantMessage assistant && assistant.hasToolCalls()) {
                trimmed.remove(trimmed.size() - 1);
                continue;
            }
            break;
        }
        return trimmed;
    }

    public String encode(List<Message> messages) {
        List<Turn> turns = new ArrayList<>();
        for (Message message : safeMessages(messages)) {
            if (message instanceof UserMessage user) {
                turns.add(new Turn("user", nullToEmpty(user.getText()), List.of(), List.of()));
            } else if (message instanceof AssistantMessage assistant) {
                List<Turn.Call> calls = assistant.hasToolCalls()
                        ? assistant.getToolCalls().stream()
                        .map(call -> new Turn.Call(call.id(), call.name(), call.arguments()))
                        .toList()
                        : List.of();
                turns.add(new Turn("assistant", nullToEmpty(assistant.getText()), calls, List.of()));
            } else if (message instanceof ToolResponseMessage toolResponse) {
                List<Turn.Result> results = toolResponse.getResponses().stream()
                        .map(response -> new Turn.Result(response.id(), response.name(), response.responseData()))
                        .toList();
                turns.add(new Turn("tool", "", List.of(), results));
            }
        }

        try {
            return mapper.writeValueAsString(turns);
        } catch (Exception e) {
            throw new IllegalStateException("대화 컨텍스트 직렬화 실패", e);
        }
    }

    public List<Message> decode(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }

        List<Turn> turns;
        try {
            turns = mapper.readValue(json, new TypeReference<List<Turn>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }

        List<Message> messages = new ArrayList<>();
        for (Turn turn : turns) {
            if (turn == null || turn.role() == null) {
                continue;
            }

            switch (turn.role()) {
                case "user" -> messages.add(new UserMessage(nullToEmpty(turn.text())));
                case "assistant" -> {
                    List<AssistantMessage.ToolCall> calls = safeCalls(turn).stream()
                            .map(call -> new AssistantMessage.ToolCall(
                                    call.id(), "function", call.name(), nullToEmpty(call.arguments())))
                            .toList();
                    messages.add(new AssistantMessage(nullToEmpty(turn.text()), Map.of(), calls));
                }
                case "tool" -> messages.add(new ToolResponseMessage(safeResults(turn).stream()
                        .map(result -> new ToolResponseMessage.ToolResponse(
                                result.id(), result.name(), nullToEmpty(result.data())))
                        .toList()));
                default -> {
                    // Unknown role from a future format: ignore it and keep usable context.
                }
            }
        }
        return messages;
    }

    private List<Message> safeMessages(List<Message> messages) {
        return messages == null ? List.of() : messages;
    }

    private List<Turn.Call> safeCalls(Turn turn) {
        return turn.calls() == null ? List.of() : turn.calls();
    }

    private List<Turn.Result> safeResults(Turn turn) {
        return turn.results() == null ? List.of() : turn.results();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
