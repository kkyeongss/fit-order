package kr.fitorder.module.uniform.price;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.ingestion.CsvUploadIngestion;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.price.MarketPriceProperties.Rule;
import org.junit.jupiter.api.Test;

class MarketPriceServiceTest {

    /** application.yml과 같은 규칙 */
    static MarketPriceProperties properties() {
        return new MarketPriceProperties("https://example", List.of("getMASCntrctPrdctInfoList"), "근무복", 12, 5,
                List.of("상/하", "상하의"), List.of(
                new Rule(UniformItemGroup.PADDING_VEST, List.of("패딩조끼", "패딩 조끼")),
                new Rule(UniformItemGroup.VEST, List.of("조끼")),
                new Rule(UniformItemGroup.JUMPER, List.of("점퍼", "잠바", "파카", "방한복")),
                new Rule(UniformItemGroup.JACKET, List.of("재킷", "자켓")),
                new Rule(UniformItemGroup.PANTS, List.of("하의", "바지", "슬랙스")),
                new Rule(UniformItemGroup.SHIRT, List.of("셔츠", "하계", "혹서기")),
                new Rule(UniformItemGroup.JACKET, List.of("동계", "춘추", "추동"))));
    }

    private final MarketPriceService service = new MarketPriceService(null, properties(), null, null, null);

    @Test
    void 규격명_설명으로_품목군을_정한다() {
        // 실제 쇼핑몰 규격명 형식: 품명, 업체, 모델, 설명
        assertThat(service.classify("근무복, 다원, DA-0202, 소방공무원하계용(하의/남성용)")).hasValue(UniformItemGroup.PANTS);
        assertThat(service.classify("근무복, 한일, HI-1, 소방공무원하계용(상의/여성용)")).hasValue(UniformItemGroup.SHIRT);
        assertThat(service.classify("근무복, 한일, HI-2, 경찰혹서기근무복(교통경찰)")).hasValue(UniformItemGroup.SHIRT);
        assertThat(service.classify("근무복, 제이드, J-2, 교도관동계용(상의/남성용)")).hasValue(UniformItemGroup.JACKET);
        assertThat(service.classify("근무복, 부창, B-31, 소방 이너 티셔츠(반팔)")).hasValue(UniformItemGroup.SHIRT);
        // 상·하의 한 벌 묶음은 뺀다
        assertThat(service.classify("근무복, 제너럴나인, P22, 사회복무요원추동용(상/하의/남성용)")).isEmpty();
        // 업체명에 든 말에는 걸리지 않는다
        assertThat(service.classify("근무복, 바지사랑, X-1, 여성용")).isEmpty();
    }

    @Test
    void 분위수는_선형_보간한다() {
        List<BigDecimal> prices = List.of(new BigDecimal("10000"), new BigDecimal("20000"), new BigDecimal("30000"),
                new BigDecimal("40000"));

        assertThat(MarketPriceService.quantile(prices, 0.5)).isEqualByComparingTo("25000");
        assertThat(MarketPriceService.quantile(prices, 0.25)).isEqualByComparingTo("17500");
    }

    @Test
    void API_응답_한_행을_상품으로_바꾼다() {
        MarketItem item = ProcurementMallSource.toItem(Map.of("shopngCntrctNo", "R25TA0066476400",
                "shopngCntrctSno", "1", "prdctSpecNm", "근무복, 다원, DA-0202, 소방공무원하계용(하의/남성용)",
                "cntrctPrceAmt", "49000", "prdctUnit", "매", "dutyPurchsObjCertNm", "환경표지제품"));

        assertThat(item.key()).isEqualTo("R25TA0066476400-1");
        assertThat(item.price()).isEqualByComparingTo("49000");
        assertThat(item.eco()).isTrue();
    }

    @Test
    void 망분리용_CSV는_UTF8과_CP949를_모두_읽는다() {
        String csv = """
                물품규격명,계약가격금액,물품단위,의무구매대상인증명
                "근무복, 다원, DA-0202, 소방공무원하계용(하의/남성용)","49,000",매,환경표지제품
                "근무복, 한일, HI-1906, 소방공무원하계용(하의/남성용)",49000,착,해당 없음
                """;
        for (byte[] bytes : List.of(csv.getBytes(StandardCharsets.UTF_8), csv.getBytes(CsvUploadIngestion.charsetOf("CP949")))) {
            List<MarketItem> items = MarketItemCsvReader.read(bytes);
            assertThat(items).hasSize(2);
            assertThat(items.getFirst().price()).isEqualByComparingTo("49000");
            assertThat(items.getFirst().eco()).isTrue();
            assertThat(items.get(1).eco()).isFalse();
        }
        assertThatThrownBy(() -> MarketItemCsvReader.read("규격,가격\na,1\n".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("물품규격명");
    }
}
