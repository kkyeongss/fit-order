package kr.fitorder.common;

import java.util.Map;

/**
 * 적재 방식과 무관한 공통 레코드. 원천 한 행.
 *
 * @param lineNo   원본 파일의 행 번호(헤더 = 1)
 * @param original 원본 컬럼명 → 값, 원본 컬럼 순서 유지
 * @param fields   표준 필드명 → 값
 */
public record RawRecord(long lineNo, Map<String, String> original, Map<String, String> fields) {

    /** 표준 필드 값. 비어 있으면 null. */
    public String field(String name) {
        String value = fields.get(name);
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
