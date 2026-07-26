package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ReturnRankingExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public ReturnRankingExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "return_ranking"; }

    @Override public String description() {
        return "사용자가 나열한 복수 종목(2~10개)의 기간수익률을 계산해 순위를 매긴다. "
             + "예: '에코프로, 엘앤에프, 포스코퓨처엠 3개월 수익률 순위'. "
             + "주의: 테마명·업종명만 있고 종목이 나열되지 않았으면 이 tool을 쓰지 말고 종목을 되물을 것.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "targets", Map.of("type", "array", "items", Map.of("type", "string"),
                    "description", "순위를 매길 종목들의 한글 종목명 (2~10개, 사용자가 직접 나열한 것)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("targets"); }

    @Override public MetricResult execute(JsonNode args) {
        JsonNode targetsNode = args.path("targets");
        if (!targetsNode.isArray() || targetsNode.size() < 2 || targetsNode.size() > 10) {
            throw new MetricException("PARAM_INVALID", "targets는 2~10개 종목 배열이어야 합니다");
        }
        ExecutorSupport.Period p = s.parsePeriodOrRecent(args);

        record Entry(StockInfo info, double ret, List<DailyQuote> quotes) {}
        List<Entry> entries = new ArrayList<>();
        for (JsonNode t : targetsNode) {
            StockInfo info = s.resolveTarget(t.asText());
            List<DailyQuote> quotes = s.api().dailyQuotes(info, p.from(), p.to());
            entries.add(new Entry(info, Calculations.periodReturnPct(quotes), quotes));
        }
        entries.sort(Comparator.comparingDouble(Entry::ret).reversed());

        List<MetricResult.Target> targets = new ArrayList<>();
        List<MetricResult.Headline> headline = new ArrayList<>();
        List<MetricResult.ChartSpec.Point> bars = new ArrayList<>();
        List<MetricResult.Evidence.ApiCall> apiCalls = new ArrayList<>();
        List<MetricResult.Evidence.RawSeries> raw = new ArrayList<>();
        List<MetricResult.Evidence.Step> steps = new ArrayList<>();

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            targets.add(new MetricResult.Target(e.info().code(), e.info().name()));
            headline.add(new MetricResult.Headline(
                    "%d위 %s".formatted(i + 1, e.info().name()), ExecutorSupport.round4(e.ret()), "%"));
            bars.add(new MetricResult.ChartSpec.Point(e.info().name(), ExecutorSupport.round4(e.ret())));
            apiCalls.add(s.apiCall("stock-daily", "일별 시세 조회", e.info(), p));
            raw.add(s.rawRows(e.info().name(), e.quotes()));
            steps.add(new MetricResult.Evidence.Step("%d위 %s".formatted(i + 1, e.info().name()),
                    "(%s / %s − 1) × 100 = %s%%".formatted(
                        ExecutorSupport.fmt(e.quotes().get(e.quotes().size() - 1).close()),
                        ExecutorSupport.fmt(e.quotes().get(0).close()), ExecutorSupport.fmt(e.ret()))));
        }

        return new MetricResult(
            MetricResult.newCardId(), "RETURN_RANKING",
            "기간수익률 랭킹 (%s ~ %s)".formatted(p.from(), p.to()),
            p.from(), p.to(), targets, headline,
            new MetricResult.ChartSpec("bar",
                List.of(new MetricResult.ChartSpec.Series("기간수익률(%)", bars))),
            new MetricResult.Evidence(apiCalls, raw,
                "기간수익률(%) = (기간 마지막 종가 / 기간 첫 종가 − 1) × 100, 내림차순 정렬",
                steps));
    }
}