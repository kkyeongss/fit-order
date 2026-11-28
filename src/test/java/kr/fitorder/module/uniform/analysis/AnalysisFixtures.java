package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.Basis;
import kr.fitorder.module.uniform.analysis.UniformDataset.UnitPrice;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;

/** application.yml과 같은 계수, 손으로 만든 작은 데이터. */
final class AnalysisFixtures {

    private AnalysisFixtures() {
    }

    static AnalysisProperties properties() {
        return new AnalysisProperties(2020, 2021, 0.5, 10, 3, 2, List.of(3, 2, 1),
                java.util.Set.of(UniformItemGroup.JACKET, UniformItemGroup.PADDING_VEST), 0.05,
                Map.of(UniformItemGroup.JACKET, 0.10, UniformItemGroup.PANTS, 0.10, UniformItemGroup.PADDING_VEST, 0.10), List.of(4, 8),
                new BigDecimal("53000"), 2022, 2025);
    }

    static OrderLine line(String date, UniformItemGroup group, String size, int quantity) {
        return line("A", date, group, size, quantity);
    }

    static OrderLine line(String source, String date, UniformItemGroup group, String size, int quantity) {
        return new OrderLine(source, LocalDate.parse(date), group, size, size + " (" + source + ")", quantity, false);
    }

    /** 연도 경계 표시용: 첫 해 1월, 마지막 해 12월에 1벌씩 넣어 그 사이를 완전한 해로 만든다. */
    static List<OrderLine> bounds(int from, int to) {
        return List.of(line(from + "-01-02", UniformItemGroup.VEST, "1", 1),
                line(to + "-12-30", UniformItemGroup.VEST, "1", 1));
    }

    static UniformDataset dataset(List<OrderLine> lines, Map<Integer, Integer> headcounts) {
        Map<Integer, YearHeadcount> hc = new java.util.HashMap<>();
        headcounts.forEach((y, n) -> hc.put(y, new YearHeadcount(y, LocalDate.of(y, 12, 31), n)));
        Map<UniformItemGroup, UnitPrice> prices = new EnumMap<>(UniformItemGroup.class);
        for (UniformItemGroup g : UniformItemGroup.values()) {
            prices.put(g, new UnitPrice(new BigDecimal("50000"), false));
        }
        Map<UniformItemGroup, BigDecimal> carbon = new EnumMap<>(UniformItemGroup.class);
        for (UniformItemGroup g : UniformItemGroup.values()) {
            carbon.put(g, new BigDecimal("10"));
        }
        return new UniformDataset(Basis.DEDUP, new ArrayList<>(lines), hc, prices, carbon);
    }
}
