package kr.fitorder.common.esg;

import kr.fitorder.common.EsgModule;
import kr.fitorder.common.MetricCalculator;
import kr.fitorder.common.MetricDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시작할 때 코드에 있는 모듈·지표를 esg_item, esg_metric에 등록한다.
 * 새 모듈은 클래스만 추가하면 DB에 따로 넣지 않아도 나타난다.
 * 이미 있는 항목의 사용 여부·적재 방식은 관리 화면 값을 그대로 둔다.
 */
@Component
public class EsgCatalogSynchronizer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EsgCatalogSynchronizer.class);

    private final EsgModuleRegistry registry;
    private final EsgItemRepository items;
    private final EsgMetricRepository metrics;

    public EsgCatalogSynchronizer(EsgModuleRegistry registry, EsgItemRepository items, EsgMetricRepository metrics) {
        this.registry = registry;
        this.items = items;
        this.metrics = metrics;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        for (EsgModule module : registry.modules()) {
            if (!items.existsById(module.code())) {
                items.save(new EsgItem(module.code(), module.name(), module.category(), module.defaultLoadMethod()));
                log.info("ESG 항목 등록: {} ({})", module.code(), module.name());
            }
            for (MetricCalculator calculator : module.metrics()) {
                MetricDefinition def = calculator.definition();
                metrics.findById(def.code()).ifPresentOrElse(
                        existing -> existing.update(def.name(), def.unit()),
                        () -> metrics.save(new EsgMetric(def.code(), module.code(), def.name(), def.unit())));
            }
        }
    }
}
