package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.util.List;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.module.uniform.UniformModule;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 품목군별 탄소계수(kg CO2e/벌)와 출처. 기본값은 해외 일반값(Carbonfact 중앙값)이라, ESG 보고에는 국내 인증 값으로 바꿔 쓸 수 있게 한다.
 * 바꾸면 탄소 감축량 지표가 다시 계산된다.
 */
@Service
public class CarbonFactorService {

    public record FactorView(UniformItemGroup group, String label, BigDecimal kgCo2e, String source) {
    }

    public record Change(UniformItemGroup group, BigDecimal kgCo2e, String source) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public CarbonFactorService(JdbcTemplate jdbc, AuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<FactorView> list() {
        return jdbc.query("select item_group, kg_co2e, source from carbon_factor", (rs, i) -> {
            UniformItemGroup g = UniformItemGroup.valueOf(rs.getString("item_group"));
            return new FactorView(g, g.label(), rs.getBigDecimal("kg_co2e"), rs.getString("source"));
        }).stream().sorted(java.util.Comparator.comparing(FactorView::group)).toList();
    }

    @Transactional
    public List<FactorView> update(List<Change> changes, String user) {
        for (Change c : changes) {
            if (c.group() == null || c.group() == UniformItemGroup.UNKNOWN) {
                throw new BusinessException("품목군을 지정하세요.");
            }
            if (c.kgCo2e() == null || c.kgCo2e().signum() <= 0 || c.kgCo2e().compareTo(new BigDecimal("1000")) > 0) {
                throw new BusinessException(c.group().label() + " 탄소계수는 0보다 크고 1,000 이하여야 합니다.");
            }
            String source = c.source() == null || c.source().isBlank() ? null : c.source().strip();
            if (source == null) {
                throw new BusinessException(c.group().label() + " 탄소계수의 출처를 적으세요(보고서에 표시됩니다).");
            }
            jdbc.update("""
                    insert into carbon_factor (item_group, kg_co2e, source) values (?, ?, ?)
                    on conflict (item_group) do update set kg_co2e = excluded.kg_co2e, source = excluded.source""",
                    c.group().name(), c.kgCo2e(), source);
            audit.record(user, "CARBON_FACTOR_UPDATE", c.group().name(), c.kgCo2e().toPlainString() + " kg CO2e (" + source + ")");
        }
        events.publishEvent(new EsgDataChangedEvent(UniformModule.CODE, "탄소계수 변경"));
        return list();
    }
}
