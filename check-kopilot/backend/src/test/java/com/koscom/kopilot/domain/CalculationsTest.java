package com.koscom.kopilot.domain;

import com.koscom.kopilot.checkapi.DailyQuote;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.assertj.core.api.Assertions.within;

class CalculationsTest {

    private static DailyQuote q(String date, double close) {
        return new DailyQuote(LocalDate.parse(date), close, close, close, close, 100);
    }

    // 기대값 산출 근거(엑셀로 재검증 가능):
    // closes 100→105 : (105/100 − 1)×100 = 5.0%
    @Test
    void periodReturnPct() {
        List<DailyQuote> quotes = List.of(q("2026-07-14", 100), q("2026-07-15", 102), q("2026-07-17", 105));
        assertThat(Calculations.periodReturnPct(quotes)).isCloseTo(5.0, within(1e-9));
    }

    @Test
    void periodReturn_requiresAtLeastTwoRows() {
        assertThatThrownBy(() -> Calculations.periodReturnPct(List.of(q("2026-07-14", 100))))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }

    // closes 100,110,99,108.9 → 일간수익률 {+10%, −10%, +10%}
    // 표본표준편차 = sqrt(1/75) = 0.1154700538…, 연율화 = sqrt(252/75) = 1.8330302…
    @Test
    void annualizedVolatility() {
        List<DailyQuote> quotes = List.of(
                q("2026-07-14", 100), q("2026-07-15", 110),
                q("2026-07-16", 99), q("2026-07-17", 108.9));
        List<Double> dailyReturns = Calculations.dailyReturns(quotes);
        assertThat(dailyReturns.get(0)).isCloseTo(0.10, within(1e-9));
        assertThat(dailyReturns.get(1)).isCloseTo(-0.10, within(1e-9));
        assertThat(dailyReturns.get(2)).isCloseTo(0.10, within(1e-9));
        assertThat(Calculations.annualizedVolPct(quotes)).isCloseTo(183.30303, within(0.001));
    }

    // closes 100,101,102,103,104 → MA5 = 102.0
    @Test
    void movingAverage() {
        List<DailyQuote> quotes = List.of(
                q("2026-07-13", 100), q("2026-07-14", 101), q("2026-07-15", 102),
                q("2026-07-16", 103), q("2026-07-17", 104));
        assertThat(Calculations.movingAverage(quotes, 5)).isCloseTo(102.0, within(1e-9));
    }

    @Test
    void movingAverage_requiresEnoughRows() {
        assertThatThrownBy(() -> Calculations.movingAverage(List.of(q("2026-07-17", 100)), 5))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }

    // 회귀: 날짜 내림차순으로 입력해도 오름차순 입력과 동일한 결과가 나와야 한다 (부호 뒤집힘 방지).
    @Test
    void periodReturnPct_isOrderIndependent() {
        List<DailyQuote> ascending = List.of(
                q("2026-07-14", 100), q("2026-07-15", 102), q("2026-07-17", 105));
        List<DailyQuote> descending = List.of(
                q("2026-07-17", 105), q("2026-07-15", 102), q("2026-07-14", 100));

        double expected = Calculations.periodReturnPct(ascending);
        assertThat(expected).isCloseTo(5.0, within(1e-9));
        assertThat(Calculations.periodReturnPct(descending)).isCloseTo(expected, within(1e-9));
    }

    @Test
    void dailyReturns_isOrderIndependent() {
        List<DailyQuote> ascending = List.of(
                q("2026-07-14", 100), q("2026-07-15", 110),
                q("2026-07-16", 99), q("2026-07-17", 108.9));
        List<DailyQuote> descending = List.of(
                q("2026-07-17", 108.9), q("2026-07-16", 99),
                q("2026-07-15", 110), q("2026-07-14", 100));

        List<Double> fromDescending = Calculations.dailyReturns(descending);
        assertThat(fromDescending.get(0)).isCloseTo(0.10, within(1e-9));
        assertThat(fromDescending.get(1)).isCloseTo(-0.10, within(1e-9));
        assertThat(fromDescending.get(2)).isCloseTo(0.10, within(1e-9));
        assertThat(fromDescending).isEqualTo(Calculations.dailyReturns(ascending));
    }

    @Test
    void periodReturnPct_rejectsZeroStartPrice() {
        List<DailyQuote> quotes = List.of(q("2026-07-14", 0), q("2026-07-15", 105));
        assertThatThrownBy(() -> Calculations.periodReturnPct(quotes))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INVALID"));
    }

    @Test
    void dailyReturns_rejectsZeroPriceInSeries() {
        List<DailyQuote> quotes = List.of(
                q("2026-07-14", 100), q("2026-07-15", 0), q("2026-07-16", 105));
        assertThatThrownBy(() -> Calculations.dailyReturns(quotes))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INVALID"));
    }

    @Test
    void sampleStdDev_rejectsSingleValue() {
        assertThatThrownBy(() -> Calculations.sampleStdDev(List.of(0.05)))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }

    @Test
    void sampleStdDev_rejectsEmptyList() {
        assertThatThrownBy(() -> Calculations.sampleStdDev(List.of()))
                .isInstanceOfSatisfying(MetricException.class,
                        e -> assertThat(e.code()).isEqualTo("DATA_INSUFFICIENT"));
    }
}
