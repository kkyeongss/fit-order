package kr.fitorder.module.uniform.loader;

import kr.fitorder.module.uniform.UniformProperties;
import kr.fitorder.module.uniform.UniformProperties.CodeRule;
import kr.fitorder.module.uniform.UniformProperties.ItemRule;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.stereotype.Component;

/**
 * 원본 품목을 표준 품목·품목군으로 분류한다. 규칙은 설정(fitorder.uniform)에 있다.
 * 코드 규칙 → 품목명 키워드 규칙 순서로 보고, 맞는 규칙이 없으면 UNKNOWN.
 */
@Component
public class ItemClassifier {

    public record Classification(String item, UniformItemGroup group) {
    }

    private final UniformProperties properties;

    public ItemClassifier(UniformProperties properties) {
        this.properties = properties;
    }

    public Classification classify(String source, String itemCode, String rawName) {
        String name = RecordParser.normalizeName(rawName);
        if (itemCode != null) {
            for (CodeRule rule : properties.codeRules()) {
                if (rule.source().equals(source) && rule.code().equals(itemCode)) {
                    return new Classification(rule.item() != null ? rule.item() : name, rule.group());
                }
            }
        }
        if (name != null) {
            for (ItemRule rule : properties.itemRules()) {
                if (name.contains(rule.keyword())) {
                    return new Classification(rule.item() != null ? rule.item() : name, rule.group());
                }
            }
        }
        return new Classification(name, UniformItemGroup.UNKNOWN);
    }
}
