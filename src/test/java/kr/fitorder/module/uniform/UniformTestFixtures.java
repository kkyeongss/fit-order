package kr.fitorder.module.uniform;

import java.util.List;
import java.util.Map;
import kr.fitorder.module.uniform.UniformProperties.CodeRule;
import kr.fitorder.module.uniform.UniformProperties.ItemRule;
import kr.fitorder.module.uniform.domain.UniformItemGroup;

/** application.yml과 같은 근무복 설정. */
public final class UniformTestFixtures {

    private UniformTestFixtures() {
    }

    public static UniformProperties properties() {
        return new UniformProperties(
                List.of(new CodeRule(UniformSources.ORDER_INFO, "10", "점퍼", UniformItemGroup.JUMPER),
                        new CodeRule(UniformSources.ORDER_INFO, "11", "점퍼", UniformItemGroup.JUMPER)),
                List.of(new ItemRule("패딩", null, UniformItemGroup.PADDING_VEST),
                        new ItemRule("조끼", null, UniformItemGroup.VEST),
                        new ItemRule("재킷", null, UniformItemGroup.JACKET),
                        new ItemRule("점퍼", "점퍼", UniformItemGroup.JUMPER),
                        new ItemRule("바지", null, UniformItemGroup.PANTS),
                        new ItemRule("셔츠", null, UniformItemGroup.SHIRT)),
                Map.of(UniformSources.ORDER_INFO, List.of("91", "95")));
    }
}
