package kr.fitorder.common;

import java.util.List;

/** 지표 하나를 계산한다. */
public interface MetricCalculator {

    /** 이 지표가 속한 ESG 항목 코드 */
    String itemCode();

    MetricDefinition definition();

    List<MetricValue> calculate(Basis basis);
}
