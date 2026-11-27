package kr.fitorder.common;

/** 품질 플래그. 플래그가 달린 행도 적재하고 품질 리포트에 올린다. */
public enum QualityFlag {
    MISSING_QUANTITY("수량 결측"),
    INVALID_QUANTITY("수량 형식 오류"),
    MISSING_DATE("주문일 결측"),
    INVALID_DATE("주문일 형식 오류"),
    UNKNOWN_ITEM("품목 분류 불가"),
    UNKNOWN_SIZE("뜻을 모르는 사이즈 코드");

    private final String label;

    QualityFlag(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
