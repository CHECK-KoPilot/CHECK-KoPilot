package com.koscom.kopilot.domain;

import com.koscom.kopilot.checkapi.DailyQuote;

import java.util.ArrayList;
import java.util.List;

/** 모든 지표의 수치 계산. LLM은 절대 계산하지 않는다 — 여기서만 계산한다. */
public final class Calculations {

    public static final int TRADING_DAYS_PER_YEAR = 252;

    private Calculations() {}

    /** 기간수익률(%) = (마지막 종가 / 첫 종가 − 1) × 100 */
    public static double periodReturnPct(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        double first = quotes.get(0).close();
        double last = quotes.get(quotes.size() - 1).close();
        return (last / first - 1) * 100;
    }

    /** 일간수익률 r_i = c_i / c_{i-1} − 1 */
    public static List<Double> dailyReturns(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < quotes.size(); i++) {
            out.add(quotes.get(i).close() / quotes.get(i - 1).close() - 1);
        }
        return out;
    }

    /** 표본표준편차(n−1) */
    public static double sampleStdDev(List<Double> values) {
        if (values.size() < 2) throw new MetricException("DATA_INSUFFICIENT", "표준편차 계산에 최소 2개 수익률 필요");
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double ss = values.stream().mapToDouble(v -> (v - mean) * (v - mean)).sum();
        return Math.sqrt(ss / (values.size() - 1));
    }

    /** 연율화 변동성(%) = 일간수익률 표본표준편차 × √252 × 100 */
    public static double annualizedVolPct(List<DailyQuote> quotes) {
        return sampleStdDev(dailyReturns(quotes)) * Math.sqrt(TRADING_DAYS_PER_YEAR) * 100;
    }

    /** 마지막 n개 종가의 단순이동평균 */
    public static double movingAverage(List<DailyQuote> quotes, int n) {
        requireRows(quotes, n);
        return quotes.subList(quotes.size() - n, quotes.size())
                .stream().mapToDouble(DailyQuote::close).average().orElseThrow();
    }

    private static void requireRows(List<DailyQuote> quotes, int n) {
        if (quotes == null || quotes.size() < n) {
            throw new MetricException("DATA_INSUFFICIENT",
                    "데이터 부족: 최소 " + n + "개 시세 필요 (현재 " + (quotes == null ? 0 : quotes.size()) + "개)");
        }
    }
}
