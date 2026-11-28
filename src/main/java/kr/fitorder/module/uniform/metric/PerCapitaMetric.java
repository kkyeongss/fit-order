package kr.fitorder.module.uniform.metric;

import java.util.ArrayList;
import java.util.List;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 1인당 지급량 = 연간 주문량 ÷ 그해 마지막 기준일자 정원. 2021년부터(설정). */
@Component
@Order(5)
public class PerCapitaMetric extends UniformMetric {

    public PerCapitaMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "per-capita", "1인당 지급량", "벌/인");
    }

    @Override
    public List<MetricValue> calculate(UniformDataset data) {
        List<MetricValue> values = new ArrayList<>();
        for (int year : data.completeYears()) {
            analysis.perCapita(data, year)
                    .ifPresent(v -> values.add(new MetricValue(String.valueOf(year), ALL, decimal(v))));
        }
        return values;
    }
}
