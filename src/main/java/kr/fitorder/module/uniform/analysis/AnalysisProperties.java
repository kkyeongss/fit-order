package kr.fitorder.module.uniform.analysis;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 근무복 지표·추천 계수(application.yml의 fitorder.uniform.analysis). 코드 대신 설정으로 바꾼다.
 *
 * @param stableFromYear   두 출처가 모두 쌓이기 시작한 해. 연평균·미스매치 비교 구간은 이 해부터 쓴다
 * @param perCapitaFromYear 1인당 지급량을 계산하는 첫 해(이전 정원은 지원직·계약직 미포함)
 * @param improvementRate  추천으로 줄일 수 있다고 보는 미스매치 비율(절감 수량 = 미스매치율 × 연간 주문량 × 이 값)
 * @param smallOrderMax    이 수량 이하인 주문일을 소량 발주로 본다
 * @param windowYears      사이즈 비율 비교 구간 길이(최근 N년 vs 그 전 N년)
 * @param averageYears     추천 총량에 쓰는 직전 연도 수
 * @param sizeWeights      사이즈 배분 가중치. 최근 연도부터
 * @param biennialGroups   격년 주문 품목. 총량에 2년 전(같은 홀짝 해) 값을 쓴다
 * @param safetyStock      품목군별 안전재고 비율. 없으면 defaultSafetyStock
 * @param orderMonths      정기 발주 시기로 제안하는 달
 * @param defaultUnitPrice 단가를 입력하지 않은 품목의 추정 단가(원)
 * @param backtestFromYear 백테스트 첫 해
 * @param backtestToYear   백테스트 마지막 해
 */
@ConfigurationProperties(prefix = "fitorder.uniform.analysis")
public record AnalysisProperties(int stableFromYear, int perCapitaFromYear, double improvementRate,
                                 int smallOrderMax, int windowYears, int averageYears, List<Integer> sizeWeights,
                                 Set<UniformItemGroup> biennialGroups, double defaultSafetyStock,
                                 Map<UniformItemGroup, Double> safetyStock, List<Integer> orderMonths,
                                 BigDecimal defaultUnitPrice, int backtestFromYear, int backtestToYear) {

    public AnalysisProperties {
        sizeWeights = sizeWeights == null || sizeWeights.isEmpty() ? List.of(3, 2, 1) : List.copyOf(sizeWeights);
        biennialGroups = biennialGroups == null ? Set.of() : Set.copyOf(biennialGroups);
        safetyStock = safetyStock == null ? Map.of() : Map.copyOf(safetyStock);
        orderMonths = orderMonths == null ? List.of() : List.copyOf(orderMonths);
    }

    public double safetyStockOf(UniformItemGroup group) {
        return safetyStock.getOrDefault(group, defaultSafetyStock);
    }
}
