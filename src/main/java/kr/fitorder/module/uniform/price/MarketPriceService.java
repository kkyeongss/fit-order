package kr.fitorder.module.uniform.price;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * 조달청 쇼핑몰 근무복 계약가격을 품목군별 중앙값으로 요약해 시장가로 둔다.
 * 규격명 키워드로 품목군을 나누고, 환경표지제품 수와 가격도 함께 센다(녹색구매 비교용).
 */
@Service
public class MarketPriceService {

    public enum Method {
        API, UPLOAD
    }

    /** 품목군 하나의 시장가. usable=false면 표본이 적어 단가로 쓰지 않는다. */
    public record MarketPrice(UniformItemGroup group, String label, int sampleCount, BigDecimal medianPrice,
                              BigDecimal q1Price, BigDecimal q3Price, int ecoCount, BigDecimal ecoMedianPrice,
                              boolean usable, Method method, LocalDate periodFrom, LocalDate periodTo,
                              String fetchedBy, OffsetDateTime fetchedAt) {
    }

    /** 가져오기 결과: 받은 상품 수, 품목군을 못 정한 상품 수와 예시. */
    public record FetchResult(int itemCount, int unclassifiedCount, List<String> unclassifiedExamples,
                              List<MarketPrice> prices) {
    }

    private final ProcurementMallSource api;
    private final MarketPriceProperties props;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public MarketPriceService(ProcurementMallSource api, MarketPriceProperties props, JdbcTemplate jdbc,
                              AuditService audit, ApplicationEventPublisher events) {
        this.api = api;
        this.props = props;
        this.jdbc = jdbc;
        this.audit = audit;
        this.events = events;
    }

    @Transactional
    public FetchResult fetch(String user) {
        LocalDate to = LocalDate.now();
        return save(api.fetch(), Method.API, to.minusMonths(props.months()), to, user);
    }

    /** 망분리 환경: 같은 항목을 CSV로 올린다. 기간은 알 수 없어 비워 둔다. */
    @Transactional
    public FetchResult upload(byte[] content, String user) {
        return save(MarketItemCsvReader.read(content), Method.UPLOAD, null, null, user);
    }

    @Transactional(readOnly = true)
    public List<MarketPrice> list() {
        return jdbc.query("select * from market_price order by item_group", (rs, i) -> {
            UniformItemGroup group = UniformItemGroup.valueOf(rs.getString("item_group"));
            int samples = rs.getInt("sample_count");
            return new MarketPrice(group, group.label(), samples, rs.getBigDecimal("median_price"),
                    rs.getBigDecimal("q1_price"), rs.getBigDecimal("q3_price"), rs.getInt("eco_count"),
                    rs.getBigDecimal("eco_median_price"), samples >= props.minSamples(),
                    Method.valueOf(rs.getString("method")), rs.getObject("period_from", LocalDate.class),
                    rs.getObject("period_to", LocalDate.class), rs.getString("fetched_by"),
                    rs.getObject("fetched_at", OffsetDateTime.class));
        }).stream().sorted(java.util.Comparator.comparing(MarketPrice::group)).toList();
    }

    public Optional<MarketPrice> usable(UniformItemGroup group) {
        return list().stream().filter(p -> p.group() == group && p.usable()).findFirst();
    }

    private FetchResult save(List<MarketItem> items, Method method, LocalDate from, LocalDate to, String user) {
        if (items.isEmpty()) {
            throw new BusinessException("받은 근무복 계약상품이 없습니다.");
        }
        Map<UniformItemGroup, List<MarketItem>> byGroup = new EnumMap<>(UniformItemGroup.class);
        List<String> unclassified = new ArrayList<>();
        for (MarketItem item : items) {
            classify(item.specName()).ifPresentOrElse(
                    g -> byGroup.computeIfAbsent(g, k -> new ArrayList<>()).add(item),
                    () -> unclassified.add(item.specName()));
        }
        jdbc.update("delete from market_price");
        List<MarketPrice> saved = new ArrayList<>();
        byGroup.forEach((group, list) -> {
            List<BigDecimal> prices = list.stream().map(MarketItem::price).sorted().toList();
            List<BigDecimal> eco = list.stream().filter(MarketItem::eco).map(MarketItem::price).sorted().toList();
            BigDecimal ecoMedian = eco.isEmpty() ? null : quantile(eco, 0.5);
            jdbc.update("""
                    insert into market_price (item_group, sample_count, median_price, q1_price, q3_price, eco_count,
                        eco_median_price, method, period_from, period_to, fetched_by)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    group.name(), prices.size(), quantile(prices, 0.5), quantile(prices, 0.25),
                    quantile(prices, 0.75), eco.size(), ecoMedian, method.name(), from, to, user);
        });
        saved.addAll(list());
        audit.record(user, "MARKET_PRICE_" + method.name(), "pps-shopping-mall",
                items.size() + "개 상품, 품목군 " + byGroup.size() + "개, 미분류 " + unclassified.size());
        events.publishEvent(new EsgDataChangedEvent(UniformModule.CODE, "시장가 갱신"));
        return new FetchResult(items.size(), unclassified.size(),
                unclassified.stream().distinct().limit(10).toList(), saved);
    }

    /** 규격명에서 품목군을 정한다. 묶음 상품(상·하의 한 벌)은 뺀다. */
    Optional<UniformItemGroup> classify(String specName) {
        // 규격명은 '품명, 업체, 모델, 설명' 순서다. 업체명·모델명에 든 말에 걸리지 않게 설명만 본다.
        String[] parts = specName.split(",", 4);
        return classifyText(parts.length == 4 ? parts[3] : specName);
    }

    /** 설명 글에서 품목군을 정한다(녹색제품 제품명에도 같은 규칙을 쓴다). */
    public Optional<UniformItemGroup> classifyText(String desc) {
        if (desc == null || props.excludeKeywords().stream().anyMatch(desc::contains)) {
            return Optional.empty();
        }
        return props.rules().stream()
                .filter(r -> r.keywords().stream().anyMatch(desc::contains))
                .map(MarketPriceProperties.Rule::group)
                .findFirst();
    }

    /** 선형 보간 분위수(정렬된 값). 원 단위로 반올림. */
    static BigDecimal quantile(List<BigDecimal> sorted, double q) {
        double pos = (sorted.size() - 1) * q;
        int lo = (int) Math.floor(pos);
        int hi = (int) Math.ceil(pos);
        BigDecimal a = sorted.get(lo);
        BigDecimal b = sorted.get(hi);
        return a.add(b.subtract(a).multiply(BigDecimal.valueOf(pos - lo))).setScale(0, RoundingMode.HALF_UP);
    }
}
