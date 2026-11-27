package kr.fitorder.module.uniform;

/** 근무복 원천 코드. application.yml의 fitorder.ingestion.sources 키와 같다. */
public final class UniformSources {

    /** 근무복 주문현황 (셔츠·재킷·점퍼·패딩조끼, 사이즈 80~125) */
    public static final String ORDER = "uniform-order";

    /** 근무복 주문현황정보 (점퍼·바지·조끼, 사이즈 10~95) */
    public static final String ORDER_INFO = "uniform-order-info";

    private UniformSources() {
    }
}
