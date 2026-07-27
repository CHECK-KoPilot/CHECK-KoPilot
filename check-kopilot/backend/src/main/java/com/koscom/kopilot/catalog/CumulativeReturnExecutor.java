package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricResult;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 한 대상의 누적수익률 추이.
 *
 * <p>누적수익률 라인을 그리는 실행기가 {@link ReturnGapExecutor} 하나뿐이었고 그건 대상을 둘 요구해서,
 * "네이버 3개월 누적수익률 차트"에 LLM이 비교 종목을 되물었다(이슈 #60). 단일 대상 경로인
 * {@link PeriodSummaryExecutor}는 기간수익률 수치는 주지만 차트는 종가 추이다.
 *
 * <p>계산 자체는 {@code ExecutorSupport.cumulativeReturnSeries}에 이미 있다 — 수익률 갭이
 * 좌·우 대상에 한 번씩 부르던 것을 여기서는 한 번만 부른다.
 */
public class CumulativeReturnExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public CumulativeReturnExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "cumulative_return"; }

    @Override public String description() {
        return "한 대상(종목/지수/ETF)의 기간 누적수익률 추이를 계산해 차트로 낸다. "
             + "예: '네이버 최근 3개월 누적수익률 차트로 보여줘'. 대상이 하나일 때 쓴다 — "
             + "둘을 비교하면 return_gap, 셋 이상을 줄 세우면 return_ranking을 쓸 것.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "대상의 한글 종목명/지수명"),
            "from", Map.of("type", "string",
                "description", "조회 시작일 YYYY-MM-DD (생략 시 최근 "
                    + ExecutorSupport.DEFAULT_PERIOD_DAYS + "일)"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD (생략 시 오늘)"));
    }

    @Override public List<String> requiredParams() { return List.of("target"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        ExecutorSupport.Period p = s.parsePeriodOrRecent(args);

        List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
        double totalReturn = Calculations.periodReturnPct(quotes);

        // 차트 시리즈가 곧 일별 누적수익률이라, 최고/최저는 그 점들에서 그대로 읽는다.
        // 따로 계산하면 차트와 헤드라인이 어긋날 수 있다.
        MetricResult.ChartSpec.Series series = s.cumulativeReturnSeries(info.name(), quotes);
        MetricResult.ChartSpec.Point peak = series.points().stream()
                .max(Comparator.comparingDouble(MetricResult.ChartSpec.Point::value)).orElseThrow();
        MetricResult.ChartSpec.Point trough = series.points().stream()
                .min(Comparator.comparingDouble(MetricResult.ChartSpec.Point::value)).orElseThrow();

        DailyQuote base = quotes.stream().min(Comparator.comparing(DailyQuote::date)).orElseThrow();
        DailyQuote last = quotes.stream().max(Comparator.comparing(DailyQuote::date)).orElseThrow();

        return new MetricResult(
            MetricResult.newCardId(), "CUMULATIVE_RETURN",
            "%s 누적수익률 (%s ~ %s)".formatted(info.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline("기간 누적수익률", ExecutorSupport.round4(totalReturn), "%"),
                    new MetricResult.Headline("최고 누적수익률", peak.value(), "%"),
                    new MetricResult.Headline("최저 누적수익률", trough.value(), "%")),
            new MetricResult.ChartSpec("line", List.of(series)),
            new MetricResult.Evidence(
                List.of(s.apiCall(s.dailyApiId(info), "일별 시세 조회", info, p)),
                List.of(s.rawRows(info.name(), quotes)),
                "누적수익률(%) = (해당일 종가 / 기간 첫 종가 − 1) × 100",
                List.of(
                    new MetricResult.Evidence.Step("기준 종가 (" + base.date() + ")",
                        ExecutorSupport.fmt(base.close())),
                    new MetricResult.Evidence.Step("기간 누적수익률",
                        "(%s / %s − 1) × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(last.close()), ExecutorSupport.fmt(base.close()),
                            ExecutorSupport.fmt(totalReturn))),
                    new MetricResult.Evidence.Step("최고 누적수익률",
                        "%s%% (%s)".formatted(ExecutorSupport.fmt(peak.value()), peak.label())),
                    new MetricResult.Evidence.Step("최저 누적수익률",
                        "%s%% (%s)".formatted(ExecutorSupport.fmt(trough.value()), trough.label())))));
    }
}
