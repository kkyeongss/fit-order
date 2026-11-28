package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
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

/** 발주 추천. 결과는 저장하지 않고, 화면과 엑셀 모두 같은 입력값으로 그때그때 계산한다. */
@Service
@Transactional(readOnly = true)
public class RecommendationService {

    /**
     * @param year      발주 연도. 비우면 데이터 마지막 해의 다음 해
     * @param groups    품목군. 비우면 전체
     * @param headcount 대상 인원. 비우면 최근 정원
     */
    public record Request(Integer year, List<UniformItemGroup> groups, Integer headcount, Basis basis) {
    }

    public record Totals(int quantity, int safetyQuantity, BigDecimal amount, double savedQuantity,
                         BigDecimal savedAmount, BigDecimal carbonKg, boolean priceEstimated) {
    }

    /**
     * @param marketPrices  조달청 쇼핑몰 시장가와 환경표지제품 수·가격(녹색구매 비교용)
     * @param greenProducts 녹색제품 정보조회 서비스의 품목군별 유효 환경표지 인증 제품 수
     */
    public record Recommendation(int year, Basis basis, Integer headcount, YearHeadcount latestHeadcount,
                                 List<ItemPlan> items, Totals totals, List<MonthShare> orderMonths,
                                 double improvementRate, List<MarketPrice> marketPrices,
                                 List<GreenProductService.GroupSummary> greenProducts) {
    }

    private final UniformDatasetLoader loader;
    private final RecommendationCalculator calculator;
    private final Backtester backtester;
    private final AnalysisProperties props;
    private final MarketPriceService market;
    private final GreenProductService green;
    private final ResultCache cache;

    public RecommendationService(UniformDatasetLoader loader, RecommendationCalculator calculator,
                                 Backtester backtester, AnalysisProperties props, MarketPriceService market,
                                 GreenProductService green, ResultCache cache) {
        this.loader = loader;
        this.calculator = calculator;
        this.backtester = backtester;
        this.props = props;
        this.market = market;
        this.green = green;
        this.cache = cache;
    }

    /** 같은 입력은 데이터가 바뀔 때까지 다시 계산하지 않는다. 품목 순서·빈 목록처럼 뜻이 같은 입력은 하나로 본다. */
    public Recommendation recommend(Request request) {
        List<UniformItemGroup> groups = request.groups() == null || request.groups().isEmpty() ? null
                : request.groups().stream().distinct().sorted().toList();
        Request normalized = new Request(request.year(), groups, request.headcount(),
                request.basis() == null ? Basis.DEDUP : request.basis());
        return cache.get("recommend|" + normalized, () -> compute(normalized));
    }

    private Recommendation compute(Request request) {
        Basis basis = request.basis() == null ? Basis.DEDUP : request.basis();
        UniformDataset data = loader.load(basis);
        if (data.lines().isEmpty()) {
            throw new BusinessException("적재된 근무복 주문 데이터가 없습니다.");
        }
        int year = request.year() != null ? request.year() : RecommendationCalculator.defaultYear(data);
        if (data.completeYears().headSet(year).isEmpty()) {
            throw new BusinessException(year + "년 이전의 완전한 연도 데이터가 없어 추천할 수 없습니다.");
        }
        if (request.headcount() != null && request.headcount() <= 0) {
            throw new BusinessException("인원은 1명 이상이어야 합니다.");
        }
        YearHeadcount latest = RecommendationCalculator.latestHeadcount(data).orElse(null);
        Integer headcount = request.headcount() != null ? request.headcount()
                : latest == null ? null : latest.headcount();
        List<UniformItemGroup> groups = request.groups() == null || request.groups().isEmpty()
                ? data.groups() : request.groups().stream().filter(data.groups()::contains).toList();
        if (groups.isEmpty()) {
            throw new BusinessException("데이터가 있는 품목군을 고르세요.");
        }

        List<ItemPlan> items = groups.stream().map(g -> calculator.plan(data, g, year, headcount)).toList();
        Totals totals = new Totals(
                items.stream().mapToInt(ItemPlan::quantity).sum(),
                items.stream().mapToInt(ItemPlan::safetyQuantity).sum(),
                items.stream().map(ItemPlan::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).mapToDouble(s -> s.savedQuantity()).sum(),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).map(s -> s.savedAmount())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(ItemPlan::savings).filter(Objects::nonNull).map(s -> s.carbonKg())
                        .reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().anyMatch(ItemPlan::priceEstimated));
        return new Recommendation(year, basis, headcount, latest, items, totals, calculator.orderMonths(data),
                props.improvementRate(), market.list().stream().filter(m -> groups.contains(m.group())).toList(),
                green.summary().stream().filter(g -> g.group() == null || groups.contains(g.group())).toList());
    }

    public Backtester.Result backtest(Basis basis) {
        Basis b = basis == null ? Basis.DEDUP : basis;
        return cache.get("backtest|" + b, () -> backtester.run(loader.load(b)));
    }
}
