package kr.fitorder.module.uniform.price;

import java.util.List;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 조달청 나라장터 쇼핑몰 근무복 시장가 설정(application.yml의 fitorder.uniform.market-price).
 *
 * @param url             ShoppingMallPrdctInfoService 주소
 * @param operations      부를 오퍼레이션(다수공급자계약, 일반단가계약 등)
 * @param itemName        품명(prdctClsfcNoNm). 쇼핑몰에서는 셔츠·바지 모두 품명이 '근무복'이고 규격명으로 구분된다
 * @param months          최근 몇 달 동안 등록된 계약상품을 볼지(API 제한 12개월)
 * @param minSamples      품목군 표본이 이보다 적으면 시장가로 쓰지 않는다
 * @param excludeKeywords 규격명에 있으면 빼는 말(상·하의 한 벌 묶음 등)
 * @param rules           규격명 키워드 → 품목군. 위에서부터 처음 맞는 규칙을 쓴다
 */
@ConfigurationProperties(prefix = "fitorder.uniform.market-price")
public record MarketPriceProperties(String url, List<String> operations, String itemName, int months,
                                    int minSamples, List<String> excludeKeywords, List<Rule> rules) {

    public record Rule(UniformItemGroup group, List<String> keywords) {
    }

    public MarketPriceProperties {
        operations = operations == null ? List.of() : List.copyOf(operations);
        excludeKeywords = excludeKeywords == null ? List.of() : List.copyOf(excludeKeywords);
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
