package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * XlsxExportService 시나리오 테스트.
 *
 * <p><b>무엇을 보장하나</b>: 답변 카드(MetricResult)를 xlsx로 내보낼 때, 파일의 수치가
 * 카드의 수치와 <b>정확히 일치</b>해야 한다(스펙 11절 3항 "xlsx 검증 — 다운로드 파일 수치와
 * 답변 카드 수치 일치 스냅샷"). 백엔드 계산값이 최종 방어선이므로, 엑셀로 흘러가는 과정에서
 * 값이 재가공되거나 어긋나면 안 된다.</p>
 *
 * <p><b>시트 레이아웃은 계약(contract)이다</b>: 시트 순서(결과 요약 → 원본 데이터 → 계산 과정)와
 * 각 값의 행/열 위치는 프론트·QA가 의존하는 고정 규격이다. 구현을 바꿔 위치가 틀어지면
 * 테스트가 아니라 구현을 되돌린다.</p>
 */
@DisplayName("xlsx 내보내기: 3개 시트 + 카드 수치 스냅샷 일치")
class XlsxExportServiceTest {

    private final XlsxExportService service = new XlsxExportService();

    /**
     * 표준 표본 카드(수익률 갭). 모든 시나리오가 이 한 장을 기준으로 검증한다.
     * - 헤드라인 1건: "수익률 갭" = 3.0 %p
     * - 원본 2행: 삼성전자 종가 100.0(07-13), 105.0(07-17)
     * - 공식/스텝 1건
     */
    private MetricResult sampleCard() {
        return new MetricResult(
            "test-card-id", "RETURN_GAP", "삼성전자 vs 코스피 수익률 갭",
            LocalDate.parse("2026-07-13"), LocalDate.parse("2026-07-17"),
            List.of(new MetricResult.Target("005930", "삼성전자"),
                    new MetricResult.Target("KOSPI", "코스피")),
            List.of(new MetricResult.Headline("수익률 갭", 3.0, "%p")),
            new MetricResult.ChartSpec("line", List.of()),
            new MetricResult.Evidence(
                List.of(new MetricResult.Evidence.ApiCall("일별 시세 조회", "삼성전자(005930) 2026-07-13 ~ 2026-07-17", "https://example")),
                List.of(new MetricResult.Evidence.RawSeries("삼성전자", List.of(
                        new MetricResult.Evidence.Row(LocalDate.parse("2026-07-13"), 100.0),
                        new MetricResult.Evidence.Row(LocalDate.parse("2026-07-17"), 105.0)))),
                "수익률 갭 = A − B",
                List.of(new MetricResult.Evidence.Step("수익률 갭", "5.0 − 2.0 = 3.0%p"))));
    }

    /**
     * 시나리오 1 (핵심 스냅샷 — plan.md Task 9 Step 1과 동일):
     *   Given  수익률 갭 카드 1장을
     *   When   xlsx로 내보내면
     *   Then   시트가 3장(결과 요약/원본 데이터/계산 과정)이고,
     *          각 시트의 대표 수치가 카드 값과 그대로 일치한다.
     *          특히 결과 요약 4행의 헤드라인 값(3.0)이 카드 헤드라인과 동일해야 한다(환각/재가공 방지).
     */
    @Test
    @DisplayName("시트 3장이 순서대로 생성되고, 결과 요약의 헤드라인 수치가 카드와 일치한다")
    void producesThreeSheets_andSummaryValuesMatchCard() throws Exception {
        byte[] bytes = service.toXlsx(sampleCard());
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // Then: 시트 순서/이름은 고정 계약
            assertThat(wb.getNumberOfSheets()).isEqualTo(3);
            assertThat(wb.getSheetName(0)).isEqualTo("결과 요약");
            assertThat(wb.getSheetName(1)).isEqualTo("원본 데이터");
            assertThat(wb.getSheetName(2)).isEqualTo("계산 과정");

            var summary = wb.getSheetAt(0);
            // 헤드라인 행: [label, value, unit] — 카드 수치와 xlsx 수치 일치(스냅샷)
            var headlineRow = summary.getRow(4);
            assertThat(headlineRow.getCell(0).getStringCellValue()).isEqualTo("수익률 갭");
            assertThat(headlineRow.getCell(1).getNumericCellValue()).isEqualTo(3.0);

            var raw = wb.getSheetAt(1);
            assertThat(raw.getRow(1).getCell(2).getNumericCellValue()).isEqualTo(100.0);

            var calc = wb.getSheetAt(2);
            assertThat(calc.getRow(0).getCell(1).getStringCellValue()).isEqualTo("수익률 갭 = A − B");
        }
    }

    /**
     * 시나리오 2 (근거 원본 데이터 보존):
     *   Given  종가 2행(100.0, 105.0)을 가진 카드를
     *   When   내보내면
     *   Then   '원본 데이터' 시트가 헤더행 + 시리즈명/일자/값을 순서 그대로 기록해,
     *          트레이더가 엑셀에서 동일 원본으로 재검증할 수 있어야 한다.
     */
    @Test
    @DisplayName("원본 데이터 시트: 헤더 + 시리즈명·일자·종가가 순서대로 보존된다")
    void rawDataSheet_preservesSeriesRowsInOrder() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.toXlsx(sampleCard())))) {
            var raw = wb.getSheetAt(1);
            // 0행: 헤더
            assertThat(raw.getRow(0).getCell(0).getStringCellValue()).isEqualTo("시리즈");
            assertThat(raw.getRow(0).getCell(1).getStringCellValue()).isEqualTo("일자");
            assertThat(raw.getRow(0).getCell(2).getStringCellValue()).isEqualTo("값");
            // 1~2행: 시리즈 데이터(오름차순 그대로)
            assertThat(raw.getRow(1).getCell(0).getStringCellValue()).isEqualTo("삼성전자");
            assertThat(raw.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2026-07-13");
            assertThat(raw.getRow(2).getCell(2).getNumericCellValue()).isEqualTo(105.0);
        }
    }

    /**
     * 시나리오 3 (계산 과정 = 검증 가능성):
     *   Given  공식과 중간 스텝을 가진 카드를
     *   When   내보내면
     *   Then   '계산 과정' 시트가 0행에 공식을, 2행부터 스텝(label/detail)을 기록한다.
     *          이 시트가 "엑셀 수작업"을 대체하는 근거 공개의 핵심이다.
     */
    @Test
    @DisplayName("계산 과정 시트: 공식(0행)과 중간 계산 스텝(2행~)이 기록된다")
    void calcStepsSheet_writesFormulaAndSteps() throws Exception {
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(service.toXlsx(sampleCard())))) {
            var calc = wb.getSheetAt(2);
            assertThat(calc.getRow(0).getCell(0).getStringCellValue()).isEqualTo("공식");
            assertThat(calc.getRow(0).getCell(1).getStringCellValue()).isEqualTo("수익률 갭 = A − B");
            // 2행부터 스텝 (1행은 가독성용 공백 — 레이아웃 계약)
            assertThat(calc.getRow(2).getCell(0).getStringCellValue()).isEqualTo("수익률 갭");
            assertThat(calc.getRow(2).getCell(1).getStringCellValue()).isEqualTo("5.0 − 2.0 = 3.0%p");
        }
    }
}
