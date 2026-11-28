package kr.fitorder.module.uniform.analysis;

import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.bounds;
import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.dataset;
import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.line;
import static kr.fitorder.module.uniform.domain.UniformItemGroup.JACKET;
import static kr.fitorder.module.uniform.domain.UniformItemGroup.JUMPER;
import static kr.fitorder.module.uniform.domain.UniformItemGroup.PANTS;
import static kr.fitorder.module.uniform.domain.UniformItemGroup.SHIRT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.ItemPlan;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeKey;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeLine;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.junit.jupiter.api.Test;

class RecommendationCalculatorTest {

    private final RecommendationCalculator calculator =
            new RecommendationCalculator(new UniformAnalysis(AnalysisFixtures.properties()),
                    kr.fitorder.module.uniform.UniformTestFixtures.properties());

    @Test
    void 총량은_직전_2년_평균이고_불완전_연도는_뺀다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2025));
        lines.add(line("2023-04-01", SHIRT, "95", 999));
        lines.add(line("2024-04-01", SHIRT, "95", 400));
        lines.add(line("2025-04-01", SHIRT, "95", 600));
        lines.add(line("2026-04-01", SHIRT, "95", 50));   // 2026은 불완전 연도

        var total = calculator.total(dataset(lines, Map.of()), SHIRT, 2027);

        assertThat(total.years()).containsExactly(2024, 2025);
        assertThat(total.quantity()).isEqualTo(500);
    }

    @Test
    void 격년_품목은_같은_홀짝의_가장_최근_해_값을_쓴다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2025));
        lines.add(line("2023-04-01", JACKET, "100", 160));
        lines.add(line("2024-04-01", JACKET, "100", 20));
        lines.add(line("2025-04-01", JACKET, "100", 180));
        UniformDataset data = dataset(lines, Map.of());

        assertThat(calculator.total(data, JACKET, 2027).quantity()).isEqualTo(180);
        assertThat(calculator.total(data, JACKET, 2026).quantity()).isEqualTo(20);
    }

    @Test
    void 사이즈_비율은_최근_3년을_3대2대1로_가중_평균한다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2025));
        lines.add(line("2023-04-01", SHIRT, "95", 10));    // 2023: 95 100%   가중 1
        lines.add(line("2024-04-01", SHIRT, "100", 10));   // 2024: 100 100%  가중 2
        lines.add(line("2025-04-01", SHIRT, "105", 10));   // 2025: 105 100%  가중 3
        Map<String, Double> ratios = bySize(calculator.sizeRatios(dataset(lines, Map.of()), SHIRT, 2026));

        assertThat(ratios.get("95")).isCloseTo(1 / 6.0, within(1e-9));
        assertThat(ratios.get("100")).isCloseTo(2 / 6.0, within(1e-9));
        assertThat(ratios.get("105")).isCloseTo(3 / 6.0, within(1e-9));
    }

    @Test
    void 점퍼는_출처_비중으로_먼저_나누고_출처_안에서_사이즈로_나눈다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2023, 2025));
        for (int y = 2023; y <= 2025; y++) {
            lines.add(line("info", y + "-04-01", JUMPER, "50", 90));   // 출처 info 90%
            lines.add(line("order", y + "-04-01", JUMPER, "100", 5));  // 출처 order 10%, 사이즈 반반
            lines.add(line("order", y + "-04-01", JUMPER, "105", 5));
        }
        Map<SizeKey, Double> ratios = calculator.sizeRatios(dataset(lines, Map.of()), JUMPER, 2026);

        assertThat(ratios.get(new SizeKey("info", "50", "50 (info)"))).isCloseTo(0.9, within(1e-9));
        assertThat(ratios.get(new SizeKey("order", "100", "100 (order)"))).isCloseTo(0.05, within(1e-9));
    }

    @Test
    void 인원_보정과_안전재고를_더하고_사이즈별_합이_총량과_같다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2025));
        lines.add(line("2024-04-01", PANTS, "50", 100));
        lines.add(line("2025-04-01", PANTS, "50", 70));
        lines.add(line("2025-04-01", PANTS, "55", 30));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));

        // 총량 100 × (1,100 ÷ 1,000) = 110, 바지 안전재고 10% = 11
        ItemPlan plan = calculator.plan(data, PANTS, 2026, 1100);

        assertThat(plan.headcountFactor()).isCloseTo(1.1, within(1e-9));
        assertThat(plan.baseQuantity()).isEqualTo(110);
        assertThat(plan.safetyQuantity()).isEqualTo(11);
        assertThat(plan.quantity()).isEqualTo(121);
        assertThat(plan.sizes().stream().mapToInt(SizeLine::quantity).sum()).isEqualTo(121);
        assertThat(plan.sizes().stream().mapToInt(SizeLine::base).sum()).isEqualTo(110);
    }

    @Test
    void 정수_배분은_나머지가_큰_쪽부터_채워_합을_맞춘다() {
        Map<String, Double> ratios = new LinkedHashMap<>();
        ratios.put("a", 1 / 3.0);
        ratios.put("b", 1 / 3.0);
        ratios.put("c", 1 / 3.0);

        assertThat(RecommendationCalculator.allocate(10, ratios).values().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(10);
    }

    @Test
    void 재고를_빼면_발주_수량이_줄고_회차별_수량의_합이_발주_수량과_같다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2026));
        lines.add(line("2025-04-01", SHIRT, "95", 60));
        lines.add(line("2025-04-01", SHIRT, "100", 40));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));
        var stock = Map.of(StockService.StockKey.of(SHIRT, "A", "95"), 20);

        ItemPlan plan = calculator.plan(data, SHIRT, 2027, 1000, null, stock, List.of(0.6, 0.4));

        SizeLine s95 = plan.sizes().stream().filter(z -> "95".equals(z.rawSize())).findFirst().orElseThrow();
        assertThat(s95.stock()).isEqualTo(20);
        assertThat(s95.orderQuantity()).isEqualTo(s95.quantity() - 20);
        assertThat(plan.stockTotal()).isEqualTo(20);
        assertThat(plan.orderQuantity()).isEqualTo(plan.quantity() - 20);
        for (SizeLine z : plan.sizes()) {
            assertThat(z.rounds()).hasSize(2);
            assertThat(z.rounds().stream().mapToInt(Integer::intValue).sum()).isEqualTo(z.orderQuantity());
        }
        assertThat(plan.rounds().stream().mapToInt(r -> r.quantity()).sum()).isEqualTo(plan.orderQuantity());
        assertThat(plan.orderAmount()).isEqualByComparingTo(new java.math.BigDecimal("50000").multiply(java.math.BigDecimal.valueOf(plan.orderQuantity())));
    }

    @Test
    void 재고가_필요_수량보다_많으면_발주_수량은_0이다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2026));
        lines.add(line("2025-04-01", SHIRT, "95", 100));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));
        var stock = Map.of(StockService.StockKey.of(SHIRT, "A", "95"), 999);

        ItemPlan plan = calculator.plan(data, SHIRT, 2027, 1000, null, stock, List.of());

        assertThat(plan.orderQuantity()).isZero();
        assertThat(plan.sizes().getFirst().orderQuantity()).isZero();
    }

    @Test
    void 담당자가_총량을_지정하면_과거_기록_대신_그_값으로_계산하고_근거에_밝힌다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2026));
        lines.add(line("2025-04-01", SHIRT, "95", 100));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));

        ItemPlan plan = calculator.plan(data, SHIRT, 2027, 1000, 300, Map.of(), List.of());

        assertThat(plan.baseQuantity()).isEqualTo(300);
        assertThat(plan.totalOverride()).isEqualTo(300);
        assertThat(plan.total().rule()).contains("직접 지정").contains("300벌").contains("참고");
    }

    @Test
    void 격년_품목의_최근_값이_이전보다_크게_낮으면_경고한다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2026));
        lines.add(line("2021-04-01", UniformItemGroup.PADDING_VEST, "100", 117));
        lines.add(line("2023-04-01", UniformItemGroup.PADDING_VEST, "100", 100));
        lines.add(line("2025-04-01", UniformItemGroup.PADDING_VEST, "100", 28));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));

        ItemPlan plan = calculator.plan(data, UniformItemGroup.PADDING_VEST, 2027, 1000);

        assertThat(plan.warnings()).anyMatch(w -> w.contains("격년") && w.contains("크게 낮아") && w.contains("2021년 117벌"));
        assertThat(plan.history()).extracting(y -> y.year()).contains(2021, 2023, 2025);
    }

    @Test
    void 뜻을_모르는_사이즈_코드와_출처_혼합을_경고한다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2026));
        lines.add(line("uniform-order-info", "2025-04-01", JUMPER, "50", 80));
        lines.add(line("uniform-order-info", "2025-04-01", JUMPER, "91", 20));
        lines.add(line("uniform-order", "2025-04-01", JUMPER, "100", 10));
        UniformDataset data = dataset(lines, Map.of(2025, 1000));

        ItemPlan plan = calculator.plan(data, JUMPER, 2027, 1000);

        assertThat(plan.sizes().stream().filter(z -> "91".equals(z.rawSize())).findFirst().orElseThrow().unknownCode()).isTrue();
        assertThat(plan.warnings()).anyMatch(w -> w.contains("뜻을 확인하지 못한") && w.contains("91"));
        assertThat(plan.warnings()).anyMatch(w -> w.contains("두 파일"));
    }

    @Test
    void 월_비중을_합_1로_환산한다() {
        var months = List.of(new RecommendationCalculator.MonthShare(4, 47, 0.47), new RecommendationCalculator.MonthShare(8, 23, 0.23));

        List<Double> shares = RecommendationCalculator.roundShares(months);

        assertThat(shares.get(0)).isCloseTo(0.671, within(0.001));
        assertThat(shares.stream().mapToDouble(Double::doubleValue).sum()).isCloseTo(1.0, within(1e-9));
        assertThat(RecommendationCalculator.roundShares(List.of())).isEmpty();
    }

    private static Map<String, Double> bySize(Map<SizeKey, Double> ratios) {
        Map<String, Double> result = new LinkedHashMap<>();
        ratios.forEach((k, v) -> result.put(k.rawSize(), v));
        return result;
    }
}
