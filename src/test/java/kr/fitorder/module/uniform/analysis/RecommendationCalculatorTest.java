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
import org.junit.jupiter.api.Test;

class RecommendationCalculatorTest {

    private final RecommendationCalculator calculator =
            new RecommendationCalculator(new UniformAnalysis(AnalysisFixtures.properties()));

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

    private static Map<String, Double> bySize(Map<SizeKey, Double> ratios) {
        Map<String, Double> result = new LinkedHashMap<>();
        ratios.forEach((k, v) -> result.put(k.rawSize(), v));
        return result;
    }
}
