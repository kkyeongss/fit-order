package kr.fitorder.module.uniform.metric;

import java.util.ArrayList;
import java.util.List;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 사이즈 미스매치율 = Σ max(0, 최근 구간 비율 − 이전 구간 비율). 기간은 최근 구간('2023-2025').
 * 전체 값은 품목군별 값을 최근 구간 주문량으로 가중 평균한다.
 */
@Component
@Order(2)
public class SizeMismatchRateMetric extends UniformMetric {

    public SizeMismatchRateMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "size-mismatch-rate", "사이즈 미스매치율", "%");
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        List<MetricValue> values = new ArrayList<>();
        for (Comparison c : analysis.comparisons(data)) {
            double weighted = 0;
            double quantity = 0;
            for (UniformItemGroup group : data.groups()) {
                var savings = analysis.savings(data, group, c);
                if (savings.isPresent()) {
                    values.add(new MetricValue(c.label(), group.label(), percent(savings.get().mismatchRate())));
                    weighted += savings.get().mismatchRate() * savings.get().annualQuantity();
                    quantity += savings.get().annualQuantity();
                }
            }
            if (quantity > 0) {
                values.add(new MetricValue(c.label(), ALL, percent(weighted / quantity)));
            }
        }
        return values;
    }
}
