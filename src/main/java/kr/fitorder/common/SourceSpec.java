package kr.fitorder.common;

import java.util.Map;

/**
 * 원천 데이터 하나의 읽기 설정(application.yml의 fitorder.ingestion.sources).
 *
 * @param name    화면에 보일 이름
 * @param charset 파일 인코딩
 * @param columns 표준 필드명 → 원본 컬럼명
 */
public record SourceSpec(String name, String charset, Map<String, String> columns) {

    public SourceSpec {
        charset = charset == null || charset.isBlank() ? "CP949" : charset;
        columns = columns == null ? Map.of() : Map.copyOf(columns);
    }
}
