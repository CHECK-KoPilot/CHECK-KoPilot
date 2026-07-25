package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.NavQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.util.List;
import java.util.Map;

public class NavDisparityExecutor implements MetricExecutor {

    private final ExecutorSupport s;

    public NavDisparityExecutor(ExecutorSupport s) { this.s = s; }

    @Override public String toolName() { return "nav_disparity"; }

    @Override public String description() {
        return "ETF의 괴리율(시장가 vs NAV)을 계산한다. 예: 'TIGER 미국S&P500 괴리율'. "
             + "ETF 전용 지표 — 일반 주식/지수에는 사용 불가.";
    }

    @Override public Map<String, Object> inputSchemaProperties() {
        return Map.of(
            "target", Map.of("type", "string", "description", "ETF의 한글 상품명 (예: TIGER 미국S&P500)"),
            "from", Map.of("type", "string", "description", "조회 시작일 YYYY-MM-DD"),
            "to", Map.of("type", "string", "description", "조회 종료일 YYYY-MM-DD"));
    }

    @Override public List<String> requiredParams() { return List.of("target", "from", "to"); }

    @Override public MetricResult execute(JsonNode args) {
        StockInfo info = s.resolveTarget(s.requiredText(args, "target"));
        if (!info.isEtf()) {
            throw new MetricException("NOT_ETF",
                    info.name() + "은(는) ETF가 아닙니다. 괴리율은 ETF 전용 지표입니다.");
        }
        ExecutorSupport.Period p = s.parsePeriod(args);

        List<NavQuote> navs = s.api().etfNav(info, p.from(), p.to());
        if (navs.isEmpty()) throw new MetricException("DATA_INSUFFICIENT", "NAV 데이터가 없습니다");

        List<MetricResult.ChartSpec.Point> series = navs.stream()
                .map(n -> new MetricResult.ChartSpec.Point(n.date().toString(), disparityPct(n))).toList();
        NavQuote latest = navs.get(navs.size() - 1);
        double latestDisp = disparityPct(latest);
        double avgDisp = navs.stream().mapToDouble(NavDisparityExecutor::disparityPct).average().orElseThrow();

        return new MetricResult(
            MetricResult.newCardId(), "NAV_DISPARITY",
            "%s 괴리율 (%s ~ %s)".formatted(info.name(), p.from(), p.to()),
            p.from(), p.to(),
            List.of(new MetricResult.Target(info.code(), info.name())),
            List.of(new MetricResult.Headline("최신 괴리율", ExecutorSupport.round4(latestDisp), "%"),
                    new MetricResult.Headline("기간 평균 괴리율", ExecutorSupport.round4(avgDisp), "%")),
            new MetricResult.ChartSpec("line",
                List.of(new MetricResult.ChartSpec.Series("괴리율(%)", series))),
            new MetricResult.Evidence(
                List.of(s.apiCall("etf-nav", "ETF NAV 조회", info, p)),
                List.of(new MetricResult.Evidence.RawSeries(info.name() + " 시장가",
                            navs.stream().map(n -> new MetricResult.Evidence.Row(n.date(), n.marketPrice())).toList()),
                        new MetricResult.Evidence.RawSeries(info.name() + " NAV",
                            navs.stream().map(n -> new MetricResult.Evidence.Row(n.date(), n.nav())).toList())),
                "괴리율(%) = (시장가 − NAV) / NAV × 100",
                List.of(new MetricResult.Evidence.Step("최신 괴리율 (" + latest.date() + ")",
                        "(%s − %s) / %s × 100 = %s%%".formatted(
                                ExecutorSupport.fmt(latest.marketPrice()), ExecutorSupport.fmt(latest.nav()),
                                ExecutorSupport.fmt(latest.nav()), ExecutorSupport.fmt(latestDisp))))));
    }

    static double disparityPct(NavQuote n) { return (n.marketPrice() - n.nav()) / n.nav() * 100; }
}