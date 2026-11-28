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
import kr.fitorder.module.uniform.analysis.UniformDataset.PriceSource;
import kr.fitorder.module.uniform.analysis.UniformDataset.UnitPrice;
import kr.fitorder.module.uniform.price.MarketPriceService;
import kr.fitorder.module.uniform.price.MarketPriceService.MarketPrice;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 품목군별 단가. 우선순위: 직접 입력(unit_price, 적용시작일이 가장 늦은 값) > 조달청 쇼핑몰 시장가(표본이 충분할 때)
 * > 설정의 추정 단가. unit_price.item에는 품목군 코드(SHIRT 등)를 넣는다.
 */
@Service
public class UnitPriceService {

    /**
     * @param note 출처 설명. 예) '조달청 쇼핑몰 근무복 182건 중앙값'
     */
    public record PriceView(UniformItemGroup group, String label, BigDecimal price, PriceSource source,
                            String sourceLabel, String note, LocalDate validFrom, String enteredBy) {

        public boolean estimated() {
            return source == PriceSource.ESTIMATE;
        }
    }

    public record Change(UniformItemGroup group, BigDecimal price) {
    }

    private final JdbcTemplate jdbc;
    private final AnalysisProperties props;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final MarketPriceService market;

    public UnitPriceService(JdbcTemplate jdbc, AnalysisProperties props, AuditService audit,
                            ApplicationEventPublisher events, MarketPriceService market) {
        this.jdbc = jdbc;
        this.props = props;
        this.audit = audit;
        this.events = events;
        this.market = market;
    }

    @Transactional(readOnly = true)
    public List<PriceView> list() {
        Map<UniformItemGroup, PriceView> entered = new EnumMap<>(UniformItemGroup.class);
        jdbc.query("""
                select distinct on (item) item, price, valid_from, entered_by
                from unit_price where valid_from <= current_date
                order by item, valid_from desc, id desc""", rs -> {
            UniformItemGroup group = UniformItemGroup.valueOf(rs.getString("item"));
            entered.put(group, new PriceView(group, group.label(), rs.getBigDecimal("price"), PriceSource.MANUAL,
                    PriceSource.MANUAL.label(), "직접 입력", rs.getObject("valid_from", LocalDate.class),
                    rs.getString("entered_by")));
        });
        Map<UniformItemGroup, MarketPrice> markets = new EnumMap<>(UniformItemGroup.class);
        market.list().stream().filter(MarketPrice::usable).forEach(m -> markets.put(m.group(), m));
        return Arrays.stream(UniformItemGroup.values())
                .filter(g -> g != UniformItemGroup.UNKNOWN)
                .map(g -> entered.containsKey(g) ? entered.get(g)
                        : markets.containsKey(g) ? marketView(markets.get(g))
                        : new PriceView(g, g.label(), props.defaultUnitPrice(), PriceSource.ESTIMATE,
                                PriceSource.ESTIMATE.label(), "시장가 표본이 부족해 설정의 추정 단가", null, null))
                .toList();
    }

    private static PriceView marketView(MarketPrice m) {
        return new PriceView(m.group(), m.label(), m.medianPrice(), PriceSource.MARKET, PriceSource.MARKET.label(),
                String.format("조달청 쇼핑몰 근무복 %d건 중앙값(%,.0f~%,.0f원)", m.sampleCount(), m.q1Price(), m.q3Price()),
                m.periodTo(), m.fetchedBy());
    }

    public Map<UniformItemGroup, UnitPrice> current() {
        Map<UniformItemGroup, UnitPrice> result = new EnumMap<>(UniformItemGroup.class);
        list().forEach(p -> result.put(p.group(), new UnitPrice(p.price(), p.source())));
        result.put(UniformItemGroup.UNKNOWN, new UnitPrice(props.defaultUnitPrice(), PriceSource.ESTIMATE));
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
            jdbc.update("insert into unit_price (item, price, valid_from, estimated, entered_by) values (?, ?, ?, false, ?)",
                    change.group().name(), change.price(), LocalDate.now(), user);
            audit.record(user, "PRICE_UPDATE", change.group().name(), change.price().toPlainString() + "원");
        }
        events.publishEvent(new EsgDataChangedEvent(UniformModule.CODE, "단가 변경"));
        return list();
    }
}
