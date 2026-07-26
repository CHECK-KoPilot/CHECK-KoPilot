package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConversationCodecTest {

    private final ConversationCodec codec = new ConversationCodec();

    @Test
    void roundTripsUserAssistantToolCallAndToolResponse() {
        List<Message> original = List.of(
                new UserMessage("삼성전자랑 코스피 수익률 갭"),
                new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "return_gap",
                                "{\"target_a\":\"삼성전자\"}"))),
                new ToolResponseMessage(List.of(
                        new ToolResponseMessage.ToolResponse("call_1", "return_gap",
                                "{\"status\":\"ok\"}"))),
                new AssistantMessage("삼성전자가 코스피 대비 3.0%p 앞섭니다."));

        List<Message> restored = codec.decode(codec.encode(original));

        assertThat(restored).hasSize(4);
        AssistantMessage withCall = (AssistantMessage) restored.get(1);
        assertThat(withCall.hasToolCalls()).isTrue();
        assertThat(withCall.getToolCalls().get(0).id()).isEqualTo("call_1");
        assertThat(withCall.getToolCalls().get(0).name()).isEqualTo("return_gap");

        ToolResponseMessage toolMsg = (ToolResponseMessage) restored.get(2);
        assertThat(toolMsg.getResponses().get(0).id()).isEqualTo("call_1");
        assertThat(restored.get(3).getText()).contains("3.0%p");
    }

    @Test
    void keepsOnlyRecentTurns() {
        List<Message> many = new java.util.ArrayList<>();
        for (int i = 0; i < 60; i++) many.add(new UserMessage("q" + i));

        String encoded = codec.encode(codec.trimToRecent(many, 20));

        assertThat(codec.decode(encoded)).hasSize(20);
        assertThat(codec.decode(encoded).get(0).getText()).isEqualTo("q40");
    }

    @Test
    void trimNeverStartsWithOrphanToolResult() {
        // user / assistant(tool_use) / tool_result / assistant 4메시지가 한 턴
        List<Message> history = new java.util.ArrayList<>();
        for (int turn = 0; turn < 6; turn++) {
            history.add(new UserMessage("q" + turn));
            history.add(new AssistantMessage("", java.util.Map.of(), List.of(
                    new AssistantMessage.ToolCall("call_" + turn, "function", "return_gap", "{}"))));
            history.add(new ToolResponseMessage(List.of(
                    new ToolResponseMessage.ToolResponse("call_" + turn, "return_gap", "{}"))));
            history.add(new AssistantMessage("a" + turn));
        }

        List<Message> trimmed = codec.trimToRecent(history, 10);

        // 고아 tool 응답으로 시작하면 LLM API가 400으로 거부한다 — 세션이 통째로 죽는다
        assertThat(trimmed.get(0)).isInstanceOf(UserMessage.class);
        assertThat(trimmed).hasSizeLessThanOrEqualTo(10);
    }

    @Test
    void trimDropsDanglingToolCallAtTheEnd() {
        // tool_use로 끝나는 히스토리(루프 중단·클라이언트 이탈)를 저장하면 다음 턴에 400이 난다
        List<Message> history = List.of(
                new UserMessage("q"),
                new AssistantMessage("", java.util.Map.of(), List.of(
                        new AssistantMessage.ToolCall("call_1", "function", "return_gap", "{}"))));

        List<Message> trimmed = codec.trimToRecent(history, 20);

        assertThat(trimmed).hasSize(1);
        assertThat(trimmed.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    void decodeAlwaysReturnsMutableList() {
        // 호출부가 load() 결과에 add 하는 것은 자연스러운 사용법이다 — 첫 턴에 터지면 안 된다
        assertThatCode(() -> codec.decode(null).add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode("").add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode("깨진 json").add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.decode(codec.encode(List.of(new UserMessage("q"))))
                .add(new UserMessage("x"))).doesNotThrowAnyException();
        assertThatCode(() -> codec.trimToRecent(new java.util.ArrayList<>(List.of(new UserMessage("q"))), 20)
                .add(new UserMessage("x"))).doesNotThrowAnyException();
    }
}
