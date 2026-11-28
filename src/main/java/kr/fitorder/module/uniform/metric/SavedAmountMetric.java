package kr.fitorder.module.uniform.metric;

import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformAnalysis.Savings;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 절감액 = 절감 수량 × 품목군별 단가(입력값이 없으면 추정 단가). */
@Component
@Order(7)
public class SavedAmountMetric extends SavingsMetric {

    public SavedAmountMetric(UniformDatasetLoader loader, UniformAnalysis analysis) {
        super(loader, analysis, "saved-amount", "절감액", "원/년", Savings::savedAmount);
    }
}
