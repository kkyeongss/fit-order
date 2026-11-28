package kr.fitorder.module.uniform.analysis;

import java.time.LocalDate;
import kr.fitorder.module.uniform.domain.UniformItemGroup;

/**
 * 집계용 주문 한 행. 수량·주문일이 있는 행만 들어온다.
 *
 * @param size 화면에 보이는 사이즈 이름(매핑이 있으면 표준 사이즈, 없으면 '코드 (출처)')
 */
public record OrderLine(String source, LocalDate date, UniformItemGroup group, String rawSize, String size,
                        int quantity, boolean duplicate) {

    public int year() {
        return date.getYear();
    }
}
