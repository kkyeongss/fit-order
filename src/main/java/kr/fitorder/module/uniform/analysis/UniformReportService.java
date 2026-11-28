package kr.fitorder.module.uniform.analysis;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.stream.Collectors;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 화면용 집계: 연도·월·품목별 주문량, 사이즈 비율, 정원. 저장하지 않고 그때그때 계산한다. */
@Service
@Transactional(readOnly = true)
public class UniformReportService {

    public record YearSummary(int year, boolean complete, int quantity, int uniqueQuantity, int duplicateQuantity,
                              int orderDays, Map<String, Integer> byGroup) {
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
                          List<YearSummary> years, List<MonthSummary> months, List<PerCapita> perCapita) {
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

    public UniformReportService(UniformDatasetLoader loader, UniformAnalysis analysis, JdbcTemplate jdbc) {
        this.loader = loader;
        this.analysis = analysis;
        this.jdbc = jdbc;
    }

    public Summary summary(Basis basis) {
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
            years.add(new YearSummary(year, complete.contains(year), UniformAnalysis.quantity(lines), unique,
                    UniformAnalysis.quantity(all) - unique, UniformAnalysis.orderDays(data, year), byGroup));
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
        return new Summary(basis, List.copyOf(complete), average, years, months, perCapita);
    }

    /** 품목군별 사이즈 분석. group이 null이면 전 품목군. */
    public List<SizeAnalysis> sizes(Basis basis, UniformItemGroup group) {
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
