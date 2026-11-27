package kr.fitorder.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.RawData;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceSpec;
import org.junit.jupiter.api.Test;

class CsvUploadIngestionTest {

    private static final Charset CP949 = CsvUploadIngestion.charsetOf("CP949");

    private final CsvUploadIngestion ingestion = new CsvUploadIngestion();

    private static SourceSpec spec() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("order-date", "주문일");
        columns.put("item", "피복종류");
        columns.put("quantity", "수량");
        return SourceSpec.upload("근무복 주문현황", "CP949", columns);
    }

    private RawData read(String csv, Charset charset) {
        return ingestion.read(new IngestionRequest("uniform-order", spec(), "a.csv", csv.getBytes(charset)));
    }

    @Test
    void CP949_파일을_컬럼_매핑대로_읽는다() {
        RawData data = read("""
                주문일,피복종류,사이즈,수량
                2024-04-01,셔츠(일반),100,5
                2024-08-02,재킷, 95 ,
                """, CP949);
        List<RawRecord> records = data.records();

        assertThat(data.columns()).containsExactly("주문일", "피복종류", "사이즈", "수량");
        assertThat(records).hasSize(2);
        RawRecord first = records.get(0);
        assertThat(first.lineNo()).isEqualTo(2);
        assertThat(first.field("item")).isEqualTo("셔츠(일반)");
        assertThat(first.field("quantity")).isEqualTo("5");
        assertThat(first.original()).containsKeys("주문일", "피복종류", "사이즈", "수량");
        assertThat(records.get(1).field("quantity")).isNull();
        assertThat(records.get(1).original().get("사이즈")).isEqualTo("95");
    }

    @Test
    void 매핑한_컬럼_중_파일에_없는_것을_찾는다() {
        RawData data = read("주문일,품목,수량\n2024-04-01,셔츠,5\n", CP949);
        assertThat(spec().missingColumns(data.columns())).containsExactly("피복종류");
    }

    @Test
    void 인코딩이_다르면_인코딩을_확인하라고_알려준다() {
        // UTF-8 한글은 CP949로 디코딩하면 깨진 바이트가 나온다
        assertThatThrownBy(() -> read("주문일,피복종류,수량\n2024-04-01,셔츠(일반),5\n", StandardCharsets.UTF_8))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("인코딩");
    }

    @Test
    void 빈_파일은_거절한다() {
        assertThatThrownBy(() -> ingestion.read(new IngestionRequest("uniform-order", spec(), "a.csv", new byte[0])))
                .isInstanceOf(BusinessException.class);
    }
}
