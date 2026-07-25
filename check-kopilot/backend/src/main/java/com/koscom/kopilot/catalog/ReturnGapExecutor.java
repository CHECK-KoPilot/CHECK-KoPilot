package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

public class ReturnGapExecutor implements MetricExecutor {

    private final ExecutorSupport support;

    public ReturnGapExecutor(ExecutorSupport support) {
        this.support = support;
    }

    @Override
    public String toolName() {
        return "return_gap";
    }

    @Override
    public String description() {
        return "두 대상(종목/지수/ETF)의 기간수익률 차이(수익률 갭)를 계산한다. "
                + "예: '삼성전자랑 코스피 최근 한 달 수익률 갭'. 두 대상의 수익률 비교 질문에 사용.";
    }

    @Override
    public Map<String, Object> inputSchemaProperties() {
        return Map.of(
                "target_a", Map.of("type", "string", "description", "첫 번째 대상의 한글 종목명/지수명"),
                "target_b", Map.of("type", "string", "description", "두 번째 대상의 한글 종목명/지수명"),
                "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
                "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override
    public List<String> requiredParams() {
        return List.of("target_a", "target_b", "from", "to");
    }

    @Override
    public MetricResult execute(JsonNode args) {
        StockInfo targetA = support.resolveTarget(support.requiredText(args, "target_a"));
        StockInfo targetB = support.resolveTarget(support.requiredText(args, "target_b"));
        ExecutorSupport.Period period = support.parsePeriod(args);

        List<DailyQuote> quotesA = support.api().dailyQuotes(targetA, period.from(), period.to());
        List<DailyQuote> quotesB = support.api().dailyQuotes(targetB, period.from(), period.to());
        double returnA = Calculations.periodReturnPct(quotesA);
        double returnB = Calculations.periodReturnPct(quotesB);
        double gap = returnA - returnB;

        MetricResult.Evidence evidence = new MetricResult.Evidence(
                List.of(
                        support.apiCall(support.dailyApiId(targetA), "일별 시세 조회", targetA, period),
                        support.apiCall(support.dailyApiId(targetB), "일별 시세 조회", targetB, period)),
                List.of(support.rawRows(targetA.name(), quotesA), support.rawRows(targetB.name(), quotesB)),
                "기간수익률(%) = (기간 마지막 종가 / 기간 첫 종가 - 1) * 100; "
                        + "수익률 갭(%p) = A 기간수익률 - B 기간수익률",
                List.of(
                        new MetricResult.Evidence.Step(
                                targetA.name() + " 기간수익률",
                                "(%s / %s - 1) * 100 = %s%%".formatted(
                                        ExecutorSupport.fmt(last(quotesA).close()),
                                        ExecutorSupport.fmt(first(quotesA).close()),
                                        ExecutorSupport.fmt(returnA))),
                        new MetricResult.Evidence.Step(
                                targetB.name() + " 기간수익률",
                                "(%s / %s - 1) * 100 = %s%%".formatted(
                                        ExecutorSupport.fmt(last(quotesB).close()),
                                        ExecutorSupport.fmt(first(quotesB).close()),
                                        ExecutorSupport.fmt(returnB))),
                        new MetricResult.Evidence.Step(
                                "수익률 갭",
                                "%s - %s = %s%%p".formatted(
                                        ExecutorSupport.fmt(returnA),
                                        ExecutorSupport.fmt(returnB),
                                        ExecutorSupport.fmt(gap)))));

        return new MetricResult(
                MetricResult.newCardId(),
                "RETURN_GAP",
                "%s vs %s 수익률 갭 (%s ~ %s)".formatted(
                        targetA.name(), targetB.name(), period.from(), period.to()),
                period.from(),
                period.to(),
                List.of(
                        new MetricResult.Target(targetA.code(), targetA.name()),
                        new MetricResult.Target(targetB.code(), targetB.name())),
                List.of(
                        new MetricResult.Headline(targetA.name() + " 기간수익률", ExecutorSupport.round4(returnA), "%"),
                        new MetricResult.Headline(targetB.name() + " 기간수익률", ExecutorSupport.round4(returnB), "%"),
                        new MetricResult.Headline("수익률 갭", ExecutorSupport.round4(gap), "%p")),
                new MetricResult.ChartSpec(
                        "line",
                        List.of(
                                support.cumulativeReturnSeries(targetA.name(), quotesA),
                                support.cumulativeReturnSeries(targetB.name(), quotesB))),
                evidence);
    }

    private static DailyQuote first(List<DailyQuote> quotes) {
        return quotes.stream().min(java.util.Comparator.comparing(DailyQuote::date)).orElseThrow();
    }

    private static DailyQuote last(List<DailyQuote> quotes) {
        return quotes.stream().max(java.util.Comparator.comparing(DailyQuote::date)).orElseThrow();
    }
}
