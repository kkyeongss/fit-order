package kr.fitorder.common.esg;

import java.time.OffsetDateTime;
import java.util.List;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.common.EsgModule;
import kr.fitorder.common.MetricCalculator;
import kr.fitorder.common.MetricValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 지표를 미리 계산해 esg_metric_value에 저장한다. 항목의 데이터가 바뀌면(EsgDataChangedEvent)
 * 커밋 직후 그 항목의 모든 지표를 dedup·raw 두 기준으로 다시 계산한다.
 */
@Service
public class MetricService {

    private static final Logger log = LoggerFactory.getLogger(MetricService.class);

    private final EsgModuleRegistry registry;
    private final EsgMetricValueRepository values;

    public MetricService(EsgModuleRegistry registry, EsgMetricValueRepository values) {
        this.registry = registry;
        this.values = values;
    }

    @TransactionalEventListener(fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onDataChanged(EsgDataChangedEvent event) {
        log.info("지표 재계산: {} ({})", event.itemCode(), event.reason());
        try {
            recalculate(event.itemCode());
        } catch (RuntimeException e) {
            // 원래 변경(적재·단가 등)은 이미 커밋됐다. 지표는 다음 변경이나 재시작 때 다시 계산된다.
            log.error("지표 재계산 실패: {} ({})", event.itemCode(), event.reason(), e);
        }
    }

    /** 항목의 지표 전체를 두 기준으로 다시 계산해 바꿔 넣는다. */
    @Transactional
    public void recalculate(String itemCode) {
        EsgModule module = module(itemCode);
        OffsetDateTime now = OffsetDateTime.now();
        for (MetricCalculator calculator : module.metrics()) {
            String code = calculator.definition().code();
            for (Basis basis : Basis.values()) {
                values.deleteByMetricCodeAndBasis(code, basis);
                values.flush();
                List<MetricValue> calculated = calculator.calculate(basis);
                values.saveAll(calculated.stream()
                        .map(v -> new EsgMetricValue(code, v.period(), v.dimension(), basis, v.value(), now))
                        .toList());
            }
        }
    }

    @Transactional
    public void recalculateAll() {
        registry.modules().forEach(m -> recalculate(m.code()));
    }

    @Transactional(readOnly = true)
    public List<EsgMetricValue> values(String itemCode, Basis basis) {
        List<String> codes = module(itemCode).metrics().stream().map(c -> c.definition().code()).toList();
        return values.findByMetricCodeInAndBasisOrderByMetricCodeAscPeriodAscDimensionAsc(codes, basis);
    }

    private EsgModule module(String itemCode) {
        return registry.module(itemCode)
                .orElseThrow(() -> new BusinessException("알 수 없는 ESG 항목입니다: " + itemCode));
    }
}
