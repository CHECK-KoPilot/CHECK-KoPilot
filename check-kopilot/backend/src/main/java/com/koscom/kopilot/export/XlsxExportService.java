package com.koscom.kopilot.export;

import com.koscom.kopilot.domain.MetricResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class XlsxExportService {

    public byte[] toXlsx(MetricResult r) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            buildSummary(wb.createSheet("결과 요약"), r);
            buildRawData(wb.createSheet("원본 데이터"), r);
            buildCalcSteps(wb.createSheet("계산 과정"), r);
            wb.write(out);
            return out.toByteArray();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("xlsx 생성 실패", e);
        }
    }

    private void buildSummary(Sheet sheet, MetricResult r) {
        int i = 0;
        row(sheet, i++, "제목", r.title());
        row(sheet, i++, "지표", r.metric());
        row(sheet, i++, "기간", r.from() + " ~ " + r.to());
        row(sheet, i++, "대상", r.targets().stream()
                .map(t -> t.name() + "(" + t.code() + ")").reduce((a, b) -> a + ", " + b).orElse(""));
        for (MetricResult.Headline h : r.headline()) {          // 4행부터 헤드라인
            Row row = sheet.createRow(i++);
            row.createCell(0).setCellValue(h.label());
            row.createCell(1).setCellValue(h.value());          // 숫자 셀 — 카드 수치 그대로
            row.createCell(2).setCellValue(h.unit());
        }
        int apiStart = i + 1;
        sheet.createRow(apiStart - 1).createCell(0).setCellValue("호출 API");
        for (MetricResult.Evidence.ApiCall c : r.evidence().apiCalls()) {
            Row row = sheet.createRow(apiStart++);
            row.createCell(0).setCellValue(c.api());
            row.createCell(1).setCellValue(c.request());
            row.createCell(2).setCellValue(c.specUrl());
        }
    }

    private void buildRawData(Sheet sheet, MetricResult r) {
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("시리즈");
        header.createCell(1).setCellValue("일자");
        header.createCell(2).setCellValue("값");
        int i = 1;
        for (MetricResult.Evidence.RawSeries s : r.evidence().rawData()) {
            for (MetricResult.Evidence.Row dataRow : s.rows()) {
                Row row = sheet.createRow(i++);
                row.createCell(0).setCellValue(s.name());
                row.createCell(1).setCellValue(dataRow.date().toString());
                row.createCell(2).setCellValue(dataRow.value());
            }
        }
    }

    private void buildCalcSteps(Sheet sheet, MetricResult r) {
        row(sheet, 0, "공식", r.evidence().formula());
        int i = 2;
        for (MetricResult.Evidence.Step s : r.evidence().steps()) {
            Row row = sheet.createRow(i++);
            row.createCell(0).setCellValue(s.label());
            row.createCell(1).setCellValue(s.detail());
        }
    }

    private void row(Sheet sheet, int idx, String k, String v) {
        Row row = sheet.createRow(idx);
        row.createCell(0).setCellValue(k);
        row.createCell(1).setCellValue(v);
    }
}
