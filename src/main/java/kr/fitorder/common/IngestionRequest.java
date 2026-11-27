package kr.fitorder.common;

/**
 * 적재 요청.
 *
 * @param sourceCode 원천 코드(예: uniform-order)
 * @param spec       원천 읽기 설정
 * @param fileName   업로드 파일명(API 적재면 null)
 * @param content    업로드 파일 내용(API 적재면 null)
 */
public record IngestionRequest(String sourceCode, SourceSpec spec, String fileName, byte[] content) {
}
