package kr.fitorder.module.uniform;

import java.util.List;
import java.util.Map;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 근무복 설정(application.yml의 fitorder.uniform). */
@ConfigurationProperties(prefix = "fitorder.uniform")
public record UniformProperties(List<CodeRule> codeRules, List<ItemRule> itemRules,
                                Map<String, List<String>> unknownSizes) {

    /** 원천의 품목 코드로 분류하는 규칙. */
    public record CodeRule(String source, String code, String item, UniformItemGroup group) {
    }

    /** 품목명에 키워드가 들어 있으면 분류하는 규칙. item이 비면 원본 품목명을 쓴다. */
    public record ItemRule(String keyword, String item, UniformItemGroup group) {
    }

    public UniformProperties {
        codeRules = codeRules == null ? List.of() : List.copyOf(codeRules);
        itemRules = itemRules == null ? List.of() : List.copyOf(itemRules);
        unknownSizes = unknownSizes == null ? Map.of() : Map.copyOf(unknownSizes);
    }

    public List<String> unknownSizesOf(String source) {
        return unknownSizes.getOrDefault(source, List.of());
    }
}
