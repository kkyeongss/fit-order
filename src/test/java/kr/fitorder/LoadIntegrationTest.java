package kr.fitorder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.ingestion.CsvUploadIngestion;
import kr.fitorder.ingestion.DataSourceService;
import kr.fitorder.ingestion.DataSourceService.Preview;
import kr.fitorder.ingestion.DataSourceService.SourceView;
import kr.fitorder.ingestion.DataSourceService.Update;
import kr.fitorder.ingestion.DataSourceSynchronizer;
import kr.fitorder.ingestion.LoadHistory;
import kr.fitorder.ingestion.LoadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 실제 공공데이터 CSV를 실제 PostgreSQL에 적재해 기획서의 분석 수치가 재현되는지 고정한다.
 * Docker가 필요하다(로컬은 Colima).
 */
@SpringBootTest
@Testcontainers
class LoadIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18");

    private static final Path ORDER_CSV = Path.of("data/한국체육산업개발주식회사_근무복 주문현황_20260827.csv");
    private static final Path ORDER_INFO_CSV = Path.of("data/한국체육산업개발주식회사_근무복 주문현황정보_20260827.csv");
    private static final Charset CP949 = CsvUploadIngestion.charsetOf("CP949");

    @Autowired
    LoadService loadService;

    @Autowired
    DataSourceService dataSources;

    @Autowired
    DataSourceSynchronizer synchronizer;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        jdbc.execute("truncate uniform_order, headcount, load_history, audit_log, size_mapping restart identity cascade");
        jdbc.execute("delete from data_source");
        jdbc.execute("update esg_item set enabled = true, load_method = 'UPLOAD'");
        synchronizer.run(null);
    }

    private LoadHistory upload(String source, Integer baseYear, Path file) throws IOException {
        return loadService.upload(source, baseYear, file.getFileName().toString(), Files.readAllBytes(file), "test");
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void 실제_근무복_CSV_적재_결과가_기획서_수치와_같다() throws IOException {
        LoadHistory order = upload("uniform-order", null, ORDER_CSV);
        LoadHistory info = upload("uniform-order-info", null, ORDER_INFO_CSV);

        assertThat(order.getBaseYear()).isEqualTo(2026);
        assertThat(order.getRowCount()).isEqualTo(1332);
        assertThat(order.getDuplicateCount()).isEqualTo(327);
        assertThat(info.getRowCount()).isEqualTo(1535);
        assertThat(info.getDuplicateCount()).isEqualTo(331);
        assertThat(count("select count(*) from uniform_order_active where quantity is null")).isEqualTo(41);
        assertThat(count("select count(*) from uniform_order_active where quality_flags like '%UNKNOWN_SIZE%'"))
                .isEqualTo(25);
        assertThat(count("select count(*) from uniform_order_active where item_group = 'UNKNOWN'")).isZero();

        // 중복 제거 기준 2020~2025 연평균 1,402벌
        int total = count("""
                select sum(quantity) from uniform_order_active
                where not duplicate and order_date between '2020-01-01' and '2025-12-31'""");
        assertThat(Math.round(total / 6.0)).isEqualTo(1402);
    }

    @Test
    void 같은_파일은_다시_적재하지_않는다() throws IOException {
        upload("uniform-order", null, ORDER_CSV);

        assertThatThrownBy(() -> upload("uniform-order", null, ORDER_CSV))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("이미 적재한 파일");
    }

    @Test
    void 다음_해_파일을_올리면_최신_기준연만_집계되고_이력은_남는다() throws IOException {
        upload("uniform-order", 2026, ORDER_CSV);
        // 내년 파일: 내용은 같고 파일만 다르다(전체 이력이 다시 담겨 온다)
        byte[] nextYear = (Files.readString(ORDER_CSV, CP949) + "\n").getBytes(CP949);
        LoadHistory next = loadService.upload("uniform-order", 2027, "2027.csv", nextYear, "test");

        assertThat(next.isActive()).isTrue();
        assertThat(next.getDuplicateCount()).isEqualTo(327);
        assertThat(count("select count(*) from uniform_order_active")).isEqualTo(1332);
        assertThat(count("select count(*) from uniform_order")).isEqualTo(1332 * 2);
        assertThat(count("select count(*) from load_history where source = 'uniform-order' and active")).isEqualTo(1);
    }

    @Test
    void 원천은_시작할_때_설정값으로_등록된다() {
        assertThat(dataSources.list()).extracting(SourceView::code, SourceView::method)
                .contains(org.assertj.core.groups.Tuple.tuple("uniform-order", LoadMethod.UPLOAD),
                        org.assertj.core.groups.Tuple.tuple("uniform-order-info", LoadMethod.UPLOAD),
                        org.assertj.core.groups.Tuple.tuple("headcount", LoadMethod.API));
    }

    @Test
    void 적재_방식을_화면에서_바꾸면_코드_수정_없이_다른_방식으로_적재된다() {
        // 정원현황(API)을 망분리 환경처럼 파일 업로드로 바꾼다
        dataSources.update("headcount", new Update(LoadMethod.UPLOAD, null, null, "CP949",
                Map.of("base-date", "기준일자", "department", "부서명")), "admin");
        byte[] csv = """
                기준일자,부서명,임원수,일반직3급,계약직
                2026-01-01,경영지원팀,1,2,4
                """.getBytes(CP949);

        LoadHistory history = loadService.upload("headcount", null, "headcount.csv", csv, "admin");

        assertThat(history.getBaseYear()).isEqualTo(2026);
        assertThat(count("select sum(quota) from headcount_active")).isEqualTo(7);
        assertThat(count("select count(*) from audit_log where action = 'SOURCE_UPDATE'")).isEqualTo(1);
        // API 방식 원천에 파일을 올리면 안내한다
        assertThatThrownBy(() -> loadService.fetch("headcount", null, "admin"))
                .hasMessageContaining("파일을 업로드하세요");
    }

    @Test
    void 잘못된_원천_설정은_저장하지_않는다() {
        assertThatThrownBy(() -> dataSources.update("headcount",
                new Update(LoadMethod.API, null, null, null, Map.of("base-date", "기준일자", "department", "부서명")), "admin"))
                .hasMessageContaining("https://");
        assertThatThrownBy(() -> dataSources.update("uniform-order",
                new Update(LoadMethod.UPLOAD, null, null, null, Map.of("order-date", "주문일")), "admin"))
                .hasMessageContaining("필수 필드");
        assertThatThrownBy(() -> dataSources.update("uniform-order",
                new Update(LoadMethod.BATCH, null, null, null, Map.of()), "admin"))
                .hasMessageContaining("구현되지 않았습니다");
    }

    @Test
    void 미리보기로_실제_컬럼과_매핑_문제를_보여준다() throws IOException {
        // 주문현황 원천에 주문현황정보 파일을 잘못 올린 경우
        Preview preview = dataSources.preview("uniform-order", null, "wrong.csv", Files.readAllBytes(ORDER_INFO_CSV));

        assertThat(preview.columns()).contains("근무복코드", "사용날짜");
        assertThat(preview.missingColumns()).contains("주문일", "피복종류");
        assertThat(preview.sampleRows()).hasSize(5);
        assertThat(preview.rowCount()).isEqualTo(1535);
        assertThat(count("select count(*) from load_history")).isZero();
    }
}
