package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.Basis;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.cleansing.SizeMappingService;
import kr.fitorder.module.uniform.cleansing.SizeMappingService.SizeLabeler;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 활성 적재 데이터(uniform_order_active, headcount_active)와 단가·탄소계수를 읽어 UniformDataset을 만든다. */
@Component
public class UniformDatasetLoader {

    private final JdbcTemplate jdbc;
    private final SizeMappingService sizeMappings;
    private final UnitPriceService prices;

    public UniformDatasetLoader(JdbcTemplate jdbc, SizeMappingService sizeMappings, UnitPriceService prices) {
        this.jdbc = jdbc;
        this.sizeMappings = sizeMappings;
        this.prices = prices;
    }

    /** 수량·주문일이 빈 행은 집계에서 뺀다. DEDUP이면 중복 표시된 행도 뺀다. */
    @Transactional(readOnly = true)
    public UniformDataset load(Basis basis) {
        SizeLabeler labeler = sizeMappings.labeler();
        String sql = """
                select source, order_date, item_group, raw_size, quantity, duplicate
                from uniform_order_active
                where quantity is not null and order_date is not null""" + (basis == Basis.DEDUP ? " and not duplicate" : "");
        List<OrderLine> lines = jdbc.query(sql, (rs, i) -> {
            String source = rs.getString("source");
            String rawSize = rs.getString("raw_size");
            return new OrderLine(source, rs.getObject("order_date", java.time.LocalDate.class),
                    UniformItemGroup.valueOf(rs.getString("item_group")), rawSize, labeler.label(source, rawSize),
                    rs.getInt("quantity"), rs.getBoolean("duplicate"));
        });
        return new UniformDataset(basis, lines, headcounts(), prices.current(), carbon());
    }

    /** 연도별 정원: 그해 마지막 기준일자의 전체 정원. */
    private Map<Integer, YearHeadcount> headcounts() {
        Map<Integer, YearHeadcount> result = new HashMap<>();
        jdbc.query("""
                select base_date, sum(quota) as total
                from headcount_active
                where base_date in (select max(base_date) from headcount_active group by extract(year from base_date))
                group by base_date""", rs -> {
            var date = rs.getObject("base_date", java.time.LocalDate.class);
            result.put(date.getYear(), new YearHeadcount(date.getYear(), date, rs.getInt("total")));
        });
        return result;
    }

    private Map<UniformItemGroup, BigDecimal> carbon() {
        Map<UniformItemGroup, BigDecimal> result = new EnumMap<>(UniformItemGroup.class);
        jdbc.query("select item_group, kg_co2e from carbon_factor",
                rs -> {
                    result.put(UniformItemGroup.valueOf(rs.getString("item_group")), rs.getBigDecimal("kg_co2e"));
                });
        return result;
    }
}
