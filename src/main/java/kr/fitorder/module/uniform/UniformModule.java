package kr.fitorder.module.uniform;

import java.util.List;
import kr.fitorder.common.EsgCategory;
import kr.fitorder.common.EsgModule;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.MetricCalculator;
import kr.fitorder.common.QualityChecker;
import kr.fitorder.common.SourceLoader;
import kr.fitorder.module.uniform.domain.HeadcountRepository;
import kr.fitorder.module.uniform.domain.UniformOrder;
import kr.fitorder.module.uniform.domain.UniformOrderRepository;
import kr.fitorder.module.uniform.loader.HeadcountLoader;
import kr.fitorder.module.uniform.loader.ItemClassifier;
import kr.fitorder.module.uniform.loader.UniformOrderLoader;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/** 근무복: 첫 번째 ESG 항목. 수요 예측으로 과잉 발주·폐기를 줄여 환경(E) 성과를 낸다. */
@Component
public class UniformModule implements EsgModule {

    public static final String CODE = "uniform";

    private final List<SourceLoader> loaders;
    private final ObjectProvider<MetricCalculator> calculators;

    public UniformModule(ItemClassifier classifier, List<QualityChecker<UniformOrder>> checkers,
                         UniformOrderRepository orders, HeadcountRepository headcounts,
                         ObjectProvider<MetricCalculator> calculators) {
        this.loaders = List.of(
                new UniformOrderLoader(UniformSources.ORDER, classifier, checkers, orders),
                new UniformOrderLoader(UniformSources.ORDER_INFO, classifier, checkers, orders),
                new HeadcountLoader(headcounts));
        this.calculators = calculators;
    }

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "근무복";
    }

    @Override
    public EsgCategory category() {
        return EsgCategory.E;
    }

    @Override
    public LoadMethod defaultLoadMethod() {
        return LoadMethod.UPLOAD;
    }

    @Override
    public List<SourceLoader> loaders() {
        return loaders;
    }

    @Override
    public List<MetricCalculator> metrics() {
        return calculators.orderedStream().filter(c -> CODE.equals(c.itemCode())).toList();
    }
}
