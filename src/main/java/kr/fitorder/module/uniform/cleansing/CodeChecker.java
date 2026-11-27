package kr.fitorder.module.uniform.cleansing;

import java.util.List;
import kr.fitorder.common.QualityChecker;
import kr.fitorder.common.QualityFlag;
import kr.fitorder.module.uniform.UniformProperties;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.domain.UniformOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 분류되지 않은 품목, 뜻을 모르는 사이즈 코드(설정의 unknown-sizes)에 플래그를 단다. */
@Component
@Order(3)
public class CodeChecker implements QualityChecker<UniformOrder> {

    private final UniformProperties properties;

    public CodeChecker(UniformProperties properties) {
        this.properties = properties;
    }

    @Override
    public void inspect(List<UniformOrder> rows) {
        for (UniformOrder row : rows) {
            if (row.getItemGroup() == UniformItemGroup.UNKNOWN) {
                row.addFlag(QualityFlag.UNKNOWN_ITEM);
            }
            if (row.getRawSize() != null && properties.unknownSizesOf(row.getSource()).contains(row.getRawSize())) {
                row.addFlag(QualityFlag.UNKNOWN_SIZE);
            }
        }
    }
}
