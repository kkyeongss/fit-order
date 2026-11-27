package kr.fitorder.module.uniform.loader;

import static org.assertj.core.api.Assertions.assertThat;

import kr.fitorder.module.uniform.UniformSources;
import kr.fitorder.module.uniform.UniformTestFixtures;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.loader.ItemClassifier.Classification;
import org.junit.jupiter.api.Test;

class ItemClassifierTest {

    private final ItemClassifier classifier = new ItemClassifier(UniformTestFixtures.properties());

    @Test
    void 점퍼_코드_10과_11은_같은_품목으로_묶는다() {
        assertThat(classifier.classify(UniformSources.ORDER_INFO, "10", "근무복 점퍼"))
                .isEqualTo(new Classification("점퍼", UniformItemGroup.JUMPER));
        assertThat(classifier.classify(UniformSources.ORDER_INFO, "11", "동계점퍼(신형)"))
                .isEqualTo(new Classification("점퍼", UniformItemGroup.JUMPER));
    }

    @Test
    void 두_파일의_점퍼는_품목명으로_합쳐진다() {
        assertThat(classifier.classify(UniformSources.ORDER, null, "점퍼").item()).isEqualTo("점퍼");
    }

    @Test
    void 패딩조끼는_조끼보다_먼저_분류된다() {
        assertThat(classifier.classify(UniformSources.ORDER, null, "패딩조끼").group())
                .isEqualTo(UniformItemGroup.PADDING_VEST);
        assertThat(classifier.classify(UniformSources.ORDER_INFO, "20", "조끼").group())
                .isEqualTo(UniformItemGroup.VEST);
    }

    @Test
    void 셔츠는_원본_이름을_품목으로_유지한다() {
        assertThat(classifier.classify(UniformSources.ORDER, null, " 셔츠(강사)  "))
                .isEqualTo(new Classification("셔츠(강사)", UniformItemGroup.SHIRT));
    }

    @Test
    void 규칙에_없으면_미분류() {
        assertThat(classifier.classify(UniformSources.ORDER, null, "모자").group()).isEqualTo(UniformItemGroup.UNKNOWN);
        assertThat(classifier.classify(UniformSources.ORDER, null, null).group()).isEqualTo(UniformItemGroup.UNKNOWN);
    }
}
