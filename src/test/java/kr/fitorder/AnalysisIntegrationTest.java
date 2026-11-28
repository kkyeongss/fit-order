package kr.fitorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import kr.fitorder.common.esg.EsgItemService;
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
@SpringBootTest(properties = "fitorder.warmup=false")
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
    @Autowired kr.fitorder.module.uniform.analysis.UniformReportService reports;
    @Autowired UnitPriceService prices;
    @Autowired SizeMappingService sizeMappings;
    @Autowired MarketPriceService market;
    @Autowired GreenProductService green;
    @Autowired kr.fitorder.module.uniform.analysis.StockService stock;
    @Autowired kr.fitorder.module.uniform.analysis.PlanService plans;
    @Autowired kr.fitorder.module.uniform.analysis.DuplicateExportService duplicates;
    @Autowired kr.fitorder.module.uniform.analysis.CarbonFactorService carbonFactors;
    @Autowired kr.fitorder.admin.AuditQueryService auditQuery;
    @Autowired kr.fitorder.config.UserAdminService userAdmin;
    @Autowired kr.fitorder.config.DemoResetService demoReset;
    @Autowired kr.fitorder.common.esg.EsgItemService esgItems;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;

    @BeforeEach
    void load() throws IOException {
        jdbc.execute("truncate uniform_order, headcount, load_history, audit_log, size_mapping, unit_price, "
                + "esg_metric_value, market_price, green_product, stock, order_plan restart identity cascade");
        // 테스트가 만든 계정은 지운다(시연 계정 3개는 시작할 때 등록된 것을 그대로 쓴다)
        jdbc.execute("delete from users where username not in ('user', 'admin', 'manager')");
        jdbc.execute("delete from data_source");
        jdbc.execute("update esg_item set enabled = true, load_method = 'UPLOAD'");
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
    void 계산_결과는_저장해_두되_데이터가_바뀌면_바로_새로_계산한다() {
        Request request = new Request(null, null, null, null);
        Recommendation first = recommendations.recommend(request);
        // 같은 입력(뜻이 같은 입력 포함)은 다시 계산하지 않는다
        assertThat(recommendations.recommend(request)).isSameAs(first);
        assertThat(recommendations.recommend(new Request(null, List.of(), null, Basis.DEDUP))).isSameAs(first);
        var summary = reports.summary(Basis.DEDUP);
        assertThat(reports.summary(Basis.DEDUP)).isSameAs(summary);

        // 단가를 바꾸면 추천 금액과 절감액이 바로 달라진다(낡은 결과가 남지 않는다)
        prices.update(List.of(new UnitPriceService.Change(UniformItemGroup.SHIRT, new BigDecimal("20000"))), "admin");
        Recommendation second = recommendations.recommend(request);
        assertThat(second).isNotSameAs(first);
        var shirtBefore = first.items().stream().filter(i -> i.group() == UniformItemGroup.SHIRT).findFirst().orElseThrow();
        var shirtAfter = second.items().stream().filter(i -> i.group() == UniformItemGroup.SHIRT).findFirst().orElseThrow();
        assertThat(shirtAfter.unitPrice()).isEqualByComparingTo("20000");
        assertThat(shirtAfter.unitPrice()).isNotEqualByComparingTo(shirtBefore.unitPrice());
        assertThat(shirtAfter.amount()).isEqualByComparingTo(new BigDecimal("20000").multiply(BigDecimal.valueOf(shirtAfter.quantity())));

        // 녹색제품을 올려도 추천 화면의 녹색구매 비교가 바로 바뀐다
        assertThat(second.greenProducts().stream().mapToInt(g -> g.validCount()).sum()).isZero();
        green.upload("상품명,인증종료일,인증기업명\n근무복 바지,20291231,다원\n".getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin");
        assertThat(recommendations.recommend(request).greenProducts().stream().mapToInt(g -> g.validCount()).sum()).isPositive();
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

    @Test
    void 화면용_로그인은_JSON으로_답하고_미로그인_API는_401이다() throws Exception {
        mvc.perform(get("/api/uniform/summary")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "user").param("password", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").exists());
        var login = mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "admin").param("password", "admin1234"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"))
                .andExpect(jsonPath("$.admin").value(true))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"));
        mvc.perform(post("/api/auth/logout").session(session).with(csrf())).andExpect(status().isNoContent());
        // 로그인도 CSRF 토큰이 있어야 한다
        mvc.perform(post("/api/auth/login").param("username", "admin").param("password", "admin1234"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ESG_항목의_적재_방식을_바꾸면_원천이_한꺼번에_바뀐다() {
        // 망분리 전환: 모든 원천을 업로드로
        var item = esgItems.update("uniform", new EsgItemService.Change(null, LoadMethod.UPLOAD), "admin");
        assertThat(item.sources()).allMatch(s -> s.method() == LoadMethod.UPLOAD);
        // 정원현황은 setUp에서 주소 없이 업로드로 바꿨으므로, 주소를 넣고 API로 되돌린다
        dataSources.update("headcount", new Update(LoadMethod.UPLOAD,
                "https://api.odcloud.kr/api/15144222/v1/x", null, "CP949",
                Map.of("base-date", "기준일자", "department", "부서명")), "admin");

        item = esgItems.update("uniform", new EsgItemService.Change(null, LoadMethod.API), "admin");

        // API 주소가 있는 정원현황만 API가 되고, 파일로만 주는 근무복 CSV는 업로드로 남는다
        assertThat(item.sources()).extracting(EsgItemService.SourceInfo::code, EsgItemService.SourceInfo::method)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("headcount", LoadMethod.API),
                        org.assertj.core.groups.Tuple.tuple("uniform-order", LoadMethod.UPLOAD),
                        org.assertj.core.groups.Tuple.tuple("uniform-order-info", LoadMethod.UPLOAD));
    }

    // ---- 점검 반영 기능 ---------------------------------------------------------------

    private static RecommendationService.Request req(Integer year) {
        return new RecommendationService.Request(year, null, null, null);
    }

    @Test
    void 추천_연도는_범위_안만_허용하고_이상한_인원은_경고한다() {
        assertThatThrownBy(() -> recommendations.recommend(req(2019))).hasMessageContaining("추천할 수 없는 연도");
        assertThatThrownBy(() -> recommendations.recommend(req(2100))).hasMessageContaining("2026~2028");
        assertThat(recommendations.recommend(req(2026)).year()).isEqualTo(2026);

        var rec = recommendations.recommend(new RecommendationService.Request(null, null, 5000, null));
        assertThat(rec.warnings()).anyMatch(w -> w.contains("5000명") && w.contains("확인"));
        assertThat(rec.yearRange().from()).isEqualTo(2026);
    }

    @Test
    void 재고를_입력하면_발주_수량이_줄고_엑셀의_수식_합계도_맞는다() throws Exception {
        var before = recommendations.recommend(req(null));
        var shirt = before.items().stream().filter(i -> i.group() == UniformItemGroup.SHIRT).findFirst().orElseThrow();
        var size100 = shirt.sizes().stream().filter(z -> "100".equals(z.rawSize())).findFirst().orElseThrow();
        assertThat(before.totals().orderQuantity()).isEqualTo(before.totals().quantity());   // 재고 없음: 필요 = 발주

        stock.update(List.of(new kr.fitorder.module.uniform.analysis.StockService.Change(UniformItemGroup.SHIRT,
                size100.source(), "100", 50)), "user");
        var after = recommendations.recommend(req(null));

        var shirtAfter = after.items().stream().filter(i -> i.group() == UniformItemGroup.SHIRT).findFirst().orElseThrow();
        assertThat(after.totals().quantity()).isEqualTo(before.totals().quantity());   // 필요 수량은 그대로
        assertThat(after.totals().orderQuantity()).isEqualTo(before.totals().quantity() - 50);
        assertThat(after.totals().stockTotal()).isEqualTo(50);
        assertThat(shirtAfter.orderAmount()).isEqualByComparingTo(shirtAfter.unitPrice().multiply(BigDecimal.valueOf(shirtAfter.orderQuantity())));
        // 4월·8월 회차 수량의 합이 발주 수량과 같다
        assertThat(after.totals().rounds()).hasSize(2);
        assertThat(after.totals().rounds().stream().mapToInt(r -> r.quantity()).sum()).isEqualTo(after.totals().orderQuantity());

        // 엑셀: 수식으로 계산되는 합계가 서버 계산과 같다
        byte[] body = mvc.perform(get("/api/uniform/recommendation/export").with(user("u").roles("USER")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(body))) {
            var sheet = wb.getSheetAt(0);
            int formulas = 0;
            for (var row : sheet) {
                for (var cell : row) {
                    if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
                        formulas++;
                    }
                }
            }
            assertThat(formulas).isGreaterThan(50);
            wb.getCreationHelper().createFormulaEvaluator().evaluateAll();
            var total = java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                    .filter(r -> r.getCell(0) != null && "합계".equals(r.getCell(0).getStringCellValue())).findFirst().orElseThrow();
            assertThat((int) total.getCell(3).getNumericCellValue()).isEqualTo(after.totals().quantity());
            assertThat((int) total.getCell(4).getNumericCellValue()).isEqualTo(50);
            assertThat((int) total.getCell(5).getNumericCellValue()).isEqualTo(after.totals().orderQuantity());
            // 사이즈 이름에서 같은 출처 꼬리표를 뗐다(셔츠는 한 출처)
            boolean strippedShirt = java.util.stream.StreamSupport.stream(sheet.spliterator(), false)
                    .anyMatch(r -> r.getCell(0) != null && "셔츠".equals(r.getCell(0).getStringCellValue())
                            && r.getCell(1) != null && "100".equals(r.getCell(1).getStringCellValue()));
            assertThat(strippedShirt).isTrue();
            // 결재란과 입력 칸, 인쇄 설정
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("담당");
            assertThat(sheet.getPrintSetup().getLandscape()).isTrue();
        }
    }

    @Test
    void 총량을_직접_지정하면_그_값으로_계산하고_다른_기준과의_차이를_함께_보여준다() {
        var rec = recommendations.recommend(new RecommendationService.Request(null, List.of(UniformItemGroup.SHIRT), null,
                null, java.util.Map.of(UniformItemGroup.SHIRT, 100)));

        var shirt = rec.items().getFirst();
        assertThat(shirt.baseQuantity()).isEqualTo(99);   // 100 × (1,344 ÷ 1,360)
        assertThat(shirt.total().rule()).contains("직접 지정");

        var normal = recommendations.recommend(req(null));
        assertThat(normal.otherBasis().basis()).isEqualTo(Basis.RAW);
        assertThat(normal.otherBasis().quantity()).isGreaterThan(normal.totals().quantity());   // 중복 행을 세면 더 많다
        assertThat(normal.totals().range().low()).isLessThan(normal.totals().quantity());
        assertThat(normal.totals().range().high()).isGreaterThan(normal.totals().quantity());
        assertThat(normal.lastOrderDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 27));
    }

    @Test
    void 발주안을_저장하면_그해_실제_주문과_비교하고_지우는_권한을_나눈다() {
        var saved = plans.save(new kr.fitorder.module.uniform.analysis.PlanService.SaveRequest(2026, null, null, Basis.DEDUP,
                null, "시험 발주안"), "kim");
        assertThat(saved.planYear()).isEqualTo(2026);
        assertThat(plans.list()).extracting(p -> p.id()).contains(saved.id());

        // 2026년은 8/27까지만 있어 '중간 비교'. 실제 주문(중복 제거) 403벌
        var compare = plans.compare(saved.id());
        assertThat(compare.hasActual()).isTrue();
        assertThat(compare.yearComplete()).isFalse();
        assertThat(compare.message()).contains("중간 비교");
        assertThat(compare.actualQuantity()).isEqualTo(403);
        assertThat(compare.plannedQuantity()).isEqualTo(recommendations.recommend(req(2026)).totals().quantity());
        var shirt = compare.items().stream().filter(i -> i.group().equals("SHIRT")).findFirst().orElseThrow();
        assertThat(shirt.actual()).isEqualTo(212);
        assertThat(shirt.difference()).isEqualTo(shirt.planned() - 212);
        assertThat(shirt.sizes()).isNotEmpty();

        // 데이터가 아직 없는 해는 그렇게 알려준다
        var future = plans.save(new kr.fitorder.module.uniform.analysis.PlanService.SaveRequest(2027, null, null, Basis.DEDUP,
                null, null), "kim");
        assertThat(plans.compare(future.id()).hasActual()).isFalse();
        assertThat(plans.compare(future.id()).message()).contains("아직 없습니다");

        assertThatThrownBy(() -> plans.delete(saved.id(), "other", false)).hasMessageContaining("저장한 사람이나 관리자");
        plans.delete(saved.id(), "kim", false);
        plans.delete(future.id(), "boss", true);
        assertThat(plans.list()).isEmpty();
        assertThat(auditQuery.actions()).contains("PLAN_SAVE", "PLAN_DELETE");
    }

    @Test
    void 절감_근거에_단가_출처와_개선률_범위와_추정_단가_비율이_나온다() {
        var report = reports.savings(Basis.DEDUP);

        assertThat(report.scenarios()).hasSize(3);
        assertThat(report.scenarios().get(0).rate()).isCloseTo(0.25, within(1e-9));
        assertThat(report.scenarios().get(2).rate()).isCloseTo(0.75, within(1e-9));
        assertThat(report.scenarios().get(0).savedAmount().doubleValue()).isCloseTo(report.savedAmount().doubleValue() * 0.5, within(1.0));
        assertThat(report.scenarios().get(2).savedAmount().doubleValue()).isCloseTo(report.savedAmount().doubleValue() * 1.5, within(1.0));
        assertThat(report.groups().stream().map(g -> g.savedAmount()).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo(report.savedAmount());
        assertThat(report.groups()).allMatch(g -> g.carbonSource() != null && !g.carbonSource().equals("-"));
        assertThat(report.estimatedPriceShare()).isCloseTo(1.0, within(1e-9));   // 시장가를 안 올렸으니 전부 추정 단가
        assertThat(report.estimatedGroups()).hasSize(6);

        // 바지 시장가를 올리면 그만큼 추정 단가에 기대는 비율이 줄어든다
        StringBuilder csv = new StringBuilder("물품규격명,계약가격금액\n");
        for (int i = 0; i < 6; i++) {
            csv.append("\"근무복, 업체, M").append(i).append(", 하계용(하의/남성용)\",40000\n");
        }
        market.upload(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8), "admin");
        var after = reports.savings(Basis.DEDUP);
        assertThat(after.estimatedPriceShare()).isLessThan(1.0).isGreaterThan(0.8);
        assertThat(after.groups().stream().filter(g -> g.group() == UniformItemGroup.PANTS).findFirst().orElseThrow().priceSource())
                .isEqualTo(PriceSource.MARKET);
    }

    @Test
    void 요약에_데이터_기준일과_연도별_추정_금액이_들어_있다() {
        var summary = reports.summary(Basis.DEDUP);

        assertThat(summary.dataAsOf().lastOrderDate()).isEqualTo(java.time.LocalDate.of(2026, 8, 27));
        assertThat(summary.dataAsOf().loads()).hasSize(3);
        var y2025 = summary.years().stream().filter(y -> y.year() == 2025).findFirst().orElseThrow();
        // 시장가를 안 올렸으니 모든 품목이 추정 단가 53,000원
        assertThat(y2025.amount()).isEqualByComparingTo(new BigDecimal("53000").multiply(BigDecimal.valueOf(1323)));
    }

    @Test
    void 중복_행_목록을_CSV로_내려받는다() throws Exception {
        byte[] bytes = duplicates.csv();
        String text = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

        assertThat(text).startsWith("\uFEFF출처,파일 줄 번호,같은 행이 처음 나온 줄");
        try (var parser = org.apache.commons.csv.CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get()
                .parse(new java.io.StringReader(text.substring(1)))) {
            var records = parser.getRecords();
            assertThat(records).hasSize(327 + 331);
            assertThat(records.getFirst().get("같은 행이 처음 나온 줄")).isNotBlank();
        }
        mvc.perform(get("/api/uniform/duplicates/export").with(user("m").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("attachment")));
    }

    @Test
    void 탄소계수를_바꾸면_탄소_지표가_다시_계산되고_변경_기록에_남는다() {
        BigDecimal before = metric("carbon-reduction", "2023-2025", "셔츠", Basis.DEDUP);

        carbonFactors.update(List.of(new kr.fitorder.module.uniform.analysis.CarbonFactorService.Change(UniformItemGroup.SHIRT,
                new BigDecimal("20.00"), "국내 탄소성적표지(시험)")), "admin");

        BigDecimal after = metric("carbon-reduction", "2023-2025", "셔츠", Basis.DEDUP);
        assertThat(after.doubleValue()).isCloseTo(before.doubleValue() * 20.0 / 11.98, within(before.doubleValue() * 0.01));
        assertThat(reports.savings(Basis.DEDUP).groups().stream().filter(g -> g.group() == UniformItemGroup.SHIRT).findFirst()
                .orElseThrow().carbonSource()).isEqualTo("국내 탄소성적표지(시험)");
        var rows = auditQuery.recent(50, "CARBON_FACTOR_UPDATE", "admin");
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().detail()).contains("20.00");
        assertThatThrownBy(() -> carbonFactors.update(List.of(new kr.fitorder.module.uniform.analysis.CarbonFactorService.Change(
                UniformItemGroup.SHIRT, BigDecimal.ZERO, "x")), "admin")).hasMessageContaining("0보다 크고");
    }

    @Test
    void 역할에_따라_조회_입력_변경_기록_접근이_나뉜다() throws Exception {
        var manager = user("m").roles("MANAGER");
        var worker = user("w").roles("USER");
        // 부서장: 조회와 변경 기록은 되고 입력은 안 된다
        mvc.perform(get("/api/uniform/summary").with(manager)).andExpect(status().isOk());
        mvc.perform(get("/api/audit").with(manager)).andExpect(status().isOk());
        mvc.perform(put("/api/uniform/stock").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isForbidden());
        mvc.perform(put("/api/uniform/prices").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[]")).andExpect(status().isForbidden());
        mvc.perform(post("/api/uniform/plans").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isForbidden());
        // 추천 계산은 POST지만 저장하지 않는 조회라 부서장도 쓴다
        mvc.perform(post("/api/uniform/recommendation").with(manager).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isOk());
        // 담당자: 입력은 되고 변경 기록·관리자 화면은 안 된다
        mvc.perform(get("/api/audit").with(worker)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(worker)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/users").with(manager)).andExpect(status().isForbidden());
        mvc.perform(put("/api/uniform/prices").with(worker).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"group\":\"JUMPER\",\"price\":45000}]")).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.group == 'JUMPER')].source").value("MANUAL"));
        mvc.perform(delete("/api/uniform/prices/JUMPER").with(worker).with(csrf())).andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.group == 'JUMPER')].source").value("ESTIMATE"));
        mvc.perform(put("/api/uniform/stock").with(worker).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("[{\"group\":\"SHIRT\",\"source\":\"uniform-order\",\"rawSize\":\"100\",\"quantity\":7}]"))
                .andExpect(status().isOk());
        assertThat(auditQuery.actions()).contains("PRICE_UPDATE", "PRICE_CLEAR", "STOCK_UPDATE");
        // 잘못된 요청은 서버 메시지로 답한다
        mvc.perform(get("/api/uniform/recommendation/export").param("year", "2019").with(worker))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("추천할 수 없는 연도")));
        mvc.perform(get("/api/uniform/recommendation/export").param("totals", "SHIRT-500").with(worker))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("총량 지정 형식")));
    }

    @Test
    void 계정을_만들고_역할을_바꾸고_비밀번호를_바꾼다() throws Exception {
        var created = userAdmin.create(new kr.fitorder.config.UserAdminService.Create("kim01", "password1234",
                kr.fitorder.config.UserAdminService.Role.MANAGER), "admin");
        assertThat(created.roleLabel()).isEqualTo("부서장");
        assertThatThrownBy(() -> userAdmin.create(new kr.fitorder.config.UserAdminService.Create("kim01", "password1234",
                kr.fitorder.config.UserAdminService.Role.USER), "admin")).hasMessageContaining("이미 있는");
        assertThatThrownBy(() -> userAdmin.create(new kr.fitorder.config.UserAdminService.Create("lee02", "1234",
                kr.fitorder.config.UserAdminService.Role.USER), "admin")).hasMessageContaining("8자 이상");
        assertThatThrownBy(() -> userAdmin.create(new kr.fitorder.config.UserAdminService.Create("a", "password1234",
                kr.fitorder.config.UserAdminService.Role.USER), "admin")).hasMessageContaining("아이디");

        // 새 계정으로 로그인해 비밀번호를 바꾼다(현재 비밀번호 확인 필요)
        var login = mvc.perform(post("/api/auth/login").with(csrf()).param("username", "kim01").param("password", "password1234"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.manager").value(true)).andExpect(jsonPath("$.canEdit").value(false))
                .andReturn();
        var session = (org.springframework.mock.web.MockHttpSession) login.getRequest().getSession(false);
        mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"current\":\"wrong-password\",\"next\":\"newpassword99\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail").value("현재 비밀번호가 맞지 않습니다."));
        mvc.perform(post("/api/auth/password").session(session).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"current\":\"password1234\",\"next\":\"newpassword99\"}")).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "kim01").param("password", "password1234"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "kim01").param("password", "newpassword99"))
                .andExpect(status().isOk());

        // 역할 변경·사용 중지, 마지막 관리자 보호
        userAdmin.update("kim01", new kr.fitorder.config.UserAdminService.Update(kr.fitorder.config.UserAdminService.Role.ADMIN, null, null), "admin");
        userAdmin.update("kim01", new kr.fitorder.config.UserAdminService.Update(null, false, null), "admin");
        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "kim01").param("password", "newpassword99"))
                .andExpect(status().isUnauthorized());   // 사용 중지된 계정은 로그인 안 됨
        assertThatThrownBy(() -> userAdmin.update("admin", new kr.fitorder.config.UserAdminService.Update(null, false, null), "admin"))
                .hasMessageContaining("본인");
        assertThatThrownBy(() -> userAdmin.update("admin", new kr.fitorder.config.UserAdminService.Update(
                kr.fitorder.config.UserAdminService.Role.USER, null, null), "boss")).hasMessageContaining("마지막 관리자");
        assertThat(auditQuery.actions()).contains("USER_CREATE", "USER_ROLE", "USER_DISABLE", "PASSWORD_CHANGE");
    }

    @Test
    void 시연_모드는_관리자_변경만_막고_시연_초기화는_방문자_입력을_지운다() throws Exception {
        // 방문자가 입력한 값(단가·재고·발주안)
        prices.update(List.of(new UnitPriceService.Change(UniformItemGroup.JUMPER, new BigDecimal("45000"))), "user");
        stock.update(List.of(new kr.fitorder.module.uniform.analysis.StockService.Change(UniformItemGroup.SHIRT, "uniform-order", "100", 5)), "user");
        plans.save(new kr.fitorder.module.uniform.analysis.PlanService.SaveRequest(2027, null, null, Basis.DEDUP, null, null), "user");
        assertThat(prices.list().stream().filter(p -> p.group() == UniformItemGroup.JUMPER).findFirst().orElseThrow().source())
                .isEqualTo(PriceSource.MANUAL);

        var result = demoReset.reset("admin");

        assertThat(result.prices()).isEqualTo(1);
        assertThat(result.stock()).isEqualTo(1);
        assertThat(result.plans()).isEqualTo(1);
        assertThat(prices.list().stream().filter(p -> p.group() == UniformItemGroup.JUMPER).findFirst().orElseThrow().source())
                .isNotEqualTo(PriceSource.MANUAL);
        assertThat(stock.list()).isEmpty();
        assertThat(demoReset.reset("admin").prices()).isZero();   // 지울 것이 없으면 아무 일도 하지 않는다
        assertThat(auditQuery.actions()).contains("DEMO_RESET");
    }

    private static Average average(GroupResult group, Method method) {
        return group.averages().stream().filter(a -> a.method() == method).findFirst().orElseThrow();
    }

    private static GroupResult group(Backtester.Result result, UniformItemGroup group) {
        return result.groups().stream().filter(g -> g.group() == group).findFirst().orElseThrow();
    }
}
