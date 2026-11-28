package kr.fitorder.module.uniform.metric;

import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformAnalysis.Savings;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 탄소 감축량 = 절감 수량 × 품목군별 탄소계수(carbon_factor). */
@Component
@Order(8)
public class CarbonReductionMetric extends SavingsMetric {

    public CarbonReductionMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "carbon-reduction", "탄소 감축량", "kg CO2e/년", Savings::carbonKg);
    }
}
