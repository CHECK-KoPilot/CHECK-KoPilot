package com.koscom.kopilot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.yaml.snakeyaml.Yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * tool 선택 평가셋 — 발표용 정량 지표 "의도 인식 정확도 %"를 산출한다(스펙 6·11절).
 *
 * <p>게이트가 아니라 측정이다. 리포트가 산출물이고, FAIL 케이스를 보고 tool description과
 * 시스템 프롬프트를 튜닝한 뒤 재실행해 회귀를 확인한다.
 *
 * <p>실 LLM을 호출하므로 기본 테스트에서는 건너뛴다. 실행:
 * <pre>
 * docker compose up -d
 * RUN_EVAL=true ./gradlew test --tests '*EvalRunner'   # 키는 check-kopilot/.env
 * cat build/eval-report.txt
 * </pre>
 * CHECK API는 {@code fixture} 프로파일로 우회한다 — 이 평가는 tool 선택만 보고 실행 결과는 보지 않는다.
 */
@SpringBootTest
@ActiveProfiles("fixture")
@EnabledIfEnvironmentVariable(named = "RUN_EVAL", matches = "true")
class EvalRunner {

    /** 회귀 하한. 기준선은 100%지만 표본 1회·temperature 0.2라 흔들림을 감안해 여유를 둔다. */
    private static final double MIN_ACCURACY = 90.0;

    @Autowired ChatModel chatModel;
    @Autowired KopilotTools tools;

    @Value("${spring.ai.openai.chat.options.model}") String model;
    @Value("${spring.ai.openai.chat.options.max-tokens}") int maxTokens;

    private record Result(boolean ok, String expect, String actual, String reason, String question) {}

    @Test
    @SuppressWarnings("unchecked")
    void measureIntentAccuracy() throws Exception {
        ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                .toolCallbacks(tools.build())
                .internalToolExecutionEnabled(false)
                .model(model)
                .maxTokens(maxTokens)
                .build();

        Map<String, Object> root;
        try (var in = new ClassPathResource("eval-cases.yaml").getInputStream()) {
            root = new Yaml().load(in);
        }
        List<Map<String, Object>> cases = (List<Map<String, Object>>) root.get("cases");

        List<Result> results = new ArrayList<>();
        for (int i = 0; i < cases.size(); i++) {
            if (i > 0) pace();
            results.add(runCase(cases.get(i), options));
        }
        report(results);
    }

    /**
     * 케이스 하나가 실패해도 나머지 측정은 이어간다 — 30여 케이스를 다시 돌리는 비용이 크다.
     * 429는 한 번 더 기다렸다 재시도한다(TPM 초과는 시간이 지나면 풀린다).
     */
    private Result runCase(Map<String, Object> c, ToolCallingChatOptions options) {
        try {
            return evaluate(c, options);
        } catch (RuntimeException first) {
            if (!String.valueOf(first.getMessage()).contains("429")) {
                return errorResult(c, first);
            }
            pace();
            pace();
            try {
                return evaluate(c, options);
            } catch (RuntimeException retry) {
                return errorResult(c, retry);
            }
        }
    }

    private Result errorResult(Map<String, Object> c, RuntimeException e) {
        String message = String.valueOf(e.getMessage());
        return new Result(false, (String) c.get("expect"), "ERROR",
                message.length() > 120 ? message.substring(0, 120) : message, (String) c.get("q"));
    }

    /** 조직 TPM 한도(gpt-4o 30k)에 걸리지 않게 호출 간격을 둔다. 케이스마다 tool 스키마 8종을 함께 보낸다. */
    private void pace() {
        try {
            Thread.sleep(Long.parseLong(System.getenv().getOrDefault("EVAL_DELAY_MS", "5000")));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @SuppressWarnings("unchecked")
    private Result evaluate(Map<String, Object> c, ToolCallingChatOptions options) {
        String question = (String) c.get("q");
        String expect = (String) c.get("expect");
        Map<String, Object> expectedParams =
                (Map<String, Object>) c.getOrDefault("params", Map.of());
        Map<String, Object> exactParams =
                (Map<String, Object>) c.getOrDefault("paramsExact", Map.of());
        List<String> history = (List<String>) c.getOrDefault("history", List.of());

        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(SystemPrompt.render(LocalDate.now(ZoneId.of("Asia/Seoul")))));
        // history는 user/assistant가 번갈아 오는 평문 — tool 호출이 없는 대화만 재현한다
        for (int i = 0; i < history.size(); i++) {
            messages.add(i % 2 == 0
                    ? new UserMessage(history.get(i))
                    : new AssistantMessage(history.get(i)));
        }
        messages.add(new UserMessage(question));

        ChatResponse response = chatModel.call(new Prompt(messages, options));
        AssistantMessage out = response.getResult().getOutput();

        String actual = out.hasToolCalls() ? out.getToolCalls().get(0).name() : "no_tool";
        String arguments = out.hasToolCalls() ? String.valueOf(out.getToolCalls().get(0).arguments()) : "";

        // 병렬 호출을 첫 건만 보고 PASS 주면 "덤으로 딸려온 tool"이 집계에서 사라진다
        if (out.hasToolCalls() && out.getToolCalls().size() > 1) {
            String names = out.getToolCalls().stream()
                    .map(AssistantMessage.ToolCall::name).collect(Collectors.joining("+"));
            return new Result(false, expect, names, "tool을 " + out.getToolCalls().size() + "개 동시 호출", question);
        }
        if (!actual.equals(expect)) {
            return new Result(false, expect, actual, "tool 불일치", question);
        }
        // 키를 무시하고 부분 문자열만 보면 target_a/target_b가 뒤바뀌어도 통과한다
        // (return_gap은 A−B라 부호가 뒤집힌다). 키 단위로 값을 꺼내 비교한다.
        JsonNode parsed = parseArguments(arguments);
        for (Map.Entry<String, Object> e : expectedParams.entrySet()) {
            String wanted = String.valueOf(e.getValue());
            String got = parsed.path(e.getKey()).asText("");
            if (!got.contains(wanted)) {
                return new Result(false, expect, actual,
                        "인자 %s = '%s' (기대 '%s') — %s".formatted(e.getKey(), got, wanted, arguments), question);
            }
        }
        // 부분 문자열 비교로는 "모델이 값을 부풀렸는지"를 볼 수 없다 — "삼성전자"는 "삼성"을 포함하므로
        // params로는 종목명 보정 회귀가 영원히 PASS한다. 원문 보존을 봐야 하는 케이스는 정확일치로 비교한다.
        for (Map.Entry<String, Object> e : exactParams.entrySet()) {
            String wanted = String.valueOf(e.getValue());
            String got = parsed.path(e.getKey()).asText("");
            if (!got.equals(wanted)) {
                return new Result(false, expect, actual,
                        "인자 %s = '%s' (정확히 '%s'이어야 함) — %s"
                                .formatted(e.getKey(), got, wanted, arguments), question);
            }
        }
        return new Result(true, expect, actual, "", question);
    }

    private JsonNode parseArguments(String arguments) {
        try {
            return new ObjectMapper().readTree(arguments.isBlank() ? "{}" : arguments);
        } catch (Exception e) {
            return new ObjectMapper().createObjectNode();
        }
    }

    private void report(List<Result> results) throws Exception {
        StringBuilder report = new StringBuilder();
        Map<String, int[]> byExpect = new LinkedHashMap<>();   // expect -> {correct, total}
        int correct = 0;

        for (Result r : results) {
            if (r.ok()) correct++;
            int[] tally = byExpect.computeIfAbsent(r.expect(), k -> new int[2]);
            if (r.ok()) tally[0]++;
            tally[1]++;
            report.append(r.ok() ? "PASS  " : "FAIL  ")
                    .append("expect=").append(r.expect())
                    .append("  actual=").append(r.actual())
                    .append("  q=").append(r.question());
            if (!r.ok()) report.append("  (").append(r.reason()).append(')');
            report.append('\n');
        }

        report.append("\n[유형별]\n");
        byExpect.forEach((expect, t) -> report.append("  %-16s %d/%d (%.0f%%)%n"
                .formatted(expect, t[0], t[1], 100.0 * t[0] / t[1])));

        double accuracy = 100.0 * correct / results.size();
        report.append("\n의도 인식 정확도: %.1f%% (%d/%d)%n"
                .formatted(accuracy, correct, results.size()));

        System.out.print(report);
        Files.createDirectories(Path.of("build"));
        Files.writeString(Path.of("build/eval-report.txt"), report.toString());

        // 리포트가 산출물이지 통과/실패 게이트가 아니다. 다만 조용한 하락은 막는다 —
        // 프롬프트를 고치고 재측정하는 게 이 도구의 용도인데, 늘 초록불이면 알아채지 못한다.
        assertThat(results).isNotEmpty();
        assertThat(accuracy)
                .as("의도 인식 정확도가 하한 %.0f%% 아래로 떨어졌다 — build/eval-report.txt의 FAIL 확인",
                        MIN_ACCURACY)
                .isGreaterThanOrEqualTo(MIN_ACCURACY);
    }
}
