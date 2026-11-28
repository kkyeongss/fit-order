package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;
import kr.fitorder.common.Basis;
import kr.fitorder.module.uniform.domain.UniformItemGroup;

/**
 * 지표·추천 계산에 필요한 데이터 한 벌. DB에서 한 번 읽어 메모리에서 계산한다(수천 행).
 *
 * @param lines      집계 기준(basis)에 맞춘 주문 행. RAW면 중복 행도 들어 있다
 * @param headcounts 연도별 정원(그해 마지막 기준일자의 전체 정원)
 * @param prices     품목군별 단가. 입력값이 없으면 추정 단가
 * @param carbon     품목군별 탄소계수(kg CO2e/벌)
 */
public record UniformDataset(Basis basis, List<OrderLine> lines, Map<Integer, YearHeadcount> headcounts,
                             Map<UniformItemGroup, UnitPrice> prices, Map<UniformItemGroup, BigDecimal> carbon) {

    public record YearHeadcount(int year, LocalDate baseDate, int headcount) {
    }

    public record UnitPrice(BigDecimal price, boolean estimated) {
    }

    public UniformDataset {
        lines = List.copyOf(lines);
        headcounts = Map.copyOf(headcounts);
        prices = Map.copyOf(prices);
        carbon = Map.copyOf(carbon);
    }

    /**
     * 1년치가 다 들어 있는 해. 첫 해는 1월부터, 마지막 해는 12월까지 데이터가 있어야 한다.
     * 예) 2017-11 ~ 2026-08이면 2018~2025.
     */
    public SortedSet<Integer> completeYears() {
        SortedSet<Integer> years = new TreeSet<>();
        Optional<LocalDate> first = lines.stream().map(OrderLine::date).min(Comparator.naturalOrder());
        Optional<LocalDate> last = lines.stream().map(OrderLine::date).max(Comparator.naturalOrder());
        if (first.isEmpty()) {
            return years;
        }
        int from = first.get().getMonth() == Month.JANUARY ? first.get().getYear() : first.get().getYear() + 1;
        int to = last.get().getMonth() == Month.DECEMBER ? last.get().getYear() : last.get().getYear() - 1;
        for (int y = from; y <= to; y++) {
            years.add(y);
        }
        return years;
    }

    /** 데이터가 있는 모든 해(불완전 연도 포함). */
    public SortedSet<Integer> years() {
        SortedSet<Integer> years = new TreeSet<>();
        lines.forEach(l -> years.add(l.year()));
        return years;
    }

    /** 같은 데이터의 중복 제거 기준. RAW로 한 번 읽어 두 기준을 다 쓸 때. */
    public UniformDataset dedup() {
        return basis == Basis.DEDUP ? this
                : new UniformDataset(Basis.DEDUP, lines(l -> !l.duplicate()), headcounts, prices, carbon);
    }

    /** RAW로 읽은 데이터에서 원하는 기준을 고른다. */
    public UniformDataset withBasis(Basis target) {
        return target == Basis.DEDUP ? dedup() : this;
    }

    public List<OrderLine> lines(Predicate<OrderLine> filter) {
        return lines.stream().filter(filter).toList();
    }

    public List<OrderLine> lines(UniformItemGroup group) {
        return lines(l -> l.group() == group);
    }

    /** 데이터에 나오는 품목군(미분류 제외), 정의 순서대로. */
    public List<UniformItemGroup> groups() {
        return lines.stream().map(OrderLine::group).filter(g -> g != UniformItemGroup.UNKNOWN)
                .distinct().sorted().toList();
    }

    public UnitPrice priceOf(UniformItemGroup group) {
        return prices.get(group);
    }

    public BigDecimal carbonOf(UniformItemGroup group) {
        return carbon.getOrDefault(group, BigDecimal.ZERO);
    }
}
