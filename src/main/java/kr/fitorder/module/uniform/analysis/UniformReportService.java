package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.stream.Collectors;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ResultCache;
import kr.fitorder.ingestion.DataSourceService;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import kr.fitorder.module.uniform.analysis.UniformDataset.PriceSource;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 화면용 집계: 연도·월·품목별 주문량, 사이즈 비율, 정원. 저장하지 않고 그때그때 계산한다. */
@Service
@Transactional(readOnly = true)
public class UniformReportService {

    /**
     * @param amount 그해 주문 수량 x 현재 품목군별 단가(입력 > 조달청 시장가 > 추정)로 본 추정 구매 금액. 실제 지출액이 아니다
     */
    public record YearSummary(int year, boolean complete, int quantity, int uniqueQuantity, int duplicateQuantity,
                              int orderDays, Map<String, Integer> byGroup, BigDecimal amount) {
    }

    public record LoadInfo(String source, String name, Integer baseYear, String fileName, OffsetDateTime loadedAt) {
    }

    /** 숫자가 언제까지의 데이터인지: 마지막 주문일과 집계에 쓰는 적재(파일, 기준연, 적재 시각). */
    public record DataAsOf(LocalDate lastOrderDate, List<LoadInfo> loads) {
    }

    public record MonthSummary(int month, int quantity, double share) {
    }

    public record PerCapita(int year, int quantity, int headcount, LocalDate baseDate, double perCapita) {
    }

    public record AnnualAverage(Integer fromYear, Integer toYear, double quantity, double orderDays) {
    }

    /**
     * @param years     전체 연도. complete=false인 해(첫·마지막 불완전 연도)는 평균·추천에 쓰지 않는다
     * @param months    안정 연도 이후 완전한 해의 월별 합계
     */
    public record Summary(Basis basis, List<Integer> completeYears, AnnualAverage annualAverage,
                          List<YearSummary> years, List<MonthSummary> months, List<PerCapita> perCapita,
                          DataAsOf dataAsOf) {
    }

    /** 품목군 하나의 절감 효과와 그 근거(단가 출처, 탄소계수와 출처). */
    public record GroupSaving(UniformItemGroup group, String label, double mismatchRate, double annualQuantity,
                              double savedQuantity, BigDecimal savedAmount, BigDecimal carbonKg, BigDecimal unitPrice,
                              PriceSource priceSource, BigDecimal carbonFactor, String carbonSource) {
    }

    /** 개선률을 달리 가정했을 때의 합계. */
    public record Scenario(double rate, double savedQuantity, BigDecimal savedAmount, BigDecimal carbonKg) {
    }

    /**
     * 절감 효과 산출 근거. estimatedPriceShare는 절감액 중 추정 단가에 기대는 비율(0~1)이다.
     *
     * @param estimatedGroups 추정 단가를 쓴 품목군
     */
    public record SavingsReport(Comparison comparison, double improvementRate, List<Scenario> scenarios,
                                List<GroupSaving> groups, BigDecimal savedAmount, double savedQuantity,
                                BigDecimal carbonKg, double estimatedPriceShare, List<String> estimatedGroups) {
    }

    public record SizeShare(String size, int previousQuantity, double previousShare, int recentQuantity,
                            double recentShare, double gap) {
    }

    public record YearShares(int year, boolean complete, int quantity, Map<String, Double> shares) {
    }

    /** gap > 0: 이전 비율대로 주문하면 모자랐을 사이즈. gap < 0: 남았을 사이즈. */
    public record SizeAnalysis(UniformItemGroup group, String label, Comparison comparison, Double mismatchRate,
                               List<SizeShare> sizes, List<YearShares> years) {
    }

    public record JobFamily(String jobFamily, int headcount) {
    }

    public record HeadcountView(List<YearHeadcount> years, YearHeadcount latest, List<JobFamily> latestByJobFamily) {
    }

    private final UniformDatasetLoader loader;
    private final UniformAnalysis analysis;
    private final JdbcTemplate jdbc;
    private final ResultCache cache;
    private final DataSourceService sources;

    public UniformReportService(UniformDatasetLoader loader, UniformAnalysis analysis, JdbcTemplate jdbc,
                                ResultCache cache, DataSourceService sources) {
        this.loader = loader;
        this.analysis = analysis;
        this.jdbc = jdbc;
        this.cache = cache;
        this.sources = sources;
    }

    public Summary summary(Basis basis) {
        return cache.get("summary|" + basis, () -> computeSummary(basis));
    }

    private Summary computeSummary(Basis basis) {
        UniformDataset raw = loader.load(Basis.RAW);
        UniformDataset data = raw.withBasis(basis);
        SortedSet<Integer> complete = data.completeYears();

        List<YearSummary> years = new ArrayList<>();
        for (int year : raw.years()) {
            List<OrderLine> all = raw.lines(l -> l.year() == year);
            List<OrderLine> lines = data.lines(l -> l.year() == year);
            int unique = UniformAnalysis.quantity(all.stream().filter(l -> !l.duplicate()).toList());
            Map<String, Integer> byGroup = new LinkedHashMap<>();
            for (UniformItemGroup g : data.groups()) {
                byGroup.put(g.label(), UniformAnalysis.quantity(lines.stream().filter(l -> l.group() == g).toList()));
            }
            BigDecimal amount = BigDecimal.ZERO;
            for (OrderLine l : lines) {
                amount = amount.add(data.priceOf(l.group()).price().multiply(BigDecimal.valueOf(l.quantity())));
            }
            years.add(new YearSummary(year, complete.contains(year), UniformAnalysis.quantity(lines), unique,
                    UniformAnalysis.quantity(all) - unique, UniformAnalysis.orderDays(data, year), byGroup, amount));
        }

        SortedSet<Integer> stable = analysis.stableYears(data);
        List<OrderLine> stableLines = data.lines(l -> stable.contains(l.year()));
        int stableTotal = UniformAnalysis.quantity(stableLines);
        List<MonthSummary> months = new ArrayList<>();
        for (int m = 1; m <= 12; m++) {
            int month = m;
            int q = UniformAnalysis.quantity(stableLines.stream().filter(l -> l.date().getMonthValue() == month).toList());
            months.add(new MonthSummary(m, q, stableTotal == 0 ? 0 : q / (double) stableTotal));
        }

        List<PerCapita> perCapita = new ArrayList<>();
        for (int year : complete) {
            YearHeadcount hc = data.headcounts().get(year);
            analysis.perCapita(data, year).ifPresent(v -> perCapita.add(new PerCapita(year,
                    UniformAnalysis.quantity(data.lines(l -> l.year() == year)), hc.headcount(), hc.baseDate(), v)));
        }

        AnnualAverage average = stable.isEmpty() ? new AnnualAverage(null, null, 0, 0)
                : new AnnualAverage(stable.first(), stable.last(), stableTotal / (double) stable.size(),
                        stable.stream().mapToInt(y -> UniformAnalysis.orderDays(data, y)).average().orElse(0));
        return new Summary(basis, List.copyOf(complete), average, years, months, perCapita, dataAsOf(raw));
    }

    private DataAsOf dataAsOf(UniformDataset data) {
        LocalDate last = data.lines().stream().map(OrderLine::date).max(java.util.Comparator.naturalOrder()).orElse(null);
        Map<String, String> names = sources.names();
        List<LoadInfo> loads = jdbc.query("""
                select source, base_year, file_name, loaded_at from load_history where active order by source""",
                (rs, i) -> new LoadInfo(rs.getString("source"), names.getOrDefault(rs.getString("source"),
                        rs.getString("source")), (Integer) rs.getObject("base_year"), rs.getString("file_name"),
                        rs.getObject("loaded_at", OffsetDateTime.class)));
        return new DataAsOf(last, loads);
    }

    /** 절감 효과 산출 근거: 품목군별 값, 단가 출처, 탄소계수 출처, 개선률 시나리오. */
    public SavingsReport savings(Basis basis) {
        return cache.get("savings|" + basis, () -> computeSavings(basis));
    }

    private SavingsReport computeSavings(Basis basis) {
        UniformDataset data = loader.load(basis);
        double base = analysis.properties().improvementRate();
        Optional<Comparison> comparison = analysis.latestComparison(data);
        if (comparison.isEmpty()) {
            return new SavingsReport(null, base, List.of(), List.of(), BigDecimal.ZERO, 0, BigDecimal.ZERO, 0, List.of());
        }
        Map<String, String> carbonSource = new LinkedHashMap<>();
        jdbc.query("select item_group, source from carbon_factor", rs -> {
            carbonSource.put(rs.getString("item_group"), rs.getString("source"));
        });
        List<GroupSaving> groups = new ArrayList<>();
        for (UniformItemGroup g : data.groups()) {
            analysis.savings(data, g, comparison.get()).ifPresent(s -> groups.add(new GroupSaving(g, g.label(),
                    s.mismatchRate(), s.annualQuantity(), s.savedQuantity(), s.savedAmount(), s.carbonKg(), s.unitPrice(),
                    s.priceSource(), data.carbonOf(g), carbonSource.getOrDefault(g.name(), "-"))));
        }
        BigDecimal amount = groups.stream().map(GroupSaving::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        double quantity = groups.stream().mapToDouble(GroupSaving::savedQuantity).sum();
        BigDecimal carbon = groups.stream().map(GroupSaving::carbonKg).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal estimated = groups.stream().filter(x -> x.priceSource() == PriceSource.ESTIMATE)
                .map(GroupSaving::savedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        double share = amount.signum() == 0 ? 0 : estimated.divide(amount, 6, java.math.RoundingMode.HALF_UP).doubleValue();
        List<Scenario> scenarios = new ArrayList<>();
        for (double factor : new double[] {0.5, 1.0, 1.5}) {
            scenarios.add(new Scenario(base * factor, quantity * factor, amount.multiply(BigDecimal.valueOf(factor)),
                    carbon.multiply(BigDecimal.valueOf(factor))));
        }
        return new SavingsReport(comparison.get(), base, scenarios, groups, amount, quantity, carbon, share,
                groups.stream().filter(x -> x.priceSource() == PriceSource.ESTIMATE).map(GroupSaving::label).toList());
    }

    /** 품목군별 사이즈 분석. group이 null이면 전 품목군. */
    public List<SizeAnalysis> sizes(Basis basis, UniformItemGroup group) {
        return cache.get("sizes|" + basis + "|" + group, () -> computeSizes(basis, group));
    }

    private List<SizeAnalysis> computeSizes(Basis basis, UniformItemGroup group) {
        UniformDataset data = loader.load(basis);
        Optional<Comparison> comparison = analysis.latestComparison(data);
        List<UniformItemGroup> groups = group == null ? data.groups() : List.of(group);
        if (group != null && !data.groups().contains(group)) {
            throw new BusinessException("데이터가 없는 품목군입니다: " + group.label());
        }
        List<SizeAnalysis> result = new ArrayList<>();
        for (UniformItemGroup g : groups) {
            List<OrderLine> lines = data.lines(g);
            List<SizeShare> sizes = comparison.map(c -> compare(lines, c)).orElse(List.of());
            Double rate = comparison.flatMap(c -> {
                var r = analysis.mismatch(data, g, c);
                return r.isPresent() ? Optional.of(r.getAsDouble()) : Optional.empty();
            }).orElse(null);
            List<YearShares> years = data.years().stream().map(y -> {
                List<OrderLine> ofYear = lines.stream().filter(l -> l.year() == y).toList();
                return new YearShares(y, data.completeYears().contains(y), UniformAnalysis.quantity(ofYear),
                        UniformAnalysis.shares(ofYear, OrderLine::size));
            }).filter(y -> y.quantity() > 0).toList();
            result.add(new SizeAnalysis(g, g.label(), comparison.orElse(null), rate, sizes, years));
        }
        return result;
    }

    private static List<SizeShare> compare(List<OrderLine> lines, Comparison c) {
        Map<String, Integer> previous = sum(UniformAnalysis.within(lines, c.previous()));
        Map<String, Integer> recent = sum(UniformAnalysis.within(lines, c.recent()));
        double pt = previous.values().stream().mapToInt(Integer::intValue).sum();
        double rt = recent.values().stream().mapToInt(Integer::intValue).sum();
        // 사이즈 순서는 원본 데이터 순서(출처 → 사이즈 코드)를 따른다
        List<String> keys = lines.stream()
                .sorted(java.util.Comparator.comparing(OrderLine::source)
                        .thenComparing(l -> l.rawSize() != null && l.rawSize().matches("\\d+")
                                ? Integer.parseInt(l.rawSize()) : Integer.MAX_VALUE))
                .map(OrderLine::size).distinct()
                .filter(k -> previous.containsKey(k) || recent.containsKey(k)).toList();
        return keys.stream().map(k -> {
            int p = previous.getOrDefault(k, 0);
            int r = recent.getOrDefault(k, 0);
            double ps = pt == 0 ? 0 : p / pt;
            double rs = rt == 0 ? 0 : r / rt;
            return new SizeShare(k, p, ps, r, rs, rs - ps);
        }).toList();
    }

    private static Map<String, Integer> sum(List<OrderLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(OrderLine::size, Collectors.summingInt(OrderLine::quantity)));
    }

    public HeadcountView headcount() {
        return cache.get("headcount", this::computeHeadcount);
    }

    private HeadcountView computeHeadcount() {
        UniformDataset data = loader.load(Basis.DEDUP);
        List<YearHeadcount> years = data.headcounts().values().stream()
                .sorted(java.util.Comparator.comparingInt(YearHeadcount::year)).toList();
        YearHeadcount latest = years.isEmpty() ? null : years.getLast();
        List<JobFamily> families = latest == null ? List.of() : jdbc.query("""
                select job_family, sum(quota) as total from headcount_active
                where base_date = ? group by job_family order by total desc""",
                (rs, i) -> new JobFamily(rs.getString("job_family"), rs.getInt("total")), latest.baseDate());
        return new HeadcountView(years, latest, families);
    }
}
