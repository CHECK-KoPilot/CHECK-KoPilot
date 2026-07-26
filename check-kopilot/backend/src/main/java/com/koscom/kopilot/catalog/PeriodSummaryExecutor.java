package com.koscom.kopilot.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricResult;

public class PeriodSummaryExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public PeriodSummaryExecutor(ExecutorSupport s) { this.s = s; }

    @Override 
    public String toolName() { return "period_summary"; }

    @Override 
    public String description() {
        return "한 대상의 기간 시세 요약(기간 최고가/최저가/기간수익률)을 계산한다. "
             + "예: '현대차 올해 최고가·최저가·수익률'. 특정 종목의 기간 시세 집계 질문에 사용.";
    }

    @Override 
    public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "대상의 한글 종목명/지수명"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override 
    public List<String> requiredParams() { return List.of("target", "from", "to"); }

    @Override 
    public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
        DailyQuote maxHigh = quotes.stream().max(Comparator.comparingDouble(DailyQuote::high)).orElseThrow();
        DailyQuote minLow = quotes.stream().min(Comparator.comparingDouble(DailyQuote::low)).orElseThrow();
        double ret = Calculations.periodReturnPct(quotes);

        return new MetricResult(
            MetricResult.newCardId(), "PERIOD_SUMMARY",
            "%s 기간 시세 요약 (%s ~ %s)".formatted(info.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline("기간 최고가", maxHigh.high(), "원"),
                    new MetricResult.Headline("기간 최저가", minLow.low(), "원"),
                    new MetricResult.Headline("기간수익률", ExecutorSupport.round4(ret), "%")),
            new MetricResult.ChartSpec("line", List.of(s.closeSeries(info.name(), quotes))),
            new MetricResult.Evidence(
                List.of(s.apiCall(s.dailyApiId(info), "일별 시세 조회", info, p)),
                List.of(s.rawRows(info.name(), quotes)),
                "기간 최고가 = max(일별 고가), 기간 최저가 = min(일별 저가), 기간수익률(%) = (마지막 종가/첫 종가 − 1)×100",
                List.of(
                    new MetricResult.Evidence.Step("기간 최고가", "%s (%s)".formatted(
                            ExecutorSupport.fmt(maxHigh.high()), maxHigh.date())),
                    new MetricResult.Evidence.Step("기간 최저가", "%s (%s)".formatted(
                            ExecutorSupport.fmt(minLow.low()), minLow.date())),
                    new MetricResult.Evidence.Step("기간수익률", "(%s / %s − 1) × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(quotes.get(quotes.size() - 1).close()),
                            ExecutorSupport.fmt(quotes.get(0).close()), ExecutorSupport.fmt(ret))))));
    }
}