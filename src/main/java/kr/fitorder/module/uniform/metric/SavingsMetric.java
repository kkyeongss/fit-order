package kr.fitorder.module.uniform.metric;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformAnalysis.Savings;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import kr.fitorder.module.uniform.domain.UniformItemGroup;

/** 절감 효과 지표 공통(연간 기준). 품목군별 값과 그 합계를 낸다. 기간은 미스매치율과 같은 최근 구간. */
abstract class SavingsMetric extends UniformMetric {

    private final Function<Savings, BigDecimal> value;

    SavingsMetric(UniformDatasetLoader loader, UniformAnalysis analysis, String code, String name, String unit,
                  Function<Savings, BigDecimal> value) {
        super(loader, analysis, code, name, unit);
        this.value = value;
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        List<MetricValue> values = new ArrayList<>();
        for (Comparison c : analysis.comparisons(data)) {
            BigDecimal total = BigDecimal.ZERO;
            for (UniformItemGroup group : data.groups()) {
                var savings = analysis.savings(data, group, c);
                if (savings.isPresent()) {
                    BigDecimal v = value.apply(savings.get());
                    values.add(new MetricValue(c.label(), group.label(), decimal(v)));
                    total = total.add(v);
                }
            }
            values.add(new MetricValue(c.label(), ALL, decimal(total)));
        }
        return values;
    }
}
