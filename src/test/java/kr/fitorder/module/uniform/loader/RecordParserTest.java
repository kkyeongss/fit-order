package kr.fitorder.module.uniform.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RecordParserTest {

    @ParameterizedTest
    @ValueSource(strings = {"2024-04-01", "2024/4/1", "2024.04.01", "2024.04.01.", "20240401", "2024-04-01 00:00:00"})
    void 여러_날짜_형식을_읽는다(String raw) {
        assertThat(RecordParser.parseDate(raw)).contains(LocalDate.of(2024, 4, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-13-01", "2024-04", "abc", ""})
    void 읽을_수_없는_날짜는_비운다(String raw) {
        assertThat(RecordParser.parseDate(raw)).isEmpty();
    }

    @Test
    void 수량을_읽는다() {
        assertThat(RecordParser.parseQuantity("1,200")).contains(1200);
        assertThat(RecordParser.parseQuantity("10.0")).contains(10);
        assertThat(RecordParser.parseQuantity("1.5")).isEmpty();
        assertThat(RecordParser.parseQuantity("-3")).isEmpty();
        assertThat(RecordParser.parseQuantity("x")).isEmpty();
        assertThat(RecordParser.parseQuantity(null)).isEmpty();
    }

    @Test
    void 사이즈_코드를_정리한다() {
        assertThat(RecordParser.normalizeSize(" 95 ")).isEqualTo("95");
        assertThat(RecordParser.normalizeSize("95.0")).isEqualTo("95");
        assertThat(RecordParser.normalizeSize("XL")).isEqualTo("XL");
        assertThat(RecordParser.normalizeSize(" ")).isNull();
    }
}
