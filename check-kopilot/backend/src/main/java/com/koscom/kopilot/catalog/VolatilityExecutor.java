package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class VolatilityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public VolatilityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "volatility"; }

    @Override public String description() {
        return "대상(1~5개)의 변동성(일간수익률 표준편차의 연율화)을 계산한다. 2개 이상이면 비교. "
             + "예: '에코프로랑 에코프로비엠 변동성 비교', '삼성전자 최근 3개월 변동성'. 변동성·위험도 질문에 사용.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "targets", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "변동성을 계산할 대상들의 한글 종목명/지수명 (1~5개)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("targets", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        JsonNode targetsNode = args.path("targets");
        if (!targetsNode.isArray() || targetsNode.size() < 1 || targetsNode.size() > 5) {
            throw new MetricException("PARAM_INVALID", "targets는 1~5개 대상 배열이어야 합니다");
        }
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<MetricResult.Target> targets = new ArrayList<>();
        List<MetricResult.Headline> headline = new ArrayList<>();
        List<MetricResult.ChartSpec.Point> barPoints = new ArrayList<>();
        List<MetricResult.Evidence.ApiCall> apiCalls = new ArrayList<>();
        List<MetricResult.Evidence.RawSeries> raw = new ArrayList<>();
        List<MetricResult.Evidence.Step> steps = new ArrayList<>();

        for (JsonNode t : targetsNode) {
            StockInfo info = s.resolveTarget(t.asText());
            List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
            double dailyStd = Calculations.sampleStdDev(Calculations.dailyReturns(quotes));
            double annPct = dailyStd * Math.sqrt(Calculations.TRADING_DAYS_PER_YEAR) * 100;

            targets.add(new MetricResult.Target(info.code(), info.name()));
            headline.add(new MetricResult.Headline(info.name() + " 연율화 변동성",
                    ExecutorSupport.round4(annPct), "%"));
            barPoints.add(new MetricResult.ChartSpec.Point(info.name(), ExecutorSupport.round4(annPct)));
            apiCalls.add(s.apiCall(info.isIndex() ? "index-daily" : "stock-daily", "일별 시세 조회", info, p));
            raw.add(s.rawRows(info.name(), quotes));
            steps.add(new MetricResult.Evidence.Step(info.name(),
                    "일간수익률 표준편차 %s × √252 × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(dailyStd), ExecutorSupport.fmt(annPct))));
        }

        return new MetricResult(
            MetricResult.newCardId(), "VOLATILITY",
            "변동성 비교 (%s ~ %s)".formatted(p.from(), p.to()),
            p.from(), p.to(), targets, headline,
            new MetricResult.ChartSpec("bar",
                List.of(new MetricResult.ChartSpec.Series("연율화 변동성(%)", barPoints))),
            new MetricResult.Evidence(apiCalls, raw,
                "연율화 변동성(%) = 일간수익률(cᵢ/cᵢ₋₁ − 1)의 표본표준편차(n−1) × √252 × 100",
                steps));
    }
}