package kr.fitorder.common;

import java.util.List;

/**
 * 원천에서 읽은 데이터.
 *
 * @param columns 원천의 실제 컬럼명(CSV 헤더, API 응답 키)
 * @param records 공통 레코드
 */
public record RawData(List<String> columns, List<RawRecord> records) {
}
