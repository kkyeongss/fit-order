package kr.fitorder.common;

/**
 * ESG 항목의 지표 입력값(적재 데이터, 단가, 사이즈 매핑 등)이 바뀌었다.
 * 트랜잭션이 커밋되면 그 항목의 지표를 다시 계산한다.
 */
public record EsgDataChangedEvent(String itemCode, String reason) {
}
