package kr.fitorder.common;

/**
 * 적재기가 읽는 표준 필드. 데이터 원천 관리 화면에서 원본 컬럼과 짝짓는 대상.
 *
 * @param name     표준 필드명(예: order-date)
 * @param label    화면 이름(예: 주문일)
 * @param required 매핑이 없으면 적재할 수 없는 필드
 */
public record SourceField(String name, String label, boolean required) {
}
