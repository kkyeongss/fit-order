package kr.fitorder.module.uniform.cleansing;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import kr.fitorder.common.QualityChecker;
import kr.fitorder.module.uniform.domain.UniformOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 같은 파일 안에서 행해시가 같은 두 번째 행부터 중복으로 표시한다. 첫 행은 정상. */
@Component
@Order(1)
public class DuplicateChecker implements QualityChecker<UniformOrder> {

    @Override
    public void inspect(List<UniformOrder> rows) {
        Set<String> seen = new HashSet<>();
        for (UniformOrder row : rows) {
            if (!seen.add(row.getRowHash())) {
                row.markDuplicate();
            }
        }
    }
}
