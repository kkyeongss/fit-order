package kr.fitorder.module.uniform.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.ItemPlan;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeLine;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/** 발주표 엑셀: 품목, 사이즈, 추천 수량(기본·안전재고), 단가, 금액, 근거. */
final class RecommendationExcel {

    private static final String[] HEADERS = {"품목", "사이즈", "비율", "기본 수량", "안전재고", "추천 수량", "단가(원)",
            "금액(원)", "근거"};

    private RecommendationExcel() {
    }

    static byte[] write(Recommendation rec) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(rec.year() + "년 발주표");
            CellStyle bold = wb.createCellStyle();
            Font font = wb.createFont();
            font.setBold(true);
            bold.setFont(font);
            CellStyle header = wb.createCellStyle();
            header.cloneStyleFrom(bold);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            CellStyle number = wb.createCellStyle();
            number.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            CellStyle percent = wb.createCellStyle();
            percent.setDataFormat(wb.createDataFormat().getFormat("0.0%"));

            int r = 0;
            text(sheet.createRow(r++), 0, rec.year() + "년 근무복 발주 추천", bold);
            text(sheet.createRow(r++), 0, "대상 인원: " + (rec.headcount() == null ? "정원 데이터 없음(보정 안 함)"
                    : rec.headcount() + "명") + " / 집계 기준: " + (rec.basis().name().equals("DEDUP") ? "중복 제거" : "원자료"),
                    null);
            text(sheet.createRow(r++), 0, "정기 발주 시기 제안: " + rec.orderMonths().stream()
                    .map(m -> m.month() + "월(" + Math.round(m.share() * 100) + "%)")
                    .reduce((a, b) -> a + ", " + b).orElse("-"), null);
            if (rec.totals().priceEstimated()) {
                text(sheet.createRow(r++), 0, "단가: 입력 단가 > 조달청 쇼핑몰 시장가(중앙값) > 추정 단가 순으로 씀", null);
            }
            r++;
            Row head = sheet.createRow(r++);
            for (int i = 0; i < HEADERS.length; i++) {
                text(head, i, HEADERS[i], header);
            }
            for (ItemPlan item : rec.items()) {
                String rule = item.total().rule() + ". " + (item.headcountFactor() != 1.0
                        ? String.format("인원 보정 ×%.3f(%d년 정원 %d명). ", item.headcountFactor(), item.headcountYear(),
                        item.baseHeadcount()) : "")
                        + "안전재고 " + Math.round(item.safetyRate() * 100) + "%. " + item.sizeRule();
                boolean first = true;
                for (SizeLine line : item.sizes()) {
                    Row row = sheet.createRow(r++);
                    text(row, 0, item.label(), null);
                    text(row, 1, line.size(), null);
                    num(row, 2, line.ratio(), percent);
                    num(row, 3, line.base(), number);
                    num(row, 4, line.safety(), number);
                    num(row, 5, line.quantity(), number);
                    num(row, 6, item.unitPrice().doubleValue(), number);
                    num(row, 7, item.unitPrice().multiply(BigDecimal.valueOf(line.quantity())).doubleValue(), number);
                    if (first) {
                        text(row, 8, rule + " 단가: " + item.priceSource().label() + ".", null);
                        first = false;
                    }
                }
                Row sum = sheet.createRow(r++);
                text(sum, 0, item.label() + " 소계", bold);
                num(sum, 3, item.baseQuantity(), number);
                num(sum, 4, item.safetyQuantity(), number);
                num(sum, 5, item.quantity(), number);
                num(sum, 7, item.amount().doubleValue(), number);
            }
            Row total = sheet.createRow(r++);
            text(total, 0, "합계", bold);
            num(total, 4, rec.totals().safetyQuantity(), number);
            num(total, 5, rec.totals().quantity(), number);
            num(total, 7, rec.totals().amount().doubleValue(), number);
            r++;
            text(sheet.createRow(r++), 0, String.format("예상 절감(연간): %.0f벌, %,d원, 탄소 %.1f kg CO2e "
                            + "(사이즈 미스매치율 × 추천 수량 × 개선률 %.0f%%)", rec.totals().savedQuantity(),
                    rec.totals().savedAmount().longValue(), rec.totals().carbonKg().doubleValue(),
                    rec.improvementRate() * 100), null);
            for (var g : rec.greenProducts()) {
                if (g.validCount() > 0) {
                    text(sheet.createRow(r++), 0, String.format("녹색제품(환경표지 인증 유효): %s %d개 제품, %d개 기업. 예) %s",
                            g.label(), g.validCount(), g.companyCount(), String.join(", ", g.examples())), null);
                }
            }
            for (var m : rec.marketPrices()) {
                if (m.ecoCount() > 0) {
                    text(sheet.createRow(r++), 0, String.format("녹색구매 참고: %s 환경표지제품 %d건, 중앙값 %,d원 "
                                    + "(쇼핑몰 전체 %d건 중앙값 %,d원)", m.label(), m.ecoCount(),
                            m.ecoMedianPrice().longValue(), m.sampleCount(), m.medianPrice().longValue()), null);
                }
            }

            int[] widths = {12, 22, 8, 10, 10, 10, 11, 13, 90};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        if (style != null) {
            cell.setCellStyle(style);
        }
    }

    private static void num(Row row, int col, double value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }
}
