package kr.fitorder.module.uniform.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import kr.fitorder.module.uniform.analysis.RecommendationCalculator.SizeKey;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.stereotype.Component;

/**
 * 과거 해를 하나씩 '모르는 해'로 두고 그 전 데이터만으로 예측해 실제와 비교한다.
 * <ul>
 *   <li>기준선 1: 직전 1년 값. 기준선 2: 직전 2년 평균. 둘 다 사이즈는 직전 해 비율</li>
 *   <li>추천: RecommendationCalculator의 총량(격년 규칙)·사이즈 배분. 인원 보정과 안전재고는 빼고 비교한다</li>
 * </ul>
 * 총량 오차 = |예측 − 실제| ÷ 실제. 사이즈 오차 = Σ|사이즈별 예측 − 실제| ÷ 실제 총량(남고 모자란 벌 수의 합).
 * 품목군별·연도별로 구하고 연도 평균을 낸다. '합계'는 전 품목을 더한 값으로 같은 식을 쓴다.
 */
@Component
public class Backtester {

    public enum Method {
        PREVIOUS_YEAR("직전 1년"), TWO_YEAR_AVERAGE("직전 2년 평균"), RECOMMENDATION("추천 로직");

        private final String label;

        Method(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Prediction(Method method, double quantity, double totalError, double sizeError) {
    }

    public record YearResult(int year, int actual, List<Prediction> predictions) {
    }

    public record Average(Method method, String label, double totalError, double sizeError) {
    }

    /** group이 null이면 합계. */
    public record GroupResult(UniformItemGroup group, String label, List<YearResult> years, List<Average> averages) {
    }

    public record Result(int fromYear, int toYear, List<GroupResult> groups, GroupResult total) {
    }

    private final RecommendationCalculator recommendations;
    private final AnalysisProperties props;

    public Backtester(RecommendationCalculator recommendations, AnalysisProperties props) {
        this.recommendations = recommendations;
        this.props = props;
    }

    public Result run(UniformDataset data) {
        List<Integer> years = data.completeYears().stream()
                .filter(y -> y >= props.backtestFromYear() && y <= props.backtestToYear()).toList();
        List<UniformItemGroup> groups = data.groups();

        List<GroupResult> results = new ArrayList<>();
        List<List<Forecast>> byYear = new ArrayList<>();
        years.forEach(y -> byYear.add(new ArrayList<>()));
        for (UniformItemGroup group : groups) {
            List<YearResult> yearResults = new ArrayList<>();
            for (int i = 0; i < years.size(); i++) {
                List<Forecast> forecasts = forecasts(data, group, years.get(i));
                byYear.get(i).addAll(forecasts);
                score(years.get(i), forecasts).ifPresent(yearResults::add);
            }
            results.add(new GroupResult(group, group.label(), yearResults, averages(yearResults)));
        }
        List<YearResult> totalYears = new ArrayList<>();
        for (int i = 0; i < years.size(); i++) {
            score(years.get(i), byYear.get(i)).ifPresent(totalYears::add);
        }
        GroupResult total = new GroupResult(null, "합계", totalYears, averages(totalYears));
        return new Result(props.backtestFromYear(), props.backtestToYear(), results, total);
    }

    /** 방법 하나가 품목군 하나·한 해에 낸 예측. 사이즈별 예측 수량과 실제 수량. */
    private record Forecast(Method method, double quantity, Map<SizeKey, Double> predicted,
                            Map<SizeKey, Integer> actual) {
    }

    private List<Forecast> forecasts(UniformDataset data, UniformItemGroup group, int year) {
        List<OrderLine> lines = data.lines(group);
        Function<OrderLine, SizeKey> key = l -> new SizeKey(l.source(), l.rawSize(), l.size());
        Map<SizeKey, Integer> actual = sizes(lines, year, key);
        Map<Integer, Integer> byYear = UniformAnalysis.quantityByYear(lines);

        Map<SizeKey, Double> lastRatio = UniformAnalysis.shares(
                lines.stream().filter(l -> l.year() == year - 1).toList(), key);
        double prev1 = byYear.getOrDefault(year - 1, 0);
        double prev2 = (byYear.getOrDefault(year - 1, 0) + byYear.getOrDefault(year - 2, 0)) / 2.0;
        double rec = recommendations.total(data, group, year).quantity();
        Map<SizeKey, Double> recRatio = recommendations.sizeRatios(data, group, year);

        return List.of(
                new Forecast(Method.PREVIOUS_YEAR, prev1, scale(lastRatio, prev1), actual),
                new Forecast(Method.TWO_YEAR_AVERAGE, prev2, scale(lastRatio, prev2), actual),
                new Forecast(Method.RECOMMENDATION, rec, scale(recRatio, rec), actual));
    }

    /** 같은 해의 예측들(한 품목군 또는 전 품목군)을 방법별로 합쳐 오차를 낸다. 실제가 0이면 건너뛴다. */
    private Optional<YearResult> score(int year, List<Forecast> forecasts) {
        int actual = forecasts.stream().filter(f -> f.method() == Method.PREVIOUS_YEAR)
                .mapToInt(f -> f.actual().values().stream().mapToInt(Integer::intValue).sum()).sum();
        if (actual == 0) {
            return Optional.empty();
        }
        List<Prediction> predictions = new ArrayList<>();
        for (Method method : Method.values()) {
            double quantity = 0;
            double sizeMiss = 0;
            for (Forecast f : forecasts) {
                if (f.method() != method) {
                    continue;
                }
                quantity += f.quantity();
                Set<SizeKey> keys = new HashSet<>(f.predicted().keySet());
                keys.addAll(f.actual().keySet());
                for (SizeKey k : keys) {
                    sizeMiss += Math.abs(f.predicted().getOrDefault(k, 0.0) - f.actual().getOrDefault(k, 0));
                }
            }
            predictions.add(new Prediction(method, quantity, Math.abs(quantity - actual) / actual, sizeMiss / actual));
        }
        return Optional.of(new YearResult(year, actual, predictions));
    }

    private static List<Average> averages(List<YearResult> years) {
        List<Average> result = new ArrayList<>();
        for (Method method : Method.values()) {
            List<Prediction> ps = years.stream()
                    .flatMap(y -> y.predictions().stream().filter(p -> p.method() == method)).toList();
            result.add(new Average(method, method.label(),
                    ps.stream().mapToDouble(Prediction::totalError).average().orElse(0),
                    ps.stream().mapToDouble(Prediction::sizeError).average().orElse(0)));
        }
        return result;
    }

    private static Map<SizeKey, Integer> sizes(List<OrderLine> lines, int year, Function<OrderLine, SizeKey> key) {
        Map<SizeKey, Integer> result = new HashMap<>();
        lines.stream().filter(l -> l.year() == year).forEach(l -> result.merge(key.apply(l), l.quantity(), Integer::sum));
        return result;
    }

    private static Map<SizeKey, Double> scale(Map<SizeKey, Double> ratios, double quantity) {
        Map<SizeKey, Double> result = new HashMap<>();
        ratios.forEach((k, r) -> result.put(k, r * quantity));
        return result;
    }
}
