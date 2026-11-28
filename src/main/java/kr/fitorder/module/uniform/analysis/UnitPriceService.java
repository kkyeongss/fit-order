package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.module.uniform.UniformModule;
import kr.fitorder.module.uniform.analysis.UniformDataset.UnitPrice;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 품목군별 단가. unit_price.item에는 품목군 코드(SHIRT 등)를 넣고, 적용시작일이 가장 늦은 값을 쓴다.
 * 입력값이 없는 품목군은 설정의 추정 단가를 쓰고 '추정'으로 표시한다.
 */
@Service
public class UnitPriceService {

    public record PriceView(UniformItemGroup group, String label, BigDecimal price, boolean estimated,
                            LocalDate validFrom, String enteredBy) {
    }

    public record Change(UniformItemGroup group, BigDecimal price, Boolean estimated) {
    }

    private final JdbcTemplate jdbc;
    private final AnalysisProperties props;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public UnitPriceService(JdbcTemplate jdbc, AnalysisProperties props, AuditService audit,
                            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.props = props;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<PriceView> list() {
        Map<UniformItemGroup, PriceView> entered = new EnumMap<>(UniformItemGroup.class);
        jdbc.query("""
                select distinct on (item) item, price, estimated, valid_from, entered_by
                from unit_price where valid_from <= current_date
                order by item, valid_from desc, id desc""", rs -> {
            UniformItemGroup group = UniformItemGroup.valueOf(rs.getString("item"));
            entered.put(group, new PriceView(group, group.label(), rs.getBigDecimal("price"),
                    rs.getBoolean("estimated"), rs.getObject("valid_from", LocalDate.class),
                    rs.getString("entered_by")));
        });
        return Arrays.stream(UniformItemGroup.values())
                .filter(g -> g != UniformItemGroup.UNKNOWN)
                .map(g -> entered.getOrDefault(g,
                        new PriceView(g, g.label(), props.defaultUnitPrice(), true, null, null)))
                .toList();
    }

    public Map<UniformItemGroup, UnitPrice> current() {
        Map<UniformItemGroup, UnitPrice> result = new EnumMap<>(UniformItemGroup.class);
        list().forEach(p -> result.put(p.group(), new UnitPrice(p.price(), p.estimated())));
        result.put(UniformItemGroup.UNKNOWN, new UnitPrice(props.defaultUnitPrice(), true));
        return result;
    }

    /** 오늘부터 적용되는 단가를 넣는다. 이전 값은 이력으로 남는다. */
    @Transactional
    public List<PriceView> update(List<Change> changes, String user) {
        for (Change change : changes) {
            if (change.group() == null || change.group() == UniformItemGroup.UNKNOWN) {
                throw new BusinessException("품목군을 지정하세요.");
            }
            if (change.price() == null || change.price().signum() <= 0) {
                throw new BusinessException(change.group().label() + " 단가는 0보다 커야 합니다.");
            }
            boolean estimated = Boolean.TRUE.equals(change.estimated());
            jdbc.update("insert into unit_price (item, price, valid_from, estimated, entered_by) values (?, ?, ?, ?, ?)",
                    change.group().name(), change.price(), LocalDate.now(), estimated, user);
            audit.record(user, "PRICE_UPDATE", change.group().name(),
                    change.price().toPlainString() + "원" + (estimated ? " (추정)" : ""));
        }
        events.publishEvent(new EsgDataChangedEvent(UniformModule.CODE, "단가 변경"));
        return list();
    }
}
