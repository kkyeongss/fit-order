package kr.fitorder.module.uniform.cleansing;

import java.util.List;
import kr.fitorder.common.QualityChecker;
import kr.fitorder.common.QualityFlag;
import kr.fitorder.module.uniform.domain.UniformOrder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** 수량·주문일이 빈 행에 플래그를 단다. 집계에서는 수량 결측 행을 뺀다. */
@Component
@Order(2)
public class MissingValueChecker implements QualityChecker<UniformOrder> {

    @Override
    public void inspect(List<UniformOrder> rows) {
        for (UniformOrder row : rows) {
            if (row.getQuantity() == null && !row.hasFlag(QualityFlag.INVALID_QUANTITY)) {
                row.addFlag(QualityFlag.MISSING_QUANTITY);
            }
            if (row.getOrderDate() == null && !row.hasFlag(QualityFlag.INVALID_DATE)) {
                row.addFlag(QualityFlag.MISSING_DATE);
            }
        }
    }
}
