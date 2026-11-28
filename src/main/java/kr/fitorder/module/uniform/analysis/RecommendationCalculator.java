package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;
import java.util.stream.Collectors;
import kr.fitorder.module.uniform.analysis.UniformAnalysis.Savings;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.stereotype.Component;

/**
 * 규칙 기반 발주 추천. 담당자가 따라 계산할 수 있게 단계를 그대로 둔다.
 * <ol>
 *   <li>총량 = 직전 N년(기본 2년) 평균. 격년 품목은 같은 홀짝의 가장 최근 해 값</li>
 *   <li>인원 보정 = 총량 × (입력 인원 ÷ 기준 연도 정원)</li>
 *   <li>사이즈 배분 = 출처 비중으로 먼저 나누고, 출처 안에서 사이즈 비율로 나눈다. 둘 다 최근 3년 3:2:1 가중</li>
 *   <li>안전재고 = 품목군별 비율(기본 5%, 백테스트 오차가 큰 재킷·바지·패딩조끼 10%)</li>
 * </ol>
 * 과거는 불완전 연도(첫 해·마지막 해 중 1년치가 안 되는 해)를 빼고 본다.
 */
@Component
public class RecommendationCalculator {

    /** 사이즈 하나. 출처마다 사이즈 체계가 달라 출처까지 같아야 같은 사이즈다. */
    public record SizeKey(String source, String rawSize, String size) {
    }

    /** 1단계 결과. */
    public record TotalEstimate(List<Integer> years, double quantity, String rule) {
    }

    public record SizeLine(String source, String rawSize, String size, double ratio, int base, int safety,
                           int quantity) {
    }

    public record ItemPlan(UniformItemGroup group, String label, TotalEstimate total, Integer headcountYear,
                           Integer baseHeadcount, double headcountFactor, int baseQuantity, double safetyRate,
                           int safetyQuantity, int quantity, String sizeRule, List<SizeLine> sizes,
                           BigDecimal unitPrice, boolean priceEstimated, BigDecimal amount, Savings savings) {
    }

    public record MonthShare(int month, int quantity, double share) {
    }

    private final UniformAnalysis analysis;
    private final AnalysisProperties props;

    public RecommendationCalculator(UniformAnalysis analysis) {
        this.analysis = analysis;
        this.props = analysis.properties();
    }

    /**
     * 품목군 하나의 추천.
     *
     * @param headcount 발주 대상 인원. null이면 인원 보정을 하지 않는다
     */
    public ItemPlan plan(UniformDataset data, UniformItemGroup group, int targetYear, Integer headcount) {
        TotalEstimate total = total(data, group, targetYear);

        int headcountYear = total.years().stream().mapToInt(Integer::intValue).max().orElse(targetYear - 1);
        YearHeadcount base = data.headcounts().get(headcountYear);
        double factor = headcount != null && base != null && base.headcount() > 0
                ? headcount / (double) base.headcount() : 1.0;

        int baseQuantity = (int) Math.round(total.quantity() * factor);
        double safetyRate = props.safetyStockOf(group);
        int safety = (int) Math.round(baseQuantity * safetyRate);

        Map<SizeKey, Double> ratios = sizeRatios(data, group, targetYear);
        Map<SizeKey, Integer> baseBySize = allocate(baseQuantity, ratios);
        Map<SizeKey, Integer> safetyBySize = allocate(safety, ratios);
        List<SizeLine> sizes = ratios.entrySet().stream()
                .map(e -> {
                    int b = baseBySize.get(e.getKey());
                    int s = safetyBySize.get(e.getKey());
                    return new SizeLine(e.getKey().source(), e.getKey().rawSize(), e.getKey().size(), e.getValue(),
                            b, s, b + s);
                })
                .toList();

        var price = data.priceOf(group);
        Savings savings = analysis.latestComparison(data)
                .flatMap(c -> {
                    OptionalDouble rate = analysis.mismatch(data, group, c);
                    return rate.isPresent()
                            ? Optional.of(analysis.savings(data, group, c, rate.getAsDouble(), baseQuantity))
                            : Optional.empty();
                })
                .orElse(null);
        return new ItemPlan(group, group.label(), total, base == null ? null : headcountYear,
                base == null ? null : base.headcount(), factor, baseQuantity, safetyRate, safety,
                baseQuantity + safety, sizeRule(data, group, targetYear), sizes, price.price(), price.estimated(),
                price.price().multiply(BigDecimal.valueOf(baseQuantity + safety)), savings);
    }

    /** 1단계: 총량. */
    public TotalEstimate total(UniformDataset data, UniformItemGroup group, int targetYear) {
        Map<Integer, Integer> byYear = UniformAnalysis.quantityByYear(data.lines(group));
        List<Integer> past = pastYears(data, targetYear);
        if (props.biennialGroups().contains(group)) {
            return past.stream().filter(y -> (targetYear - y) % 2 == 0).findFirst()
                    .map(y -> new TotalEstimate(List.of(y), byYear.getOrDefault(y, 0),
                            "격년 주문: " + y + "년 " + byYear.getOrDefault(y, 0) + "벌"))
                    .orElse(new TotalEstimate(List.of(), 0, "같은 홀짝 해의 데이터가 없습니다"));
        }
        List<Integer> years = past.stream().limit(props.averageYears()).sorted().toList();
        double avg = years.stream().mapToInt(y -> byYear.getOrDefault(y, 0)).average().orElse(0);
        String label = years.stream().map(String::valueOf).collect(Collectors.joining("·"));
        return new TotalEstimate(years, avg, years.isEmpty() ? "과거 데이터가 없습니다"
                : "직전 " + years.size() + "년(" + label + ") 평균 " + Math.round(avg) + "벌");
    }

    /**
     * 3단계: 사이즈 비율(합 1). 출처별 비중 × 출처 안 사이즈 비율.
     * 연도별 비율을 최근 연도부터 가중치(3:2:1)로 평균하고, 주문이 없는 해는 건너뛴다.
     */
    public Map<SizeKey, Double> sizeRatios(UniformDataset data, UniformItemGroup group, int targetYear) {
        List<Integer> years = ratioYears(data, targetYear);
        List<OrderLine> lines = data.lines(group);
        Map<String, Double> sourceShares = weighted(lines, years, OrderLine::source);
        Map<SizeKey, Double> result = new LinkedHashMap<>();
        sourceShares.forEach((source, share) -> {
            List<OrderLine> ofSource = lines.stream().filter(l -> l.source().equals(source)).toList();
            weighted(ofSource, years, l -> new SizeKey(l.source(), l.rawSize(), l.size()))
                    .forEach((key, ratio) -> result.put(key, share * ratio));
        });
        return sortBySize(result);
    }

    /** 과거 수량이 몰린 달(설정)과 그 비중. 안정 연도 이후 완전한 해 기준. */
    public List<MonthShare> orderMonths(UniformDataset data) {
        List<OrderLine> lines = data.lines(l -> analysis.stableYears(data).contains(l.year()));
        int total = UniformAnalysis.quantity(lines);
        return props.orderMonths().stream().map(m -> {
            int q = UniformAnalysis.quantity(lines.stream().filter(l -> l.date().getMonthValue() == m).toList());
            return new MonthShare(m, q, total == 0 ? 0 : q / (double) total);
        }).toList();
    }

    /** 목표 연도 이전의 완전한 해, 최근부터. */
    List<Integer> pastYears(UniformDataset data, int targetYear) {
        return data.completeYears().headSet(targetYear).reversed().stream().toList();
    }

    private List<Integer> ratioYears(UniformDataset data, int targetYear) {
        return pastYears(data, targetYear).stream().limit(props.sizeWeights().size()).toList();
    }

    private String sizeRule(UniformDataset data, UniformItemGroup group, int targetYear) {
        List<Integer> years = ratioYears(data, targetYear);
        if (years.isEmpty()) {
            return "과거 데이터가 없습니다";
        }
        String weights = props.sizeWeights().subList(0, years.size()).stream().map(String::valueOf)
                .collect(Collectors.joining(":"));
        String sources = data.lines(group).stream().map(OrderLine::source).distinct().count() > 1
                ? "출처별 주문 비중으로 나눈 뒤 " : "";
        return sources + "최근 " + years.size() + "년(" + years.getLast() + "~" + years.getFirst() + ") 사이즈 비율 "
                + weights + " 가중";
    }

    /** 연도별 비율을 가중 평균한다. years[0]이 가장 최근 해이고 가중치도 그 순서. */
    private <K> Map<K, Double> weighted(List<OrderLine> lines, List<Integer> years,
                                        Function<OrderLine, K> key) {
        Map<K, Double> acc = new LinkedHashMap<>();
        double weightSum = 0;
        for (int i = 0; i < years.size(); i++) {
            int year = years.get(i);
            Map<K, Double> shares = UniformAnalysis.shares(lines.stream().filter(l -> l.year() == year).toList(), key);
            if (shares.isEmpty()) {
                continue;
            }
            double w = props.sizeWeights().get(i);
            weightSum += w;
            shares.forEach((k, s) -> acc.merge(k, w * s, Double::sum));
        }
        double sum = weightSum;
        if (sum > 0) {
            acc.replaceAll((k, v) -> v / sum);
        }
        return acc;
    }

    /** 최대 나머지 방식으로 정수 배분. 합이 total과 정확히 같다. */
    static <K> Map<K, Integer> allocate(int total, Map<K, Double> ratios) {
        Map<K, Integer> result = new LinkedHashMap<>();
        if (ratios.isEmpty()) {
            return result;
        }
        List<Map.Entry<K, Double>> remainders = new ArrayList<>();
        int assigned = 0;
        for (var e : ratios.entrySet()) {
            double exact = total * e.getValue();
            int floor = (int) Math.floor(exact);
            result.put(e.getKey(), floor);
            assigned += floor;
            remainders.add(Map.entry(e.getKey(), exact - floor));
        }
        remainders.sort(Map.Entry.<K, Double>comparingByValue().reversed());
        for (int i = 0; i < total - assigned; i++) {
            K key = remainders.get(i % remainders.size()).getKey();
            result.merge(key, 1, Integer::sum);
        }
        return result;
    }

    private static Map<SizeKey, Double> sortBySize(Map<SizeKey, Double> ratios) {
        Comparator<SizeKey> order = Comparator.comparing(SizeKey::source)
                .thenComparing(k -> k.rawSize() != null && k.rawSize().matches("\\d+") ? Integer.parseInt(k.rawSize())
                        : Integer.MAX_VALUE)
                .thenComparing(k -> String.valueOf(k.rawSize()));
        return ratios.entrySet().stream().sorted(Map.Entry.comparingByKey(order))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
    }

    /** 추천 대상 기본 연도: 데이터 마지막 해의 다음 해. */
    public static int defaultYear(UniformDataset data) {
        return data.years().isEmpty() ? java.time.Year.now().getValue() : data.years().last() + 1;
    }

    /** 기본 인원: 가장 최근 정원. */
    public static Optional<YearHeadcount> latestHeadcount(UniformDataset data) {
        return data.headcounts().values().stream().max(Comparator.comparingInt(YearHeadcount::year));
    }
}
