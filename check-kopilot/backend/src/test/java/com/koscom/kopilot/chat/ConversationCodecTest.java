package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
}
