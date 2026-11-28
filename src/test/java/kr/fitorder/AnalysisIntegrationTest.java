package kr.fitorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.Basis;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.ingestion.CsvUploadIngestion;
import kr.fitorder.ingestion.DataSourceService;
import kr.fitorder.ingestion.DataSourceService.Update;
import kr.fitorder.ingestion.DataSourceSynchronizer;
import kr.fitorder.ingestion.LoadService;
import kr.fitorder.module.uniform.analysis.Backtester;
import kr.fitorder.module.uniform.analysis.Backtester.Average;
import kr.fitorder.module.uniform.analysis.Backtester.GroupResult;
import kr.fitorder.module.uniform.analysis.Backtester.Method;
import kr.fitorder.module.uniform.analysis.RecommendationService;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import kr.fitorder.module.uniform.analysis.RecommendationService.Request;
import kr.fitorder.module.uniform.analysis.UniformDatasetLoader;
import kr.fitorder.module.uniform.analysis.UnitPriceService;
import kr.fitorder.module.uniform.cleansing.SizeMappingService;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.analysis.UniformDataset.PriceSource;
import kr.fitorder.module.uniform.price.GreenProductService;
import kr.fitorder.module.uniform.price.MarketPriceService;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 근무복 CSV로 지표·추천·백테스트가 기획서 수치를 재현하는지, 데이터가 바뀌면 지표가 다시 계산되는지 고정한다.
 * 정원은 실제 Open API 값(2023~2026 마지막 기준일자 합계)을 작은 CSV로 넣는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AnalysisIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    private static final Charset CP949 = CsvUploadIngestion.charsetOf("CP949");

    @Autowired LoadService loadService;
    @Autowired DataSourceService dataSources;
    @Autowired DataSourceSynchronizer synchronizer;
    @Autowired UniformDatasetLoader loader;
    @Autowired Backtester backtester;
    @Autowired RecommendationService recommendations;
    @Autowired UnitPriceService prices;
    @Autowired SizeMappingService sizeMappings;
    @Autowired MarketPriceService market;
    @Autowired GreenProductService green;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void load() throws IOException {
        jdbc.execute("truncate uniform_order, headcount, load_history, audit_log, size_mapping, unit_price, "
                + "esg_metric_value, market_price, green_product restart identity cascade");
        jdbc.execute("delete from data_source");
        synchronizer.run(null);
        upload("uniform-order", "data/한국체육산업개발주식회사_근무복 주문현황_20260827.csv");
        upload("uniform-order-info", "data/한국체육산업개발주식회사_근무복 주문현황정보_20260827.csv");
        dataSources.update("headcount", new Update(LoadMethod.UPLOAD, null, null, "CP949",
                Map.of("base-date", "기준일자", "department", "부서명")), "test");
        byte[] headcount = """
                기준일자,부서명,일반직3급
                2023-07-01,전체,1374
                2024-11-27,전체,1376
                2025-01-09,전체,1360
                2026-01-01,전체,1344
                """.getBytes(CP949);
        loadService.upload("headcount", null, "headcount.csv", headcount, "test");
    }

    private void upload(String source, String file) throws IOException {
        Path p = Path.of(file);
        loadService.upload(source, null, p.getFileName().toString(), Files.readAllBytes(p), "test");
    }

    private BigDecimal metric(String code, String period, String dimension, Basis basis) {
        return jdbc.queryForObject("""
                select value from esg_metric_value
                where metric_code = ? and period = ? and dimension = ? and basis = ?""",
                BigDecimal.class, "uniform." + code, period, dimension, basis.name());
    }

    @Test
    void 적재하면_지표가_두_기준으로_저장되고_기획서_수치와_같다() {
        assertThat(jdbc.queryForObject("select count(distinct basis) from esg_metric_value", Integer.class))
                .isEqualTo(2);
        // 2020~22년 비율로 2023~25년을 주문하면 셔츠 16%, 점퍼 18% 미스매치
        assertThat(metric("size-mismatch-rate", "2023-2025", "셔츠", Basis.DEDUP).doubleValue())
                .isCloseTo(16.12, within(0.01));
        assertThat(metric("size-mismatch-rate", "2023-2025", "점퍼", Basis.DEDUP).doubleValue())
                .isCloseTo(17.80, within(0.01));
        // 2020~2025 연평균 약 29일 주문
        double days = jdbc.queryForObject("""
                select avg(value) from esg_metric_value
                where metric_code = 'uniform.order-days' and basis = 'DEDUP' and period between '2020' and '2025'""",
                Double.class);
        assertThat(days).isCloseTo(28.7, within(0.1));
        assertThat(metric("order-quantity", "2025", "", Basis.DEDUP)).isEqualByComparingTo("1323");
        assertThat(metric("per-capita", "2025", "", Basis.DEDUP).doubleValue()).isCloseTo(1323 / 1360.0, within(1e-4));
        // 불완전 연도는 저장하지 않는다
        assertThat(jdbc.queryForObject("select count(*) from esg_metric_value where period in ('2017', '2026')",
                Integer.class)).isZero();
    }

    @Test
    void 단가를_바꾸면_절감액이_다시_계산된다() {
        BigDecimal before = metric("saved-amount", "2023-2025", "셔츠", Basis.DEDUP);
        BigDecimal savedQty = metric("saved-quantity", "2023-2025", "셔츠", Basis.DEDUP);
        // 단가 미입력: 추정 단가 53,000원 (저장된 절감 수량은 소수 4자리로 반올림돼 있다)
        assertThat(before.doubleValue()).isCloseTo(savedQty.doubleValue() * 53000, within(10.0));

        prices.update(List.of(new UnitPriceService.Change(UniformItemGroup.SHIRT, new BigDecimal("30000"))),
                "admin");

        assertThat(metric("saved-amount", "2023-2025", "셔츠", Basis.DEDUP).doubleValue())
                .isCloseTo(savedQty.doubleValue() * 30000, within(1.0));
        assertThat(jdbc.queryForObject("select count(*) from audit_log where action = 'PRICE_UPDATE'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void 시장가를_올리면_표본이_충분한_품목만_시장가를_쓰고_입력_단가가_우선한다() {
        StringBuilder csv = new StringBuilder("물품규격명,계약가격금액,물품단위,의무구매대상인증명\n");
        int[] pants = {36000, 38000, 40000, 42000, 44000, 60000};
        for (int i = 0; i < pants.length; i++) {
            csv.append("\"근무복, 업체").append(i).append(", M").append(i).append(", 하계용(하의/남성용)\",")
                    .append(pants[i]).append(",매,").append(i == 5 ? "환경표지제품" : "해당 없음").append("\n");
        }
        csv.append("\"근무복, 업체, V1, 근무용 조끼\",30000,매,해당 없음\n");   // 조끼 1건: 표본 부족
        BigDecimal before = metric("saved-amount", "2023-2025", "바지", Basis.DEDUP);

        var result = market.upload(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin");

        assertThat(result.itemCount()).isEqualTo(7);
        var byGroup = prices.list().stream().collect(java.util.stream.Collectors.toMap(p -> p.group(), p -> p));
        assertThat(byGroup.get(UniformItemGroup.PANTS).source()).isEqualTo(PriceSource.MARKET);
        assertThat(byGroup.get(UniformItemGroup.PANTS).price()).isEqualByComparingTo("41000");   // 6건 중앙값
        assertThat(byGroup.get(UniformItemGroup.VEST).source()).isEqualTo(PriceSource.ESTIMATE);
        // 절감액이 시장가로 다시 계산된다 (53,000원 → 41,000원)
        BigDecimal after = metric("saved-amount", "2023-2025", "바지", Basis.DEDUP);
        assertThat(after.doubleValue()).isCloseTo(before.doubleValue() * 41000 / 53000, within(1.0));
        assertThat(market.list().stream().filter(m -> m.group() == UniformItemGroup.PANTS).findFirst().orElseThrow()
                .ecoCount()).isEqualTo(1);

        // 직접 입력한 단가가 시장가보다 우선한다
        prices.update(List.of(new UnitPriceService.Change(UniformItemGroup.PANTS, new BigDecimal("45000"))), "admin");
        assertThat(prices.list().stream().filter(p -> p.group() == UniformItemGroup.PANTS).findFirst().orElseThrow()
                .source()).isEqualTo(PriceSource.MANUAL);
    }

    @Test
    void 녹색제품을_올리면_발주_추천에_품목군별_유효_인증_제품이_나온다() {
        String csv = """
                상품ID,상품명,인증종료일,인증상태,인증기업명
                1,근무복(하계 바지),20291231,인증,다원
                2,근무 바지,20291231,인증,한일
                3,근무복 셔츠,20201231,만료,다원
                4,근무복,20291231,인증,부창
                """;
        var result = green.upload(csv.getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin");

        assertThat(result.productCount()).isEqualTo(4);
        assertThat(result.validCount()).isEqualTo(3);
        Recommendation rec = recommendations.recommend(new Request(null, null, null, null));
        var pants = rec.greenProducts().stream().filter(g -> g.group() == UniformItemGroup.PANTS).findFirst()
                .orElseThrow();
        assertThat(pants.validCount()).isEqualTo(2);
        assertThat(pants.companyCount()).isEqualTo(2);
        var shirt = rec.greenProducts().stream().filter(g -> g.group() == UniformItemGroup.SHIRT).findFirst()
                .orElseThrow();
        assertThat(shirt.validCount()).isZero();   // 인증 만료
        assertThat(rec.greenProducts()).anyMatch(g -> g.group() == null && g.validCount() == 1);
    }

    @Test
    void 사이즈_매핑을_바꾸면_미스매치율이_다시_계산된다() {
        BigDecimal before = metric("size-mismatch-rate", "2023-2025", "점퍼", Basis.DEDUP);
        // 주문현황의 점퍼 사이즈 전부를 주문현황정보 50으로 합친다 → 두 체계가 섞이지 않으니 값이 바뀐다
        List<SizeMappingService.Change> changes = sizeMappings.list().stream()
                .filter(r -> r.source().equals("uniform-order"))
                .map(r -> new SizeMappingService.Change(r.source(), r.rawSize(), "50"))
                .toList();
        sizeMappings.update(changes, "admin");

        assertThat(metric("size-mismatch-rate", "2023-2025", "점퍼", Basis.DEDUP)).isNotEqualByComparingTo(before);
    }

    @Test
    void 백테스트는_기준선_오차를_재현하고_추천이_사이즈_오차를_줄인다() {
        Backtester.Result result = backtester.run(loader.load(Basis.DEDUP));

        // 기준선 오차: 합계 18~22%, 바지 68%
        assertThat(average(result.total(), Method.PREVIOUS_YEAR).totalError()).isCloseTo(0.176, within(0.001));
        assertThat(average(result.total(), Method.TWO_YEAR_AVERAGE).totalError()).isCloseTo(0.218, within(0.001));
        assertThat(average(group(result, UniformItemGroup.PANTS), Method.TWO_YEAR_AVERAGE).totalError())
                .isCloseTo(0.684, within(0.001));
        // 추천: 격년 규칙으로 재킷 오차가 크게 줄고, 전체 사이즈 오차가 두 기준선보다 작다
        assertThat(average(group(result, UniformItemGroup.JACKET), Method.RECOMMENDATION).totalError())
                .isLessThan(average(group(result, UniformItemGroup.JACKET), Method.TWO_YEAR_AVERAGE).totalError() / 3);
        double recSize = average(result.total(), Method.RECOMMENDATION).sizeError();
        assertThat(recSize).isLessThan(average(result.total(), Method.PREVIOUS_YEAR).sizeError());
        assertThat(recSize).isLessThan(average(result.total(), Method.TWO_YEAR_AVERAGE).sizeError());
    }

    @Test
    void 추천은_다음_해_발주량을_사이즈별로_내고_합이_맞는다() {
        Recommendation rec = recommendations.recommend(new Request(null, null, null, null));

        assertThat(rec.year()).isEqualTo(2027);
        assertThat(rec.headcount()).isEqualTo(1344);
        var shirt = rec.items().stream().filter(i -> i.group() == UniformItemGroup.SHIRT).findFirst().orElseThrow();
        // 2024·2025 평균 493.5 × (1,344 ÷ 1,360) = 487.7 → 488, 안전재고 5% = 24
        assertThat(shirt.total().years()).containsExactly(2024, 2025);
        assertThat(shirt.baseQuantity()).isEqualTo(488);
        assertThat(shirt.safetyQuantity()).isEqualTo(24);
        assertThat(shirt.sizes().stream().mapToInt(s -> s.quantity()).sum()).isEqualTo(shirt.quantity());
        var jacket = rec.items().stream().filter(i -> i.group() == UniformItemGroup.JACKET).findFirst().orElseThrow();
        assertThat(jacket.total().years()).containsExactly(2025);   // 격년: 2027 ← 2025
        assertThat(rec.orderMonths()).extracting(m -> m.month()).containsExactly(4, 8);
        assertThat(rec.totals().priceEstimated()).isTrue();
    }

    @Test
    void API는_basis를_소문자로_받고_권한을_나눈다() throws Exception {
        mvc.perform(get("/api/uniform/summary").param("basis", "raw").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basis").value("RAW"))
                .andExpect(jsonPath("$.annualAverage.fromYear").value(2020));
        mvc.perform(get("/api/uniform/summary").param("basis", "wrong").with(user("u").roles("USER")))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/esg/items/uniform/metrics").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'uniform.size-mismatch-rate')].values.length()").value(7));
        mvc.perform(get("/api/uniform/sizes").param("group", "SHIRT").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comparison.recent.from").value(2023));
        mvc.perform(post("/api/uniform/recommendation").with(user("u").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"year\": 2027, \"groups\": [\"JUMPER\"], \"headcount\": 1400, \"basis\": \"dedup\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].safetyRate").value(0.05));

        // ESG 항목 변경과 관리 API는 ADMIN만
        mvc.perform(put("/api/esg/items/uniform").with(user("u").roles("USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quality").with(user("u").roles("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/quality").with(user("a").roles("ADMIN", "USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.source == 'uniform-order-info')].flags[?(@.flag == 'MISSING_QUANTITY')].rows")
                        .value(41));
        mvc.perform(put("/api/esg/items/uniform").with(user("a").roles("ADMIN", "USER")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true, \"loadMethod\": \"UPLOAD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metrics.length()").value(8));
    }

    @Test
    void 엑셀_발주표를_내려받는다() throws Exception {
        byte[] body = mvc.perform(get("/api/uniform/recommendation/export")
                        .param("groups", "SHIRT", "JACKET").with(user("u").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();

        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("2027년 발주표");
            boolean hasTotal = false;
            for (var row : sheet) {
                if (row.getCell(0) != null && "합계".equals(row.getCell(0).getStringCellValue())) {
                    hasTotal = true;
                }
            }
            assertThat(hasTotal).isTrue();
        }
    }

    private static Average average(GroupResult group, Method method) {
        return group.averages().stream().filter(a -> a.method() == method).findFirst().orElseThrow();
    }

    private static GroupResult group(Backtester.Result result, UniformItemGroup group) {
        return result.groups().stream().filter(g -> g.group() == group).findFirst().orElseThrow();
    }
}
