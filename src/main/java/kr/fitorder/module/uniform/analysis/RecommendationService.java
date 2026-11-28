package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import kr.fitorder.common.Basis;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ResultCache;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.ItemPlan;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.MonthShare;
import kr.fitorder.module.uniform.analysis.UniformDataset.YearHeadcount;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.price.GreenProductService;
import kr.fitorder.module.uniform.price.MarketPriceService;
import kr.fitorder.module.uniform.price.MarketPriceService.MarketPrice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발주 추천. 결과는 DB에 저장하지 않고 입력값으로 계산한다(같은 입력은 데이터가 바뀔 때까지 메모리에 둔다).
 * 저장이 필요하면 PlanService로 '발주안'을 따로 저장한다.
 */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    /** 추천할 수 있는 연도 범위: 마지막 완전한 해의 다음 해부터 3년. 과거·먼 미래를 넣으면 엉뚱한 값이 나오기 때문이다. */
    private static final int YEARS_AHEAD = 3;
    /** 입력 인원이 최근 정원과 이 배수 이상 차이 나면 경고한다. */
    private static final double HEADCOUNT_WARN_RATIO = 3.0;

    /**
     * @param year      발주 연도. 비우면 데이터 마지막 해의 다음 해
     * @param groups    품목군. 비우면 전체
     * @param headcount 대상 인원. 비우면 최근 정원
     * @param totals    담당자가 총량(1단계)을 직접 지정한 품목군별 값. 비우면 과거 기록으로 계산
     */
    public record Request(Integer year, List<UniformItemGroup> groups, Integer headcount, Basis basis,
                          Map<UniformItemGroup, Integer> totals) {

        public Request(Integer year, List<UniformItemGroup> groups, Integer headcount, Basis basis) {
            this(year, groups, headcount, basis, null);
        }
    }

    /** 총량 오차(백테스트 평균)를 반영한 참고 범위. */
    public record Range(int low, int high, double errorRate) {
    }

    public record RoundTotal(int month, double share, int quantity, BigDecimal amount) {
    }

    /**
     * @param quantity      필요 수량(안전재고 포함), amount는 그 금액
     * @param orderQuantity 재고를 뺀 발주 수량, orderAmount는 그 금액(재고를 입력하지 않았으면 필요 수량과 같다)
     * @param range         필요 수량의 참고 범위(백테스트 평균 오차 기준)
     * @param rounds        정기 발주 회차별(4월·8월) 발주 수량·금액
     */
    public record Totals(int quantity, int safetyQuantity, BigDecimal amount, double savedQuantity,
                         BigDecimal savedAmount, BigDecimal carbonKg, boolean priceEstimated, int stockTotal,
                         int orderQuantity, BigDecimal orderAmount, Range range, List<RoundTotal> rounds) {
    }

    /** 반대 집계 기준(중복 제거 ↔ 원자료)으로 계산했을 때의 합계. 두 기준의 차이를 범위로 보여주기 위한 값. */
    public record OtherBasis(Basis basis, int quantity, int orderQuantity, BigDecimal orderAmount) {
    }

    public record YearRange(int from, int to) {
    }

    /**
     * @param marketPrices  조달청 쇼핑몰 시장가와 환경표지제품 수·가격(녹색구매 비교용)
     * @param greenProducts 녹색제품 정보조회 서비스의 품목군별 유효 환경표지 인증 제품 수
     * @param warnings      입력값에 대한 경고(품목별 경고는 ItemPlan.warnings)
     * @param lastOrderDate 사용한 데이터의 마지막 주문일
     */
    public record Recommendation(int year, Basis basis, Integer headcount, YearHeadcount latestHeadcount,
                                 List<ItemPlan> items, Totals totals, List<MonthShare> orderMonths,
                                 double improvementRate, List<MarketPrice> marketPrices,
                                 List<GreenProductService.GroupSummary> greenProducts, List<String> warnings,
                                 YearRange yearRange, OtherBasis otherBasis, LocalDate lastOrderDate) {
    }

    private final UniformDatasetLoader loader;
    private final RecommendationCalculator calculator;
    private final Backtester backtester;
    private final AnalysisProperties props;
    private final MarketPriceService market;
    private final GreenProductService green;
    private final StockService stock;
    private final ResultCache cache;

    public RecommendationService(UniformDatasetLoader loader, RecommendationCalculator calculator,
                                 Backtester backtester, AnalysisProperties props, MarketPriceService market,
                                 GreenProductService green, StockService stock, ResultCache cache) {
        this.loader = loader;
        this.calculator = calculator;
        this.backtester = backtester;
        this.props = props;
        this.market = market;
        this.green = green;
        this.stock = stock;
        this.cache = cache;
    }

    /** 같은 입력은 데이터가 바뀔 때까지 다시 계산하지 않는다. 품목 순서·빈 목록처럼 뜻이 같은 입력은 하나로 본다. */
    public Recommendation recommend(Request request) {
        Request normalized = normalize(request);
        return cache.get("recommend|" + normalized, () -> compute(normalized, true));
    }

    static Request normalize(Request request) {
        List<UniformItemGroup> groups = request.groups() == null || request.groups().isEmpty() ? null
                : request.groups().stream().distinct().sorted().toList();
        Map<UniformItemGroup, Integer> totals = request.totals() == null || request.totals().isEmpty() ? null
                : new TreeMap<>(request.totals());
        return new Request(request.year(), groups, request.headcount(),
                request.basis() == null ? Basis.DEDUP : request.basis(), totals);
    }

    private Recommendation compute(Request request, boolean withOther) {
        Basis basis = request.basis() == null ? Basis.DEDUP : request.basis();
        UniformDataset data = loader.load(basis);
        if (data.lines().isEmpty()) {
            throw new BusinessException("적재된 근무복 주문 데이터가 없습니다.");
        }
        var complete = data.completeYears();
        if (complete.isEmpty()) {
            throw new BusinessException("1년치가 모두 있는 연도가 없어 추천할 수 없습니다.");
        }
        YearRange yearRange = new YearRange(complete.last() + 1, complete.last() + YEARS_AHEAD);
        int year = request.year() != null ? request.year() : RecommendationCalculator.defaultYear(data);
        if (year < yearRange.from() || year > yearRange.to()) {
            throw new BusinessException(year + "년은 추천할 수 없는 연도입니다. 마지막으로 1년치가 다 있는 해(" + complete.last()
                    + "년) 다음부터 " + yearRange.to() + "년까지(" + yearRange.from() + "~" + yearRange.to() + "년) 고르세요.");
        }
        if (request.headcount() != null && request.headcount() <= 0) {
            throw new BusinessException("인원은 1명 이상이어야 합니다.");
        }
        if (request.totals() != null && request.totals().values().stream().anyMatch(v -> v == null || v < 0)) {
            throw new BusinessException("직접 지정하는 총량은 0 이상이어야 합니다.");
        }
        YearHeadcount latest = RecommendationCalculator.latestHeadcount(data).orElse(null);
        Integer headcount = request.headcount() != null ? request.headcount()
                : latest == null ? null : latest.headcount();
        List<UniformItemGroup> groups = request.groups() == null || request.groups().isEmpty()
                ? data.groups() : request.groups().stream().filter(data.groups()::contains).toList();
        if (groups.isEmpty()) {
            throw new BusinessException("데이터가 있는 품목군을 고르세요.");
        }

        List<MonthShare> months = calculator.orderMonths(data);
        List<Double> roundShares = RecommendationCalculator.roundShares(months);
        var stocks = stock.current();
        Map<UniformItemGroup, Integer> overrides = request.totals() == null ? Map.of() : request.totals();
        List<ItemPlan> items = groups.stream()
                .map(g -> calculator.plan(data, g, year, headcount, overrides.get(g), stocks, roundShares)).toList();

        int quantity = items.stream().mapToInt(ItemPlan::quantity).sum();
        double error = backtestError(basis);
        Range range = new Range((int) Math.round(quantity * (1 - error)), (int) Math.round(quantity * (1 + error)), error);
        List<RoundTotal> rounds = new ArrayList<>();
        for (int i = 0; i < roundShares.size(); i++) {
            int idx = i;
            rounds.add(new RoundTotal(months.get(i).month(), roundShares.get(i),
                    items.stream().mapToInt(it -> it.rounds().get(idx).quantity()).sum(),
                    items.stream().map(it -> it.rounds().get(idx).amount()).reduce(BigDecimal.ZERO, BigDecimal::add)));
        }
        Totals totals = new Totals(
                quantity,
                items.stream().mapToInt(ItemPlan::safetyQuantity).sum(),
                items.stream().map(ItemPlan::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).mapToDouble(s -> s.savedQuantity()).sum(),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).map(s -> s.savedAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).map(s -> s.carbonKg())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().anyMatch(ItemPlan::priceEstimated),
                items.stream().mapToInt(ItemPlan::stockTotal).sum(),
                items.stream().mapToInt(ItemPlan::orderQuantity).sum(),
                items.stream().map(ItemPlan::orderAmount).reduce(BigDecimal.ZERO, BigDecimal::add), range, rounds);

        List<String> warnings = new ArrayList<>();
        if (request.headcount() != null && latest != null && latest.headcount() > 0) {
            double ratio = request.headcount() / (double) latest.headcount();
            if (ratio >= HEADCOUNT_WARN_RATIO || ratio <= 1 / HEADCOUNT_WARN_RATIO) {
                warnings.add("입력한 인원 " + request.headcount() + "명이 최근 정원 " + latest.headcount() + "명의 "
                        + String.format("%.1f", ratio) + "배입니다. 인원을 확인하세요.");
            }
        }
        if (quantity == 0) {
            warnings.add("입력한 인원이 너무 적거나 과거 기록이 없어 필요 수량이 0벌로 계산됐습니다.");
        }
        OtherBasis other = null;
        if (withOther) {
            Basis otherBasis = basis == Basis.DEDUP ? Basis.RAW : Basis.DEDUP;
            Request otherRequest = new Request(year, request.groups(), request.headcount(), otherBasis, request.totals());
            try {
                Recommendation o = cache.get("recommend-core|" + otherRequest, () -> compute(otherRequest, false));
                other = new OtherBasis(otherBasis, o.totals().quantity(), o.totals().orderQuantity(),
                        o.totals().orderAmount());
            } catch (BusinessException e) {
                // 반대 기준 계산이 안 되면 비교만 생략한다
            }
        }
        LocalDate lastOrder = data.lines().stream().map(OrderLine::date).max(Comparator.naturalOrder()).orElse(null);
        return new Recommendation(year, basis, headcount, latest, items, totals, months, props.improvementRate(),
                market.list().stream().filter(m -> groups.contains(m.group())).toList(),
                green.summary().stream().filter(g -> g.group() == null || groups.contains(g.group())).toList(),
                warnings, yearRange, other, lastOrder);
    }

    /** 백테스트 평균 총량 오차(추천 로직, 합계). 계산할 수 없으면 0.2(기획서 값)로 본다. */
    private double backtestError(Basis basis) {
        try {
            return backtest(basis).total().averages().stream()
                    .filter(a -> a.method() == Backtester.Method.RECOMMENDATION)
                    .mapToDouble(Backtester.Average::totalError).findFirst().orElse(0.2);
        } catch (RuntimeException e) {
            return 0.2;
        }
    }

    public Backtester.Result backtest(Basis basis) {
        Basis b = basis == null ? Basis.DEDUP : basis;
        return cache.get("backtest|" + b, () -> backtester.run(loader.load(b)));
    }
}
