package kr.fitorder.module.uniform.domain;

/** 품목군. 탄소계수·안전재고 비율을 품목군 단위로 정한다. */
public enum UniformItemGroup {
    SHIRT("셔츠"),
    JACKET("재킷"),
    JUMPER("점퍼"),
    PADDING_VEST("패딩조끼"),
    PANTS("바지"),
    VEST("조끼"),
    UNKNOWN("미분류");

    private final String label;

    UniformItemGroup(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
