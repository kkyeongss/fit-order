package kr.fitorder.module.uniform.analysis;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ResultsChangedEvent;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 재고. 담당자가 품목·사이즈별로 입력하면 발주 수량(= 추천 수량 − 재고)에 반영된다.
 * 재고가 바뀌면 추천 결과 캐시를 버린다(지표에는 영향이 없다).
 */
@Service
public class StockService {

    /** 사이즈 하나의 키. 사이즈가 없는 행은 rawSize가 빈 문자열이다. */
    public record StockKey(String group, String source, String rawSize) {

        public static StockKey of(UniformItemGroup group, String source, String rawSize) {
            return new StockKey(group.name(), source, rawSize == null ? "" : rawSize);
        }
    }

    public record StockRow(UniformItemGroup group, String source, String rawSize, int quantity, String updatedBy,
                           OffsetDateTime updatedAt) {
    }

    /** quantity가 null이거나 0이면 그 사이즈의 재고 입력을 지운다. */
    public record Change(UniformItemGroup group, String source, String rawSize, Integer quantity) {
    }

    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public StockService(JdbcTemplate jdbc, AuditService audit, ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Map<StockKey, Integer> current() {
        Map<StockKey, Integer> result = new HashMap<>();
        jdbc.query("select item_group, source, raw_size, quantity from stock", rs -> {
            result.put(new StockKey(rs.getString("item_group"), rs.getString("source"), rs.getString("raw_size")),
                    rs.getInt("quantity"));
        });
        return result;
    }

    @Transactional(readOnly = true)
    public List<StockRow> list() {
        return jdbc.query("select * from stock order by item_group, source, raw_size", (rs, i) -> new StockRow(
                UniformItemGroup.valueOf(rs.getString("item_group")), rs.getString("source"), rs.getString("raw_size"),
                rs.getInt("quantity"), rs.getString("updated_by"), rs.getObject("updated_at", OffsetDateTime.class)));
    }

    @Transactional
    public List<StockRow> update(List<Change> changes, String user) {
        if (changes == null || changes.isEmpty()) {
            throw new BusinessException("바꿀 재고가 없습니다.");
        }
        int saved = 0;
        int cleared = 0;
        for (Change c : changes) {
            if (c.group() == null || c.group() == UniformItemGroup.UNKNOWN || c.source() == null || c.source().isBlank()) {
                throw new BusinessException("품목군과 출처를 지정하세요.");
            }
            int quantity = c.quantity() == null ? 0 : c.quantity();
            if (quantity < 0) {
                throw new BusinessException("재고는 0 이상이어야 합니다.");
            }
            if (quantity > 1_000_000) {
                throw new BusinessException("재고 수량이 너무 큽니다: " + quantity);
            }
            StockKey key = StockKey.of(c.group(), c.source(), c.rawSize());
            if (quantity == 0) {
                cleared += jdbc.update("delete from stock where item_group = ? and source = ? and raw_size = ?",
                        key.group(), key.source(), key.rawSize());
            } else {
                jdbc.update("""
                        insert into stock (item_group, source, raw_size, quantity, updated_by)
                        values (?, ?, ?, ?, ?)
                        on conflict (item_group, source, raw_size)
                        do update set quantity = excluded.quantity, updated_by = excluded.updated_by, updated_at = now()""",
                        key.group(), key.source(), key.rawSize(), quantity, user);
                saved++;
            }
        }
        audit.record(user, "STOCK_UPDATE", "stock", "입력 " + saved + "건, 삭제 " + cleared + "건");
        events.publishEvent(new ResultsChangedEvent("재고 변경"));
        return list();
    }
}
