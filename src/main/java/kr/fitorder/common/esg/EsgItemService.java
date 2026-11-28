package kr.fitorder.common.esg;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.EsgCategory;
import kr.fitorder.common.LoadMethod;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** ESG 항목 목록·설정과 항목별 저장된 지표. */
@Service
public class EsgItemService {

    public record MetricInfo(String code, String name, String unit) {
    }

    public record ItemView(String code, String name, EsgCategory category, boolean enabled, LoadMethod loadMethod,
                           List<MetricInfo> metrics) {
    }

    public record Change(Boolean enabled, LoadMethod loadMethod) {
    }

    public record Value(String period, String dimension, BigDecimal value) {
    }

    public record MetricValues(String code, String name, String unit, Basis basis, OffsetDateTime calculatedAt,
                               List<Value> values) {
    }

    private final EsgItemRepository items;
    private final EsgMetricRepository metrics;
    private final MetricService metricService;
    private final AuditService audit;

    public EsgItemService(EsgItemRepository items, EsgMetricRepository metrics, MetricService metricService,
                          AuditService audit) {
        this.items = items;
        this.metrics = metrics;
        this.metricService = metricService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ItemView> list() {
        return items.findAll().stream().sorted(Comparator.comparing(EsgItem::getCode)).map(this::view).toList();
    }

    @Transactional
    public ItemView update(String code, Change change, String user) {
        EsgItem item = items.findById(code).orElseThrow(() -> new BusinessException("알 수 없는 ESG 항목입니다: " + code));
        boolean enabled = change.enabled() != null ? change.enabled() : item.isEnabled();
        LoadMethod method = change.loadMethod() != null ? change.loadMethod() : item.getLoadMethod();
        item.change(enabled, method);
        audit.record(user, "ESG_ITEM_UPDATE", code, "사용 " + (enabled ? "예" : "아니오") + ", 적재 방식 " + method);
        return view(item);
    }

    /**
     * 저장된 지표 값(미리 계산된 값만 읽는다).
     *
     * @param period 기간으로 거르기. 비우면 전체
     */
    @Transactional(readOnly = true)
    public List<MetricValues> metrics(String code, Basis basis, String period) {
        Map<String, List<EsgMetricValue>> byMetric = new LinkedHashMap<>();
        metricService.values(code, basis).stream()
                .filter(v -> period == null || period.isBlank() || v.getPeriod().equals(period))
                .forEach(v -> byMetric.computeIfAbsent(v.getMetricCode(), k -> new java.util.ArrayList<>()).add(v));
        return metrics.findByItemCodeOrderByCode(code).stream()
                .map(m -> {
                    List<EsgMetricValue> values = byMetric.getOrDefault(m.getCode(), List.of());
                    return new MetricValues(m.getCode(), m.getName(), m.getUnit(), basis,
                            values.stream().map(EsgMetricValue::getCalculatedAt).max(Comparator.naturalOrder())
                                    .orElse(null),
                            values.stream().map(v -> new Value(v.getPeriod(), v.getDimension(), v.getValue())).toList());
                })
                .toList();
    }

    private ItemView view(EsgItem item) {
        return new ItemView(item.getCode(), item.getName(), item.getCategory(), item.isEnabled(), item.getLoadMethod(),
                metrics.findByItemCodeOrderByCode(item.getCode()).stream()
                        .map(m -> new MetricInfo(m.getCode(), m.getName(), m.getUnit())).toList());
    }
}
