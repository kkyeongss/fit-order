package kr.fitorder.common;

import java.util.List;

/**
 * ESG 항목 하나.
 * 구현 클래스를 @Component로 등록하면 esg_item에 자동 등록되고 화면·API에 나타난다.
 */
public interface EsgModule {

    /** 항목 코드. esg_item.code */
    String code();

    String name();

    EsgCategory category();

    /** esg_item에 처음 등록될 때의 적재 방식. 이후에는 esg_item.load_method가 우선한다. */
    LoadMethod defaultLoadMethod();

    /** 이 항목이 받는 원천 데이터별 적재기 */
    List<SourceLoader> loaders();

    /** 이 항목의 지표 계산기 */
    List<MetricCalculator> metrics();
}
