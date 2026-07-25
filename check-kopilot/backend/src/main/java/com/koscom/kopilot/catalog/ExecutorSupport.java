package com.koscom.kopilot.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.koscom.kopilot.checkapi.CheckApiClient;
import com.koscom.kopilot.checkapi.DailyQuote;
import com.koscom.kopilot.checkapi.StockInfo;
import com.koscom.kopilot.checkapi.StockResolver;
import com.koscom.kopilot.domain.MetricException;
import com.koscom.kopilot.domain.MetricResult;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** 실행기 공통 의존성과 검증/변환 헬퍼. */
public class ExecutorSupport {

    private static final Map<String, String> SPEC_URLS = Map.of(
            "stock-daily", "https://checkapi.koscom.co.kr/docs/stock-daily",
            "index-daily", "https://checkapi.koscom.co.kr/docs/index-daily",
            "kosdaq-daily", "https://checkapi.koscom.co.kr/docs/kosdaq-daily",
            "stock-investor", "https://checkapi.koscom.co.kr/docs/stock-investor");

    private final CheckApiClient checkApi;
    private final StockResolver stocks;

    public ExecutorSupport(CheckApiClient checkApi, StockResolver stocks) {
        this.checkApi = checkApi;
        this.stocks = stocks;
    }

    public CheckApiClient api() {
        return checkApi;
    }

    public StockInfo resolveTarget(String nameOrCode) {
        return stocks.resolve(nameOrCode);
    }

    public Period parsePeriod(JsonNode args) {
        String rawFrom = args.path("from").asText(null);
        String rawTo = args.path("to").asText(null);
        if (rawFrom == null || rawTo == null) {
            throw new MetricException("PERIOD_MISSING", "from/to 날짜가 필요합니다 (YYYY-MM-DD)");
        }

        LocalDate from;
        LocalDate to;
        try {
            from = LocalDate.parse(rawFrom);
            to = LocalDate.parse(rawTo);
        } catch (RuntimeException e) {
            throw new MetricException("PERIOD_INVALID", "날짜 형식 오류: " + rawFrom + " ~ " + rawTo);
        }

        if (from.isAfter(to)) {
            throw new MetricException("PERIOD_INVERTED", "시작일이 종료일보다 늦습니다: " + from + " > " + to);
        }
        if (to.isAfter(LocalDate.now())) {
            throw new MetricException("PERIOD_FUTURE", "미래 날짜는 조회할 수 없습니다: " + to);
        }
        return new Period(from, to);
    }

    public String requiredText(JsonNode args, String field) {
        String value = args.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new MetricException("PARAM_MISSING", "필수 파라미터 누락: " + field);
        }
        return value;
    }

    public MetricResult.ChartSpec.Series cumulativeReturnSeries(String name, List<DailyQuote> quotes) {
        List<DailyQuote> sorted = sortedQuotes(quotes);
        if (sorted.isEmpty()) {
            throw new MetricException("DATA_INSUFFICIENT", "누적수익률 계산에 시세 데이터가 필요합니다");
        }
        double base = sorted.get(0).close();
        if (base <= 0) {
            throw new MetricException("DATA_INVALID", "유효하지 않은 가격(0 이하)이 포함되어 계산할 수 없습니다");
        }
        List<MetricResult.ChartSpec.Point> points = sorted.stream()
                .map(quote -> new MetricResult.ChartSpec.Point(
                        quote.date().toString(), round4((quote.close() / base - 1) * 100)))
                .toList();
        return new MetricResult.ChartSpec.Series(name, points);
    }

    public MetricResult.ChartSpec.Series closeSeries(String name, List<DailyQuote> quotes) {
        List<MetricResult.ChartSpec.Point> points = sortedQuotes(quotes).stream()
                .map(quote -> new MetricResult.ChartSpec.Point(quote.date().toString(), quote.close()))
                .toList();
        return new MetricResult.ChartSpec.Series(name, points);
    }

    public MetricResult.Evidence.RawSeries rawRows(String name, List<DailyQuote> quotes) {
        List<MetricResult.Evidence.Row> rows = sortedQuotes(quotes).stream()
                .map(quote -> new MetricResult.Evidence.Row(quote.date(), quote.close()))
                .toList();
        if (rows.size() > 60) {
            rows = rows.subList(rows.size() - 60, rows.size());
        }
        return new MetricResult.Evidence.RawSeries(name, rows);
    }

    public MetricResult.Evidence.ApiCall apiCall(String apiId, String apiName, StockInfo instrument, Period period) {
        return new MetricResult.Evidence.ApiCall(
                apiName,
                "%s(%s) %s ~ %s".formatted(instrument.name(), instrument.code(), period.from(), period.to()),
                specUrl(apiId));
    }

    public String dailyApiId(StockInfo instrument) {
        if (instrument.isIndex()) {
            return "index-daily";
        }
        if ("KOSDAQ".equals(instrument.market())) {
            return "kosdaq-daily";
        }
        return "stock-daily";
    }

    public String specUrl(String apiId) {
        return SPEC_URLS.getOrDefault(apiId, "");
    }

    public static double round4(double value) {
        return Math.round(value * 10000.0) / 10000.0;
    }

    public static String fmt(double value) {
        return "%,.4f".formatted(value);
    }

    private static List<DailyQuote> sortedQuotes(List<DailyQuote> quotes) {
        if (quotes == null) {
            return List.of();
        }
        return quotes.stream().sorted(Comparator.comparing(DailyQuote::date)).toList();
    }

    public record Period(LocalDate from, LocalDate to) {
    }
}
