package kr.fitorder.module.uniform.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import kr.fitorder.common.BusinessException;
import org.junit.jupiter.api.Test;

/** 공공데이터포털 명세(swagger)의 getServiceList 응답 형식: response/body/collection/dataEL. */
class GreenProductSourceTest {

    private static final String XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <response>
              <header><resultCode>00</resultCode><resultMsg>NORMAL SERVICE.</resultMsg></header>
              <body>
                <collection>
                  <dataEL>
                    <rnum>1</rnum><prodProd>61334</prodProd><prodPrnm>근무복(하계 바지)</prodPrnm>
                    <ecosClnm>의류</ecosClnm><prodMdel>DA-0202</prodMdel><prodRsid>12345</prodRsid>
                    <prodRsdt>20240101</prodRsdt><prodRedt>20271231</prodRedt><prodRsstnm>인증</prodRsstnm>
                    <vendVcnm>다원</vendVcnm><prodG2sn>23578963</prodG2sn>
                  </dataEL>
                  <dataEL>
                    <prodProd>61335</prodProd><prodPrnm>근무복</prodPrnm>
                    <prodRedt>2023-12-31</prodRedt><prodRsstnm>만료</prodRsstnm><vendVcnm>한일</vendVcnm>
                  </dataEL>
                </collection>
                <numOfRows>100</numOfRows><pageNo>1</pageNo><totalCount>2</totalCount>
              </body>
            </response>
            """;

    @Test
    void 명세_형식의_XML을_읽는다() {
        List<GreenProduct> products = GreenProductSource.parse(
                GreenProductSource.xml(XML.strip().getBytes(StandardCharsets.UTF_8)));

        assertThat(products).hasSize(2);
        GreenProduct first = products.getFirst();
        assertThat(first.productId()).isEqualTo("61334");
        assertThat(first.certTo()).isEqualTo(LocalDate.of(2027, 12, 31));
        assertThat(first.g2bId()).isEqualTo("23578963");
        assertThat(first.valid(LocalDate.of(2026, 11, 28))).isTrue();
        assertThat(products.get(1).valid(LocalDate.of(2026, 11, 28))).isFalse();   // 기간 지남·만료
    }

    @Test
    void 날짜는_여러_형식을_읽는다() {
        assertThat(GreenProductSource.date("20240101")).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(GreenProductSource.date("2024.01.01")).isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(GreenProductSource.date("")).isNull();
    }

    @Test
    void XML이_아니면_알려준다() {
        assertThatThrownBy(() -> GreenProductSource.xml("not xml".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class);
    }
}
