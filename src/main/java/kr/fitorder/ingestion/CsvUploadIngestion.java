package kr.fitorder.ingestion;

import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.DataIngestion;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.RawData;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceSpec;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

/** 업로드된 CSV를 설정의 인코딩·컬럼 매핑으로 읽는다. */
@Component
public class CsvUploadIngestion implements DataIngestion {

    private static final CSVFormat FORMAT = CSVFormat.DEFAULT.builder()
            .setHeader()
            .setSkipHeaderRecord(true)
            .setTrim(true)
            .setIgnoreEmptyLines(true)
            .setAllowMissingColumnNames(true)
            .get();

    @Override
    public LoadMethod method() {
        return LoadMethod.UPLOAD;
    }

    @Override
    public RawData read(IngestionRequest request) {
        if (request.content() == null || request.content().length == 0) {
            throw new BusinessException("빈 파일입니다.");
        }
        SourceSpec spec = request.spec();
        String text = decode(request.content(), spec.charset());

        try (CSVParser parser = CSVParser.parse(new StringReader(text), FORMAT)) {
            List<String> headers = parser.getHeaderNames().stream().map(String::strip).toList();
            List<RawRecord> records = new ArrayList<>();
            for (CSVRecord row : parser) {
                Map<String, String> original = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    original.put(headers.get(i), i < row.size() ? row.get(i) : "");
                }
                Map<String, String> fields = new LinkedHashMap<>();
                spec.columns().forEach((field, column) -> fields.put(field, original.get(column)));
                // 헤더가 1행이므로 데이터 행 번호는 레코드 번호 + 1
                records.add(new RawRecord(row.getRecordNumber() + 1, original, fields));
            }
            return new RawData(headers, records);
        } catch (IOException | IllegalStateException | java.io.UncheckedIOException e) {
            throw new BusinessException("CSV 형식을 읽을 수 없습니다: " + e.getMessage());
        }
    }

    private static String decode(byte[] content, String charsetName) {
        Charset charset = charsetOf(charsetName);
        try {
            String text = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException e) {
            throw new BusinessException("파일 인코딩이 " + charsetName + "이(가) 아닙니다. 인코딩을 확인하세요.");
        }
    }

    /** CP949는 자바에서 MS949(x-windows-949)라는 이름이다. */
    public static Charset charsetOf(String name) {
        if ("CP949".equalsIgnoreCase(name)) {
            return Charset.forName("x-windows-949");
        }
        return Charset.forName(name);
    }
}
