package com.koscom.kopilot.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 지표 답변 카드 1장의 전체 데이터. 프론트는 이 JSON을 직접 렌더한다(스펙 5절 원칙 1).
 * Jackson 직렬화 형태 예시:
 * {
 *   "cardId":"...", "metric":"RETURN_GAP", "title":"삼성전자 vs 코스피 수익률 갭 (최근 1개월)",
 *   "from":"2026-06-19","to":"2026-07-19",
 *   "targets":[{"code":"005930","name":"삼성전자"}],
 *   "headline":[{"label":"수익률 갭","value":3.0,"unit":"%p"}],
 *   "chart":{"chartType":"line","series":[{"name":"삼성전자","points":[{"label":"2026-06-19","value":0.0}]}]},
 *   "evidence":{"apiCalls":[{"api":"주식 일별 시세","request":"...","specUrl":"..."}],
 *               "rawData":[{"name":"삼성전자","rows":[{"date":"2026-06-19","value":81500.0}]}],
 *               "formula":"...","steps":[{"label":"...","detail":"..."}]}
 * }
 */
public record MetricResult(
        String cardId,
        String metric,
        String title,
        LocalDate from,
        LocalDate to,
        List<Target> targets,
        List<Headline> headline,
        ChartSpec chart,
        Evidence evidence
) {
    public record Target(String code, String name) {}
    public record Headline(String label, double value, String unit) {}

    public record ChartSpec(String chartType, List<Series> series) {   // chartType: line | bar
        public record Series(String name, List<Point> points) {}
        /** label: line 차트는 ISO 날짜 문자열, bar 차트는 카테고리명(종목명 등) */
        public record Point(String label, double value) {}
    }

    public record Evidence(List<ApiCall> apiCalls, List<RawSeries> rawData,
                           String formula, List<Step> steps) {
        public record ApiCall(String api, String request, String specUrl) {}
        public record RawSeries(String name, List<Row> rows) {}
        public record Row(LocalDate date, double value) {}
        public record Step(String label, String detail) {}
    }

    public static String newCardId() { return UUID.randomUUID().toString(); }
}
