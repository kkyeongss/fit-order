package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.ItemPlan;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeLine;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import kr.fitorder.module.uniform.analysis.RecommendationService.Request;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * 저장한 발주안. 추천을 확정해 두었다가, 그 해 실제 주문이 데이터에 들어오면 계획과 실적을 비교한다.
 * '절감 효과'가 가정이 아니라 실제로 나타났는지 확인하는 용도다. 스냅샷은 저장할 때의 추천 결과 일부(JSON)다.
 */
@Service
public class PlanService {

    public record SizeSnap(String source, String rawSize, String size, int quantity, int orderQuantity) {
    }

    public record ItemSnap(String group, String label, int quantity, int orderQuantity, int stockTotal,
                           BigDecimal unitPrice, String priceSource, List<SizeSnap> sizes) {
    }

    /** 저장 시점의 추천 결과(필요 수량 기준 + 재고를 뺀 발주 수량). */
    public record Snapshot(int year, String basis, Integer headcount, int quantity, int orderQuantity,
                           BigDecimal amount, BigDecimal orderAmount, List<ItemSnap> items) {
    }

    public record PlanSummary(long id, int planYear, Basis basis, Integer headcount, String note, String savedBy,
                              OffsetDateTime savedAt, int quantity, int orderQuantity, BigDecimal orderAmount,
                              int itemCount) {
    }

    public record SaveRequest(Integer year, List<UniformItemGroup> groups, Integer headcount, Basis basis,
                              Map<UniformItemGroup, Integer> totals, String note) {
    }

    public record SizeCompare(String size, int planned, int actual, int difference) {
    }

    /** difference = 계획 − 실적. 양수면 계획이 더 많았다(남을 뻔), 음수면 모자랐다. */
    public record ItemCompare(String group, String label, int planned, int actual, int difference,
                              Double differencePercent, double sizeMismatchRate, List<SizeCompare> sizes) {
    }

    /**
     * @param hasActual        그해 주문 데이터가 있는가
     * @param yearComplete     그해 데이터가 1년치가 다 있는가(아니면 중간 비교)
     * @param sizeMismatchRate 사이즈별로 남거나 모자란 벌 수의 합 ÷ 실적(작을수록 계획이 실제와 가까웠다)
     */
    public record Compare(PlanSummary plan, boolean hasActual, boolean yearComplete, LocalDate lastOrderDate,
                          int plannedQuantity, int actualQuantity, int difference, Double differencePercent,
                          double sizeMismatchRate, List<ItemCompare> items, String message) {
    }

    private final JdbcTemplate jdbc;
    private final RecommendationService recommendations;
    private final UniformDatasetLoader loader;
    private final AuditService audit;
    private final JsonMapper mapper;

    public PlanService(JdbcTemplate jdbc, RecommendationService recommendations, UniformDatasetLoader loader,
                       AuditService audit, JsonMapper mapper) {
        this.jdbc = jdbc;
        this.recommendations = recommendations;
        this.loader = loader;
        this.audit = audit;
        this.mapper = mapper;
    }

    /** 지금 입력으로 추천을 다시 계산해 그 결과를 저장한다(화면에 보인 값과 같다). */
    @Transactional
    public PlanSummary save(SaveRequest request, String user) {
        Recommendation rec = recommendations.recommend(
                new Request(request.year(), request.groups(), request.headcount(), request.basis(), request.totals()));
        Snapshot snapshot = snapshot(rec);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        if (note != null && note.length() > 300) {
            throw new BusinessException("메모는 300자 이내로 쓰세요.");
        }
        Long id = jdbc.queryForObject("""
                insert into order_plan (plan_year, basis, headcount, note, saved_by, payload)
                values (?, ?, ?, ?, ?, ?) returning id""", Long.class, rec.year(), rec.basis().name(),
                rec.headcount(), note, user, mapper.writeValueAsString(snapshot));
        audit.record(user, "PLAN_SAVE", "plan/" + id, rec.year() + "년 발주안, 필요 " + snapshot.quantity() + "벌, 발주 "
                + snapshot.orderQuantity() + "벌");
        return summary(id);
    }

    @Transactional(readOnly = true)
    public List<PlanSummary> list() {
        return jdbc.query("select * from order_plan order by plan_year desc, id desc", (rs, i) -> toSummary(rs));
    }

    @Transactional(readOnly = true)
    public PlanSummary summary(long id) {
        List<PlanSummary> found = jdbc.query("select * from order_plan where id = ?", (rs, i) -> toSummary(rs), id);
        if (found.isEmpty()) {
            throw new BusinessException("저장한 발주안이 없습니다: " + id);
        }
        return found.getFirst();
    }

    /** 만든 사람이나 관리자만 지운다. */
    @Transactional
    public void delete(long id, String user, boolean admin) {
        PlanSummary plan = summary(id);
        if (!admin && !plan.savedBy().equals(user)) {
            throw new BusinessException("저장한 사람이나 관리자만 지울 수 있습니다.");
        }
        jdbc.update("delete from order_plan where id = ?", id);
        audit.record(user, "PLAN_DELETE", "plan/" + id, plan.planYear() + "년 발주안 삭제");
    }

    /** 계획과 그해 실제 주문을 비교한다(저장 때와 같은 집계 기준). */
    @Transactional(readOnly = true)
    public Compare compare(long id) {
        PlanSummary plan = summary(id);
        Snapshot snap = snapshotOf(id);
        UniformDataset data = loader.load(plan.basis());
        List<OrderLine> actualLines = data.lines(l -> l.year() == plan.planYear());
        LocalDate last = actualLines.stream().map(OrderLine::date).max(Comparator.naturalOrder()).orElse(null);
        boolean complete = data.completeYears().contains(plan.planYear());
        if (actualLines.isEmpty()) {
            return new Compare(plan, false, false, null, snap.quantity(), 0, snap.quantity(), null, 0, List.of(),
                    plan.planYear() + "년 주문 데이터가 아직 없습니다. 그해 파일을 불러오면 계획과 실제를 비교합니다.");
        }
        List<ItemCompare> items = new ArrayList<>();
        int plannedTotal = 0;
        int actualTotal = 0;
        double mismatchSum = 0;
        for (ItemSnap item : snap.items()) {
            UniformItemGroup group = UniformItemGroup.valueOf(item.group());
            List<OrderLine> lines = actualLines.stream().filter(l -> l.group() == group).toList();
            Map<String, Integer> actualBySize = new LinkedHashMap<>();
            Map<String, String> labels = new HashMap<>();
            for (OrderLine l : lines) {
                String key = key(l.source(), l.rawSize());
                actualBySize.merge(key, l.quantity(), Integer::sum);
                labels.putIfAbsent(key, l.size());
            }
            Map<String, Integer> plannedBySize = new LinkedHashMap<>();
            for (SizeSnap z : item.sizes()) {
                String key = key(z.source(), z.rawSize());
                plannedBySize.merge(key, z.quantity(), Integer::sum);
                labels.putIfAbsent(key, z.size());
            }
            Set<String> keys = new LinkedHashSet<>(plannedBySize.keySet());
            keys.addAll(actualBySize.keySet());
            List<SizeCompare> sizes = new ArrayList<>();
            double mismatch = 0;
            for (String k : keys) {
                int p = plannedBySize.getOrDefault(k, 0);
                int a = actualBySize.getOrDefault(k, 0);
                if (p == 0 && a == 0) {
                    continue;
                }
                sizes.add(new SizeCompare(labels.get(k), p, a, p - a));
                mismatch += Math.abs(p - a);
            }
            int actual = lines.stream().mapToInt(OrderLine::quantity).sum();
            plannedTotal += item.quantity();
            actualTotal += actual;
            mismatchSum += mismatch;
            items.add(new ItemCompare(item.group(), item.label(), item.quantity(), actual, item.quantity() - actual,
                    pct(item.quantity() - actual, actual), actual == 0 ? 0 : mismatch / actual, sizes));
        }
        String message = complete ? null : plan.planYear() + "년 주문이 " + last + "까지만 들어 있어 중간 비교입니다.";
        return new Compare(plan, true, complete, last, plannedTotal, actualTotal, plannedTotal - actualTotal,
                pct(plannedTotal - actualTotal, actualTotal), actualTotal == 0 ? 0 : mismatchSum / actualTotal, items, message);
    }

    // ---- 내부 ---------------------------------------------------------------------

    private static Double pct(int difference, int actual) {
        return actual == 0 ? null : difference / (double) actual;
    }

    private static String key(String source, String rawSize) {
        return source + "|" + (rawSize == null ? "" : rawSize);
    }

    private static Snapshot snapshot(Recommendation rec) {
        List<ItemSnap> items = new ArrayList<>();
        for (ItemPlan it : rec.items()) {
            List<SizeSnap> sizes = new ArrayList<>();
            for (SizeLine z : it.sizes()) {
                if (z.quantity() > 0 || z.stock() > 0) {
                    sizes.add(new SizeSnap(z.source(), z.rawSize() == null ? "" : z.rawSize(), z.size(), z.quantity(),
                            z.orderQuantity()));
                }
            }
            items.add(new ItemSnap(it.group().name(), it.label(), it.quantity(), it.orderQuantity(), it.stockTotal(),
                    it.unitPrice(), it.priceSource().name(), sizes));
        }
        return new Snapshot(rec.year(), rec.basis().name(), rec.headcount(), rec.totals().quantity(),
                rec.totals().orderQuantity(), rec.totals().amount(), rec.totals().orderAmount(), items);
    }

    private Snapshot snapshotOf(long id) {
        String payload = jdbc.queryForObject("select payload from order_plan where id = ?", String.class, id);
        return mapper.readValue(payload, Snapshot.class);
    }

    private PlanSummary toSummary(java.sql.ResultSet rs) throws java.sql.SQLException {
        Snapshot snap = mapper.readValue(rs.getString("payload"), Snapshot.class);
        return new PlanSummary(rs.getLong("id"), rs.getInt("plan_year"), Basis.valueOf(rs.getString("basis")),
                (Integer) rs.getObject("headcount"), rs.getString("note"), rs.getString("saved_by"),
                rs.getObject("saved_at", OffsetDateTime.class), snap.quantity(), snap.orderQuantity(), snap.orderAmount(),
                snap.items().size());
    }
}
