package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import kr.fitorder.module.uniform.analysis.UniformDataset.PriceSource;
import kr.fitorder.module.uniform.analysis.UniformDataset.UnitPrice;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.stereotype.Component;

/**
 * 근무복 지표 계산식. DB를 모르고 UniformDataset만 받아 계산한다(단위 테스트 대상).
 * 계산식은 기획서 '분석·추천 로직 > 지표' 표와 같다.
 */
@Component
public class UniformAnalysis {

    /** 품목군 하나의 절감 효과. 연간 기준이다. */
    public record Savings(UniformItemGroup group, Comparison comparison, double mismatchRate, double annualQuantity,
                          double savedQuantity, BigDecimal unitPrice, PriceSource priceSource, BigDecimal savedAmount,
                          BigDecimal carbonKg) {
    }

    private final AnalysisProperties props;

    public UniformAnalysis(AnalysisProperties props) {
        this.props = props;
    }

    public AnalysisProperties properties() {
        return props;
    }

    // 수량 ---------------------------------------------------------------------

    public static int quantity(Collection<OrderLine> lines) {
        return lines.stream().mapToInt(OrderLine::quantity).sum();
    }

    public static Map<Integer, Integer> quantityByYear(Collection<OrderLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(OrderLine::year, TreeMap::new,
                Collectors.summingInt(OrderLine::quantity)));
    }

    /** key별 수량 비율(합 1). 수량이 없으면 빈 맵. */
    public static <K> Map<K, Double> shares(Collection<OrderLine> lines, Function<OrderLine, K> key) {
        Map<K, Integer> sums = lines.stream().collect(Collectors.groupingBy(key, LinkedHashMap::new,
                Collectors.summingInt(OrderLine::quantity)));
        double total = sums.values().stream().mapToInt(Integer::intValue).sum();
        Map<K, Double> shares = new LinkedHashMap<>();
        if (total > 0) {
            sums.forEach((k, v) -> shares.put(k, v / total));
        }
        return shares;
    }

    // 사이즈 미스매치 ------------------------------------------------------------

    /** Σ max(0, 최근 비율 − 이전 비율). 이전 비율대로 주문했다면 모자랐을 몫의 합(= 남았을 몫의 합). */
    public static <K> double mismatch(Map<K, Double> previous, Map<K, Double> recent) {
        Set<K> keys = new HashSet<>(previous.keySet());
        keys.addAll(recent.keySet());
        return keys.stream()
                .mapToDouble(k -> Math.max(0, recent.getOrDefault(k, 0.0) - previous.getOrDefault(k, 0.0)))
                .sum();
    }

    /**
     * 비교할 구간 쌍. 안정 연도 이후의 완전한 해만 쓰고, 최근 구간 끝을 한 해씩 옮긴다.
     * 예) 2020~2025, 구간 3년이면 [2020-2022 → 2023-2025] 하나.
     */
    public List<Comparison> comparisons(UniformDataset data) {
        int len = props.windowYears();
        List<Integer> years = data.completeYears().stream().filter(y -> y >= props.stableFromYear()).toList();
        List<Comparison> result = new ArrayList<>();
        for (int end : years) {
            int start = end - 2 * len + 1;
            if (years.containsAll(rangeOf(start, end))) {
                result.add(new Comparison(new Period(start, start + len - 1), new Period(end - len + 1, end)));
            }
        }
        return result;
    }

    public Optional<Comparison> latestComparison(UniformDataset data) {
        List<Comparison> all = comparisons(data);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.getLast());
    }

    /** 품목군의 사이즈 미스매치율(0~1). 어느 한 구간에 주문이 없으면 비어 있다. */
    public OptionalDouble mismatch(UniformDataset data, UniformItemGroup group, Comparison c) {
        List<OrderLine> lines = data.lines(group);
        Map<String, Double> previous = shares(within(lines, c.previous()), OrderLine::size);
        Map<String, Double> recent = shares(within(lines, c.recent()), OrderLine::size);
        if (previous.isEmpty() || recent.isEmpty()) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(mismatch(previous, recent));
    }

    /**
     * 절감 수량 = 미스매치율 × 연간 주문량(최근 구간 평균) × 개선률.
     * 절감액 = 절감 수량 × 단가, 탄소 감축량 = 절감 수량 × 탄소계수.
     */
    public Optional<Savings> savings(UniformDataset data, UniformItemGroup group, Comparison c) {
        OptionalDouble rate = mismatch(data, group, c);
        if (rate.isEmpty()) {
            return Optional.empty();
        }
        double annual = quantity(within(data.lines(group), c.recent())) / (double) c.recent().length();
        return Optional.of(savings(data, group, c, rate.getAsDouble(), annual));
    }

    public Savings savings(UniformDataset data, UniformItemGroup group, Comparison c, double rate, double quantity) {
        double saved = rate * quantity * props.improvementRate();
        UnitPrice price = data.priceOf(group);
        BigDecimal savedQty = BigDecimal.valueOf(saved);
        return new Savings(group, c, rate, quantity, saved, price.price(), price.source(),
                savedQty.multiply(price.price()), savedQty.multiply(data.carbonOf(group)));
    }

    // 발주 패턴 -----------------------------------------------------------------

    /** 주문일별 수량(두 출처 합산). */
    public static Map<LocalDate, Integer> dailyQuantity(Collection<OrderLine> lines) {
        return lines.stream().collect(Collectors.groupingBy(OrderLine::date, TreeMap::new,
                Collectors.summingInt(OrderLine::quantity)));
    }

    /** 연간 발주 일수: 두 데이터를 합친 고유 주문일 수. */
    public static int orderDays(UniformDataset data, int year) {
        return dailyQuantity(data.lines(l -> l.year() == year)).size();
    }

    /** 소량 발주 비율(0~1): 하루 주문량이 기준 이하인 날 ÷ 전체 주문일. */
    public OptionalDouble smallOrderRatio(UniformDataset data, int year) {
        Map<LocalDate, Integer> days = dailyQuantity(data.lines(l -> l.year() == year));
        if (days.isEmpty()) {
            return OptionalDouble.empty();
        }
        long small = days.values().stream().filter(q -> q <= props.smallOrderMax()).count();
        return OptionalDouble.of(small / (double) days.size());
    }

    /** 1인당 지급량 = 연간 주문량 ÷ 그해 정원. 기준 연도 이전이나 정원이 없으면 비어 있다. */
    public OptionalDouble perCapita(UniformDataset data, int year) {
        YearHeadcount hc = data.headcounts().get(year);
        if (year < props.perCapitaFromYear() || hc == null || hc.headcount() == 0) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(quantity(data.lines(l -> l.year() == year)) / (double) hc.headcount());
    }

    /** 안정 연도 이후 완전한 해. 연평균 등에 쓴다. */
    public SortedSet<Integer> stableYears(UniformDataset data) {
        return data.completeYears().tailSet(props.stableFromYear());
    }

    public static List<OrderLine> within(Collection<OrderLine> lines, Period period) {
        return lines.stream().filter(l -> period.contains(l.year())).toList();
    }

    private static List<Integer> rangeOf(int from, int to) {
        List<Integer> r = new ArrayList<>();
        for (int y = from; y <= to; y++) {
            r.add(y);
        }
        return r;
    }
}
