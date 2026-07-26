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
    void asksBackOnlyWhenTargetIsMissing() {
        // 되묻는 조건은 "대상 없음" 하나여야 한다. 기간에도 되묻게 두면 이어가기 규칙과 정면으로 부딪혀
        // 같은 질문이 실행마다 되묻기/호출을 오간다(실제로 그랬다).
        assertThat(prompt).contains("대상(종목·ETF)이 질문에도 앞선 대화에도 없으면");
        assertThat(prompt).contains("기간은 되묻지 않는다");
    }

    @Test
    void keepsComplianceGuardrail() {
        assertThat(prompt).contains("투자 판단·권유·전망");
        assertThat(prompt).contains("수치 계산은 절대 직접 하지 않는다");
    }

    @Test
    void teachesCheckApiCallConventionForRecipes() {
        // 레시피에 GET·쿼리스트링 예시가 나가면 사용자가 그대로 따라 하다 인증 실패한다
        // (CHECK API는 POST 전용, 인증정보는 JSON body — 실호출로 확정된 규약)
        assertThat(prompt).contains("POST 전용");
        assertThat(prompt).contains("JSON body");
        assertThat(prompt).contains("data_list");
    }

    @Test
    void asksForOneToolCallWithGroupedTargets() {
        // 종목마다 tool을 나눠 부르면 단일 종목 카드가 여러 장 나오고 비교가 빠진다(평가셋에서 실측).
        assertThat(prompt).contains("한 번에 tool은 하나만 호출한다");
        assertThat(prompt).contains("targets 배열에 함께 넣어");
    }
}
