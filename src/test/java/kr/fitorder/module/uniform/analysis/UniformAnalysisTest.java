package kr.fitorder.module.uniform.analysis;

import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.bounds;
import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.dataset;
import static kr.fitorder.module.uniform.analysis.AnalysisFixtures.line;
import static kr.fitorder.module.uniform.domain.UniformItemGroup.SHIRT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import kr.fitorder.module.uniform.analysis.Period.Comparison;
import org.junit.jupiter.api.Test;

class UniformAnalysisTest {

    private final UniformAnalysis analysis = new UniformAnalysis(AnalysisFixtures.properties());

    @Test
    void 미스매치율은_늘어난_비율의_합이다() {
        // 95: 60% → 40%, 100: 40% → 50%, 105: 0% → 10%  ⇒ 0.1 + 0.1 = 0.2
        double rate = UniformAnalysis.mismatch(Map.of("95", 0.6, "100", 0.4),
                Map.of("95", 0.4, "100", 0.5, "105", 0.1));
        assertThat(rate).isCloseTo(0.2, within(1e-9));
    }

    @Test
    void 첫_해와_마지막_해가_1년치가_안_되면_완전한_해에서_뺀다() {
        UniformDataset data = dataset(List.of(line("2017-11-06", SHIRT, "95", 1), line("2020-05-01", SHIRT, "95", 1),
                line("2026-08-27", SHIRT, "95", 1)), Map.of());

        assertThat(data.completeYears()).containsExactly(2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025);
        assertThat(data.years()).contains(2017, 2026);
    }

    @Test
    void 비교_구간은_안정_연도_이후_3년씩이다() {
        UniformDataset data = dataset(bounds(2018, 2025), Map.of());

        assertThat(analysis.comparisons(data))
                .containsExactly(new Comparison(new Period(2020, 2022), new Period(2023, 2025)));
    }

    @Test
    void 절감_수량은_미스매치율_곱하기_연간_주문량_곱하기_개선률이다() {
        List<OrderLine> lines = new ArrayList<>(bounds(2020, 2025));
        // 이전 구간 95만 300벌, 최근 구간 95 150벌 + 100 150벌 ⇒ 미스매치 0.5, 연간 100벌
        lines.add(line("2021-04-01", SHIRT, "95", 300));
        lines.add(line("2024-04-01", SHIRT, "95", 150));
        lines.add(line("2024-04-01", SHIRT, "100", 150));
        UniformDataset data = dataset(lines, Map.of());

        var savings = analysis.savings(data, SHIRT, analysis.latestComparison(data).orElseThrow()).orElseThrow();

        assertThat(savings.mismatchRate()).isCloseTo(0.5, within(1e-9));
        assertThat(savings.annualQuantity()).isCloseTo(100, within(1e-9));
        assertThat(savings.savedQuantity()).isCloseTo(25, within(1e-9));      // 0.5 × 100 × 0.5
        assertThat(savings.savedAmount()).isEqualByComparingTo(new BigDecimal("1250000")); // × 50,000원
        assertThat(savings.carbonKg()).isEqualByComparingTo(new BigDecimal("250"));        // × 10 kg
    }

    @Test
    void 발주_일수는_두_출처를_합친_고유_주문일이고_소량_발주는_하루_10벌_이하다() {
        UniformDataset data = dataset(List.of(
                line("A", "2024-04-01", SHIRT, "95", 6),
                line("B", "2024-04-01", SHIRT, "95", 6),   // 같은 날 합계 12벌 → 소량 아님
                line("A", "2024-05-01", SHIRT, "95", 10),  // 10벌 → 소량
                line("B", "2024-08-01", SHIRT, "95", 3)), Map.of());

        assertThat(UniformAnalysis.orderDays(data, 2024)).isEqualTo(3);
        assertThat(analysis.smallOrderRatio(data, 2024).getAsDouble()).isCloseTo(2 / 3.0, within(1e-9));
    }

    @Test
    void 일인당_지급량은_2021년부터_그해_정원으로_나눈다() {
        UniformDataset data = dataset(List.of(line("2020-04-01", SHIRT, "95", 100),
                line("2021-04-01", SHIRT, "95", 300)), Map.of(2020, 200, 2021, 1500));

        assertThat(analysis.perCapita(data, 2020)).isEmpty();
        assertThat(analysis.perCapita(data, 2021).getAsDouble()).isCloseTo(0.2, within(1e-9));
    }
}
