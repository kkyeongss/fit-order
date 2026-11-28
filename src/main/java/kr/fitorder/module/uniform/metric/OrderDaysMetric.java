package kr.fitorder.module.uniform.metric;

import java.util.List;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 연간 발주 일수: 두 데이터를 합친 고유 주문일 수. */
@Component
@Order(3)
public class OrderDaysMetric extends UniformMetric {

    public OrderDaysMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "order-days", "연간 발주 일수", "일");
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        return data.completeYears().stream()
                .map(y -> new MetricValue(String.valueOf(y), ALL, decimal(UniformAnalysis.orderDays(data, y))))
                .toList();
    }
}
