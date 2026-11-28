package kr.fitorder.module.uniform.metric;

import java.math.BigDecimal;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 절감 수량 = 미스매치율 × 연간 주문량 × 개선률(기본 50%). */
@Component
@Order(6)
public class SavedQuantityMetric extends SavingsMetric {

    public SavedQuantityMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "saved-quantity", "절감 수량", "벌/년", s -> BigDecimal.valueOf(s.savedQuantity()));
    }
}
