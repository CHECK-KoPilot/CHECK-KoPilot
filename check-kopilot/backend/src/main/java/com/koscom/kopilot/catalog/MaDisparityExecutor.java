package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.Calculations;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class MaDisparityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public MaDisparityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "ma_disparity"; }

    @Override public String description() {
        return "종목의 N일 이동평균선 대비 이격도를 계산한다. 예: '카카오 20일선 이격도'. "
             + "이동평균선·이격도 질문에 사용. window 기본값 20.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description",
                "조회할 대상 — 한글 종목명·약칭." + ExecutorSupport.VERBATIM_TARGET),
            "window", Map.of("type", "integer", "description", "이동평균 일수 (기본 20)"),
            "as_of", Map.of("type", "string", "description", "기준일 YYYY-MM-DD (생략 시 오늘)"));
    }

    @Override public List<String> requiredParams() { return List.of("target"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        int window = args.path("window").asInt(20);
        if (window < 2 || window > 240) throw new MetricException("PARAM_INVALID", "window는 2~240 사이여야 합니다");
        LocalDate asOf = args.hasNonNull("as_of") ? LocalDate.parse(args.get("as_of").asText()) : LocalDate.now();
        if (asOf.isAfter(LocalDate.now())) throw new MetricException("PERIOD_FUTURE", "미래 기준일은 불가: " + asOf);

        // 휴장일 감안해 window의 2배 캘린더 일수 + 14일 여유로 조회
        LocalDate from = asOf.minusDays(window * 2L + 14);
        List<DailyQuote> quotes = s.api().dailyQuotes(info, from, asOf);
        double ma = Calculations.movingAverage(quotes, window);
        double close = quotes.get(quotes.size() - 1).close();
        double disparity = (close / ma - 1) * 100;

        List<DailyQuote> windowQuotes = quotes.subList(Math.max(0, quotes.size() - window), quotes.size());
        return new MetricResult(
            MetricResult.newCardId(), "MA_DISPARITY",
            "%s %d일선 이격도 (기준일 %s)".formatted(info.name(), window, quotes.get(quotes.size() - 1).date()),
            windowQuotes.get(0).date(), quotes.get(quotes.size() - 1).date(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline(window + "일선 이격도", ExecutorSupport.round4(disparity), "%"),
                    new MetricResult.Headline("현재가", close, "원"),
                    new MetricResult.Headline(window + "일 이동평균", ExecutorSupport.round4(ma), "원")),
            new MetricResult.ChartSpec("line", List.of(s.closeSeries(info.name(), windowQuotes))),
            new MetricResult.Evidence(
                List.of(s.apiCall("stock-daily", "일별 시세 조회", info,
                        new ExecutorSupport.Period(from, asOf))),
                List.of(s.rawRows(info.name(), windowQuotes)),
                "이격도(%) = (현재가 / N일 이동평균 − 1) × 100  (양수 = 이평선 위)",
                List.of(
                    new MetricResult.Evidence.Step(window + "일 이동평균",
                        "최근 %d개 종가 평균 = %s".formatted(window, ExecutorSupport.fmt(ma))),
                    new MetricResult.Evidence.Step("이격도",
                        "(%s / %s − 1) × 100 = %s%%".formatted(
                            ExecutorSupport.fmt(close), ExecutorSupport.fmt(ma), ExecutorSupport.fmt(disparity))))));
    }
}