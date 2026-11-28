package kr.fitorder.module.uniform.metric;

import java.util.ArrayList;
import java.util.List;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.OrderLine;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 연간 주문량(완전한 해만). 전체와 품목군별. */
@Component
@Order(1)
public class OrderQuantityMetric extends UniformMetric {

    public OrderQuantityMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "order-quantity", "연간 주문량", "벌");
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        List<MetricValue> values = new ArrayList<>();
        for (int year : data.completeYears()) {
            List<OrderLine> lines = data.lines(l -> l.year() == year);
            values.add(new MetricValue(String.valueOf(year), ALL, decimal(UniformAnalysis.quantity(lines))));
            for (UniformItemGroup group : data.groups()) {
                values.add(new MetricValue(String.valueOf(year), group.label(),
                        decimal(UniformAnalysis.quantity(lines.stream().filter(l -> l.group() == group).toList()))));
            }
        }
        return values;
    }
}
