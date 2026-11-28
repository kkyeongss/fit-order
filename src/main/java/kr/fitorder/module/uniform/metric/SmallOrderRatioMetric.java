package kr.fitorder.module.uniform.metric;

import java.util.ArrayList;
import java.util.List;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 소량 발주 비율: 하루 주문량이 기준(기본 10벌) 이하인 주문일 ÷ 전체 주문일. */
@Component
@Order(4)
public class SmallOrderRatioMetric extends UniformMetric {

    public SmallOrderRatioMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "small-order-ratio", "소량 발주 비율", "%");
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        List<MetricValue> values = new ArrayList<>();
        for (int year : data.completeYears()) {
            analysis.smallOrderRatio(data, year)
                    .ifPresent(r -> values.add(new MetricValue(String.valueOf(year), ALL, percent(r))));
        }
        return values;
    }
}
