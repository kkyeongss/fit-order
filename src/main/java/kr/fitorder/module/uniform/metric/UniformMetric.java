package kr.fitorder.module.uniform.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import kr.fitorder.common.Basis;
import kr.fitorder.common.MetricCalculator;
import kr.fitorder.common.MetricDefinition;
import kr.fitorder.common.MetricValue;
import kr.fitorder.module.uniform.UniformModule;
import kr.fitorder.module.uniform.analysis.UniformAnalysis;
import kr.fitorder.module.uniform.analysis.UniformDataset;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;

/** 근무복 지표 공통: 기준(basis)별 데이터를 읽어 계산식에 넘긴다. 계산식은 UniformAnalysis에 있다. */
abstract class UniformMetric implements MetricCalculator {

    /** 전체(품목군 구분 없음) */
    static final String ALL = "";

    private final UniformDatasetLoader loader;
    protected final UniformAnalysis analysis;
    private final MetricDefinition definition;

    UniformMetric(UniformDatasetLoader loader, UniformAnalysis analysis, String code, String name, String unit) {
        this.loader = loader;
        this.analysis = analysis;
        this.definition = new MetricDefinition(UniformModule.CODE + "." + code, name, unit);
    }

    @Override
    public final String itemCode() {
        return UniformModule.CODE;
    }

    @Override
    public final MetricDefinition definition() {
        return definition;
    }

    @Override
    public final List<MetricValue> calculate(Basis basis) {
        return calculate(loader.load(basis));
    }

    /** 이미 읽은 데이터로 계산한다(테스트·일괄 계산용). */
    public abstract List<MetricValue> calculate(UniformDataset data);

    static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal decimal(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    static BigDecimal percent(double ratio) {
        return decimal(ratio * 100);
    }
}
