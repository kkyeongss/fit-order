package kr.fitorder.module.uniform.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.ItemPlan;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeLine;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import kr.fitorder.module.uniform.analysis.RecommendationService.RoundTotal;
import kr.fitorder.module.uniform.analysis.SizeLabels;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * 발주표 엑셀. 담당자가 바로 결재·발주에 쓰도록 만든다.
 * <ul>
 *   <li>노란 칸(현재 재고, 발주처, 납기, 비고)은 직접 입력. 발주 수량·회차별 수량·소계·합계·금액은 수식이라 고치면 따라 바뀐다</li>
 *   <li>결재란, 회차(4·8월)별 발주 수량, 품목마다 산정 근거와 확인할 점, 인쇄 설정(가로, 한 쪽 너비)</li>
 *   <li>같은 출처의 사이즈 이름 꼬리표는 떼고, 필요·재고가 모두 0인 사이즈 줄은 뺀다</li>
 * </ul>
 */
final class RecommendationExcel {

    private static final int COL_ITEM = 0;
    private static final int COL_SIZE = 1;
    private static final int COL_RATIO = 2;
    private static final int COL_NEED = 3;
    private static final int COL_STOCK = 4;
    private static final int COL_ORDER = 5;
    private static final int COL_ROUNDS = 6;
    private static final int HEADER_ROW = 7;
    private static final int SHARE_ROW = 5;

    private RecommendationExcel() {
    }

    static byte[] write(Recommendation rec) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.setForceFormulaRecalculation(true);
            Sheet sheet = wb.createSheet(rec.year() + "년 발주표");
            Styles st = new Styles(wb);
            List<RoundTotal> rounds = rec.totals().rounds();
            int m = rounds.size();
            int colPrice = COL_ROUNDS + m;
            int colAmount = colPrice + 1;
            int colNote = colAmount + 1;

            // ---- 머리글: 제목, 결재란, 입력 칸
            text(sheet, 0, 0, rec.year() + "년 근무복 발주서 (핏오더 추천안)", st.title);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 4));
            String[] sign = {"담당", "팀장", "부서장"};
            for (int i = 0; i < sign.length; i++) {
                text(sheet, 0, COL_ORDER + i, sign[i], st.head);
                cell(sheet, 1, COL_ORDER + i).setCellStyle(st.box);
            }
            sheet.getRow(1).setHeightInPoints(34);
            text(sheet, 1, 0, "대상 인원 " + (rec.headcount() == null ? "정원 데이터 없음(보정 안 함)" : rec.headcount() + "명")
                    + "  ·  집계 기준 " + (rec.basis().name().equals("DEDUP") ? "중복 제거" : "원자료")
                    + "  ·  데이터 마지막 주문일 " + (rec.lastOrderDate() == null ? "-" : rec.lastOrderDate()), st.plain);
            text(sheet, 2, 0, "정기 발주 시기: " + (m == 0 ? "-" : rounds.stream()
                    .map(r -> r.month() + "월 " + Math.round(r.share() * 100) + "%").reduce((a, b) -> a + ", " + b).orElse("-"))
                    + " (과거 수량 비중을 합 100%로 환산해 나눔)", st.plain);
            text(sheet, 3, 0, "발주처", st.label);
            cell(sheet, 3, 1).setCellStyle(st.input);
            text(sheet, 3, 2, "납기", st.label);
            cell(sheet, 3, 3).setCellStyle(st.input);
            text(sheet, 3, 4, "비고", st.label);
            for (int c = 5; c <= colNote; c++) {
                cell(sheet, 3, c).setCellStyle(st.input);
            }
            sheet.addMergedRegion(new CellRangeAddress(3, 3, 5, colNote));
            text(sheet, 4, 0, "노란 칸(현재 재고·발주처·납기·비고)은 직접 입력합니다. 발주 수량 = 필요 수량 − 현재 재고, 소계·합계·금액은 자동 계산됩니다."
                    + " 단가는 입력 단가 > 조달청 시장가(중앙값) > 추정 단가 순으로 썼습니다.", st.small);

            // ---- 회차 비율 줄과 표 머리글
            text(sheet, SHARE_ROW, 0, "회차 비율(합 100%)", st.label);
            for (int i = 0; i < m; i++) {
                Cell c = cell(sheet, SHARE_ROW, COL_ROUNDS + i);
                c.setCellValue(rounds.get(i).share());
                c.setCellStyle(st.percentInput);
            }
            List<String> heads = new ArrayList<>(List.of("품목", "사이즈", "비율", "필요 수량\n(안전재고 포함)", "현재 재고", "발주 수량"));
            for (RoundTotal r : rounds) {
                heads.add(r.month() + "월 발주");
            }
            heads.addAll(List.of("단가(원)", "금액(원)", "근거 · 확인할 점"));
            for (int c = 0; c < heads.size(); c++) {
                text(sheet, HEADER_ROW, c, heads.get(c), st.head);
            }
            sheet.getRow(HEADER_ROW).setHeightInPoints(30);

            // ---- 품목별
            int r = HEADER_ROW + 1;
            List<Integer> subtotalRows = new ArrayList<>();
            for (ItemPlan item : rec.items()) {
                List<SizeLine> lines = item.sizes().stream().filter(z -> z.quantity() > 0 || z.stock() > 0).toList();
                UnaryOperator<String> label = SizeLabels.withoutCommonSource(item.sizes().stream().map(SizeLine::size).toList());
                int first = r;
                for (SizeLine z : lines) {
                    Row row = sheet.createRow(r);
                    text(sheet, r, COL_ITEM, item.label(), st.cell);
                    text(sheet, r, COL_SIZE, label.apply(z.size()) + (z.unknownCode() ? " (뜻 미확인)" : ""), st.cell);
                    num(sheet, r, COL_RATIO, z.ratio(), st.percent);
                    num(sheet, r, COL_NEED, z.quantity(), st.number);
                    num(sheet, r, COL_STOCK, z.stock(), st.numberInput);
                    formula(sheet, r, COL_ORDER, "MAX(0," + ref(COL_NEED, r) + "-" + ref(COL_STOCK, r) + ")", st.numberBold);
                    for (int i = 0; i < m; i++) {
                        String f;
                        if (m == 1) {
                            f = ref(COL_ORDER, r);
                        } else if (i < m - 1) {
                            f = "ROUND(" + ref(COL_ORDER, r) + "*" + abs(COL_ROUNDS + i, SHARE_ROW) + ",0)";
                        } else {
                            f = ref(COL_ORDER, r) + "-SUM(" + ref(COL_ROUNDS, r) + ":" + ref(COL_ROUNDS + m - 2, r) + ")";
                        }
                        formula(sheet, r, COL_ROUNDS + i, f, st.number);
                    }
                    num(sheet, r, colPrice, item.unitPrice().doubleValue(), st.number);
                    formula(sheet, r, colAmount, ref(colPrice, r) + "*" + ref(COL_ORDER, r), st.number);
                    cell(sheet, r, colNote).setCellStyle(st.cell);
                    row.setHeightInPoints(15);
                    r++;
                }
                // 소계
                text(sheet, r, COL_ITEM, item.label() + " 소계", st.subtotal);
                for (int c : new int[] {COL_SIZE, COL_RATIO, colPrice, colNote}) {
                    cell(sheet, r, c).setCellStyle(st.subtotal);
                }
                int last = r - 1;
                for (int c = COL_NEED; c < COL_ROUNDS + m; c++) {
                    sumFormula(sheet, r, c, first, last, st.subtotalNumber);
                }
                sumFormula(sheet, r, colAmount, first, last, st.subtotalNumber);
                subtotalRows.add(r);
                r++;
                // 근거와 확인할 점
                StringBuilder note = new StringBuilder("근거: ").append(item.total().rule());
                if (item.headcountFactor() != 1 && item.baseHeadcount() != null) {
                    note.append(". 인원 보정 ×").append(String.format("%.3f", item.headcountFactor())).append("(")
                            .append(item.headcountYear()).append("년 정원 ").append(item.baseHeadcount()).append("명)");
                }
                note.append(". 안전재고 ").append(Math.round(item.safetyRate() * 100)).append("%. ").append(item.sizeRule())
                        .append(". 단가 ").append(String.format("%,d", item.unitPrice().longValue())).append("원(")
                        .append(item.priceSource() == null ? "-" : switch (item.priceSource()) {
                            case MANUAL -> "입력 단가";
                            case MARKET -> "조달청 시장가";
                            case ESTIMATE -> "추정";
                        }).append(")");
                String common = SizeLabels.commonSource(item.sizes().stream().map(SizeLine::size).toList());
                if (common != null) {
                    note.append(". 사이즈 코드는 '").append(common).append("' 기준");
                }
                for (String w : item.warnings()) {
                    note.append("\n⚠ ").append(w);
                }
                text(sheet, r, 0, note.toString(), st.note);
                sheet.addMergedRegion(new CellRangeAddress(r, r, 0, colNote));
                sheet.getRow(r).setHeightInPoints(Math.max(15, 13.5f * lineCount(note.toString(), 170)));
                r += 2;
            }
            // 합계
            text(sheet, r, 0, "합계", st.total);
            for (int c : new int[] {COL_SIZE, COL_RATIO, colPrice, colNote}) {
                cell(sheet, r, c).setCellStyle(st.total);
            }
            for (int c = COL_NEED; c < COL_ROUNDS + m; c++) {
                sumOfRows(sheet, r, c, subtotalRows, st.totalNumber);
            }
            sumOfRows(sheet, r, colAmount, subtotalRows, st.totalNumber);
            int totalRow = r;
            r += 2;

            // ---- 참고
            text(sheet, r++, 0, String.format("예상 절감(연간): %.0f벌, %,d원, 탄소 %.1f kg CO2e (사이즈 미스매치율 × 추천 수량 × 개선률 %.0f%%, 추정치)",
                    rec.totals().savedQuantity(), rec.totals().savedAmount().longValue(), rec.totals().carbonKg().doubleValue(),
                    rec.improvementRate() * 100), st.plain);
            text(sheet, r++, 0, String.format("필요 수량 참고 범위: %,d ~ %,d벌 (백테스트 평균 총량 오차 약 %.0f%% 기준)", rec.totals().range().low(),
                    rec.totals().range().high(), rec.totals().range().errorRate() * 100), st.plain);
            if (rec.otherBasis() != null) {
                String other = rec.otherBasis().basis().name().equals("RAW") ? "원자료" : "중복 제거";
                text(sheet, r++, 0, "참고: " + other + " 기준으로 계산하면 필요 수량 " + String.format("%,d", rec.otherBasis().quantity())
                        + "벌입니다(완전 중복 행을 어떻게 보느냐에 따른 차이).", st.plain);
            }
            for (String w : rec.warnings()) {
                text(sheet, r++, 0, "⚠ " + w, st.plain);
            }
            for (var g : rec.greenProducts()) {
                if (g.validCount() > 0) {
                    text(sheet, r++, 0, String.format("녹색제품(환경표지 인증 유효): %s %d개 제품, %d개 기업. 예) %s", g.label(), g.validCount(),
                            g.companyCount(), String.join(", ", g.examples())), st.plain);
                }
            }
            for (var mp : rec.marketPrices()) {
                if (mp.ecoCount() > 0) {
                    text(sheet, r++, 0, String.format("녹색구매 참고: %s 환경표지제품 %d건, 중앙값 %,d원 (쇼핑몰 전체 %d건 중앙값 %,d원)", mp.label(),
                            mp.ecoCount(), mp.ecoMedianPrice().longValue(), mp.sampleCount(), mp.medianPrice().longValue()), st.plain);
                }
            }

            int[] widths = {12, 18, 8, 13, 10, 10};
            for (int i = 0; i < widths.length; i++) {
                sheet.setColumnWidth(i, widths[i] * 256);
            }
            for (int i = 0; i < m; i++) {
                sheet.setColumnWidth(COL_ROUNDS + i, 10 * 256);
            }
            sheet.setColumnWidth(colPrice, 11 * 256);
            sheet.setColumnWidth(colAmount, 14 * 256);
            sheet.setColumnWidth(colNote, 40 * 256);
            sheet.createFreezePane(2, HEADER_ROW + 1);

            // 인쇄: 가로, 한 쪽 너비, 머리글 반복, 쪽 번호
            sheet.setFitToPage(true);
            sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setFitWidth((short) 1);
            sheet.getPrintSetup().setFitHeight((short) 0);
            sheet.getPrintSetup().setPaperSize((short) 9);
            sheet.setRepeatingRows(new CellRangeAddress(HEADER_ROW, HEADER_ROW, -1, -1));
            sheet.getFooter().setCenter("핏오더 발주서 - &P / &N");
            sheet.setMargin(Sheet.LeftMargin, 0.4);
            sheet.setMargin(Sheet.RightMargin, 0.4);
            sheet.setMargin(Sheet.TopMargin, 0.5);
            sheet.setMargin(Sheet.BottomMargin, 0.6);
            sheet.setAutoFilter(new CellRangeAddress(HEADER_ROW, totalRow - 1, 0, colAmount));
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // ---- 셀 도우미 ---------------------------------------------------------------

    private static Cell cell(Sheet sheet, int row, int col) {
        Row r = sheet.getRow(row) != null ? sheet.getRow(row) : sheet.createRow(row);
        return r.getCell(col) != null ? r.getCell(col) : r.createCell(col);
    }

    private static void text(Sheet sheet, int row, int col, String value, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void num(Sheet sheet, int row, int col, double value, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellValue(value);
        c.setCellStyle(style);
    }

    private static void formula(Sheet sheet, int row, int col, String f, CellStyle style) {
        Cell c = cell(sheet, row, col);
        c.setCellFormula(f);
        c.setCellStyle(style);
    }

    private static void sumFormula(Sheet sheet, int row, int col, int from, int to, CellStyle style) {
        if (to < from) {
            num(sheet, row, col, 0, style);
        } else {
            formula(sheet, row, col, "SUM(" + ref(col, from) + ":" + ref(col, to) + ")", style);
        }
    }

    private static void sumOfRows(Sheet sheet, int row, int col, List<Integer> rows, CellStyle style) {
        if (rows.isEmpty()) {
            num(sheet, row, col, 0, style);
            return;
        }
        formula(sheet, row, col, rows.stream().map(x -> ref(col, x)).reduce((a, b) -> a + "+" + b).orElse("0"), style);
    }

    /** 엑셀 주소(예: F9). row는 0부터. */
    private static String ref(int col, int row) {
        return new CellReference(row, col).formatAsString(false);
    }

    /** 고정 주소(예: G$6). */
    private static String abs(int col, int row) {
        return CellReference.convertNumToColString(col) + "$" + (row + 1);
    }

    private static int lineCount(String text, int charsPerLine) {
        int lines = 0;
        for (String part : text.split("\n", -1)) {
            lines += Math.max(1, (int) Math.ceil(part.length() / (double) charsPerLine));
        }
        return lines;
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle head;
        final CellStyle plain;
        final CellStyle small;
        final CellStyle label;
        final CellStyle input;
        final CellStyle box;
        final CellStyle cell;
        final CellStyle number;
        final CellStyle numberBold;
        final CellStyle numberInput;
        final CellStyle percent;
        final CellStyle percentInput;
        final CellStyle subtotal;
        final CellStyle subtotalNumber;
        final CellStyle total;
        final CellStyle totalNumber;
        final CellStyle note;

        Styles(XSSFWorkbook wb) {
            Font bold = wb.createFont();
            bold.setBold(true);
            Font big = wb.createFont();
            big.setBold(true);
            big.setFontHeightInPoints((short) 14);
            Font small = wb.createFont();
            small.setFontHeightInPoints((short) 9);
            small.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            Font warn = wb.createFont();
            warn.setFontHeightInPoints((short) 9);

            title = base(wb, big, null, false, HorizontalAlignment.LEFT);
            head = base(wb, bold, IndexedColors.GREY_25_PERCENT, true, HorizontalAlignment.CENTER);
            head.setWrapText(true);
            plain = base(wb, null, null, false, HorizontalAlignment.LEFT);
            this.small = base(wb, small, null, false, HorizontalAlignment.LEFT);
            label = base(wb, bold, null, false, HorizontalAlignment.RIGHT);
            input = base(wb, null, IndexedColors.LIGHT_YELLOW, true, HorizontalAlignment.LEFT);
            box = base(wb, null, null, true, HorizontalAlignment.CENTER);
            cell = base(wb, null, null, true, HorizontalAlignment.LEFT);
            number = base(wb, null, null, true, HorizontalAlignment.RIGHT);
            number.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            numberBold = base(wb, bold, null, true, HorizontalAlignment.RIGHT);
            numberBold.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            numberInput = base(wb, null, IndexedColors.LIGHT_YELLOW, true, HorizontalAlignment.RIGHT);
            numberInput.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            percent = base(wb, null, null, true, HorizontalAlignment.RIGHT);
            percent.setDataFormat(wb.createDataFormat().getFormat("0.0%"));
            percentInput = base(wb, null, IndexedColors.LIGHT_YELLOW, true, HorizontalAlignment.RIGHT);
            percentInput.setDataFormat(wb.createDataFormat().getFormat("0.0%"));
            subtotal = base(wb, bold, IndexedColors.GREY_25_PERCENT, true, HorizontalAlignment.LEFT);
            subtotalNumber = base(wb, bold, IndexedColors.GREY_25_PERCENT, true, HorizontalAlignment.RIGHT);
            subtotalNumber.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            total = base(wb, bold, IndexedColors.GREY_40_PERCENT, true, HorizontalAlignment.LEFT);
            totalNumber = base(wb, bold, IndexedColors.GREY_40_PERCENT, true, HorizontalAlignment.RIGHT);
            totalNumber.setDataFormat(wb.createDataFormat().getFormat("#,##0"));
            note = base(wb, warn, null, false, HorizontalAlignment.LEFT);
            note.setWrapText(true);
            note.setVerticalAlignment(VerticalAlignment.TOP);
        }

        private static CellStyle base(XSSFWorkbook wb, Font font, IndexedColors fill, boolean border, HorizontalAlignment align) {
            CellStyle s = wb.createCellStyle();
            if (font != null) {
                s.setFont(font);
            }
            if (fill != null) {
                s.setFillForegroundColor(fill.getIndex());
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            if (border) {
                s.setBorderTop(BorderStyle.THIN);
                s.setBorderBottom(BorderStyle.THIN);
                s.setBorderLeft(BorderStyle.THIN);
                s.setBorderRight(BorderStyle.THIN);
            }
            s.setAlignment(align);
            s.setVerticalAlignment(VerticalAlignment.CENTER);
            return s;
        }
    }

    /** 테스트용: 금액 문자열. */
    static String won(BigDecimal v) {
        return String.format("%,d", v.longValue());
    }
}
