package com.koscom.kopilot.domain;

import com.koscom.kopilot.checkapi.DailyQuote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 모든 지표의 수치 계산. LLM은 절대 계산하지 않는다 — 여기서만 계산한다. */
public final class Calculations {

    public static final int TRADING_DAYS_PER_YEAR = 252;

    private Calculations() {}

    /** 기간수익률(%) = (마지막 종가 / 첫 종가 − 1) × 100. 입력은 날짜 오름차순으로 방어적 정렬 후 계산한다. */
    public static double periodReturnPct(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        List<DailyQuote> sorted = sortedByDate(quotes);
        double first = sorted.get(0).close();
        double last = sorted.get(sorted.size() - 1).close();
        requirePositiveClose(first);
        requirePositiveClose(last);
        return (last / first - 1) * 100;
    }

    /** 일간수익률 r_i = c_i / c_{i-1} − 1. 입력은 날짜 오름차순으로 방어적 정렬 후 계산한다. */
    public static List<Double> dailyReturns(List<DailyQuote> quotes) {
        requireRows(quotes, 2);
        List<DailyQuote> sorted = sortedByDate(quotes);
        List<Double> out = new ArrayList<>();
        for (int i = 1; i < sorted.size(); i++) {
            double prev = sorted.get(i - 1).close();
            double curr = sorted.get(i).close();
            requirePositiveClose(prev);
            requirePositiveClose(curr);
            out.add(curr / prev - 1);
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

    /** 마지막 n개 종가의 단순이동평균. 입력은 날짜 오름차순으로 방어적 정렬 후 뒤에서 n개를 취한다. */
    public static double movingAverage(List<DailyQuote> quotes, int n) {
        requireRows(quotes, n);
        List<DailyQuote> sorted = sortedByDate(quotes);
        return sorted.subList(sorted.size() - n, sorted.size())
                .stream().mapToDouble(DailyQuote::close).average().orElseThrow();
    }

    private static void requireRows(List<DailyQuote> quotes, int n) {
        if (quotes == null || quotes.size() < n) {
            throw new MetricException("DATA_INSUFFICIENT",
                    "데이터 부족: 최소 " + n + "개 시세 필요 (현재 " + (quotes == null ? 0 : quotes.size()) + "개)");
        }
    }

    /** 원본 리스트를 변형하지 않고 날짜 오름차순으로 정렬한 복사본을 반환한다. */
    private static List<DailyQuote> sortedByDate(List<DailyQuote> quotes) {
        return quotes.stream().sorted(Comparator.comparing(DailyQuote::date)).toList();
    }

    private static void requirePositiveClose(double close) {
        if (close <= 0) {
            throw new MetricException("DATA_INVALID", "유효하지 않은 가격(0 이하)이 포함되어 계산할 수 없습니다");
        }
    }
}
