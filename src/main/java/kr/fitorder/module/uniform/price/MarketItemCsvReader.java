package kr.fitorder.module.uniform.price;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import kr.fitorder.common.BusinessException;
import kr.fitorder.ingestion.CsvUploadIngestion;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

/**
 * 망분리 환경용: 나라장터 쇼핑몰 계약상품을 CSV로 받는다. 필요한 컬럼은 API 응답 항목의 국문명이다.
 * 물품규격명, 계약가격금액(필수), 물품단위, 의무구매대상인증명, 쇼핑계약번호, 쇼핑계약순번(선택). UTF-8 또는 CP949.
 */
final class MarketItemCsvReader {

    static final String SPEC = "물품규격명";
    static final String PRICE = "계약가격금액";

    private MarketItemCsvReader() {
    }

    static List<MarketItem> read(byte[] content) {
        String text = decode(content);
        try (var parser = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setTrim(true).get()
                .parse(new StringReader(text))) {
            if (!parser.getHeaderNames().contains(SPEC) || !parser.getHeaderNames().contains(PRICE)) {
                throw new BusinessException("필요한 컬럼이 없습니다: " + SPEC + ", " + PRICE + " / 받은 컬럼: "
                        + parser.getHeaderNames());
            }
            List<MarketItem> items = new ArrayList<>();
            for (CSVRecord r : parser) {
                String price = r.get(PRICE).replace(",", "");
                if (price.isBlank()) {
                    continue;
                }
                String key = r.isMapped("쇼핑계약번호") ? r.get("쇼핑계약번호") + "-" + get(r, "쇼핑계약순번") : null;
                items.add(new MarketItem(key != null ? key : "line-" + r.getRecordNumber(), r.get(SPEC),
                        new BigDecimal(price), get(r, "물품단위"), MarketItem.isEco(get(r, "의무구매대상인증명"))));
            }
            return items;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (NumberFormatException e) {
            throw new BusinessException("계약가격금액에 숫자가 아닌 값이 있습니다.");
        }
    }

    private static String get(CSVRecord r, String column) {
        return r.isMapped(column) ? r.get(column) : null;
    }

    /** UTF-8로 읽히면 UTF-8(BOM 제거), 아니면 CP949. */
    private static String decode(byte[] content) {
        try {
            String s = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
            return s.startsWith("﻿") ? s.substring(1) : s;
        } catch (CharacterCodingException e) {
            return new String(content, CsvUploadIngestion.charsetOf("CP949"));
        }
    }
}
