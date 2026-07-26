package com.koscom.kopilot.chat;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프롬프트 품질 자체는 실 LLM 호출로만 확인할 수 있다(수동 검증).
 * 여기서는 실측으로 확인된 동작을 떠받치는 조항이 실수로 사라지지 않게 고정한다.
 */
class SystemPromptTest {

    private final String prompt = SystemPrompt.render(LocalDate.of(2026, 7, 26));

    @Test
    void injectsTodayForRelativePeriods() {
        assertThat(prompt).contains("2026-07-26");
    }

    @Test
    void tellsModelToInheritConditionsFromEarlierTurns() {
        // 이 조항이 없으면 "현대차는?" 같은 후속 질문에 매번 기간을 되묻는다(실측 확인).
        // 되묻기 조항보다 먼저 와야 우선순위가 뒤집히지 않는다.
        assertThat(prompt).contains("이어받아");
        assertThat(prompt.indexOf("[대화 이어가기"))
                .isLessThan(prompt.indexOf("[되묻기"));
    }

    @Test
    void stillRefusesToGuessWhenNothingToInherit() {
        assertThat(prompt).contains("이어받을 것이 없으면");
    }

    @Test
    void keepsComplianceGuardrail() {
        assertThat(prompt).contains("투자 판단·권유·전망");
        assertThat(prompt).contains("수치 계산은 절대 직접 하지 않는다");
    }
}
