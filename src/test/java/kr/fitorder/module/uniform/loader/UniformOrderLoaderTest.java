package kr.fitorder.module.uniform.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.QualityFlag;
import kr.fitorder.common.RawRecord;
import kr.fitorder.module.uniform.UniformProperties;
import kr.fitorder.module.uniform.UniformSources;
import kr.fitorder.module.uniform.UniformTestFixtures;
import kr.fitorder.module.uniform.cleansing.CodeChecker;
import kr.fitorder.module.uniform.cleansing.DuplicateChecker;
import kr.fitorder.module.uniform.cleansing.MissingValueChecker;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import kr.fitorder.module.uniform.domain.UniformOrder;
import kr.fitorder.module.uniform.domain.UniformOrderRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class UniformOrderLoaderTest {

    private final UniformProperties properties = UniformTestFixtures.properties();
    private final UniformOrderRepository repository = mock(UniformOrderRepository.class);
    private final UniformOrderLoader loader = new UniformOrderLoader(UniformSources.ORDER_INFO,
            new ItemClassifier(properties),
            List.of(new DuplicateChecker(), new MissingValueChecker(), new CodeChecker(properties)),
            repository);

    private long line = 1;

    /** 주문현황정보 한 행: 근무복코드, 근무복명, 사이즈, 성별코드, 사용날짜, 주문수량 */
    private RawRecord row(String code, String name, String size, String date, String quantity) {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("근무복코드", code);
        original.put("근무복명", name);
        original.put("사이즈", size);
        original.put("성별코드", "10");
        original.put("사용날짜", date);
        original.put("주문수량", quantity);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("item-code", code);
        fields.put("item", name);
        fields.put("size", size);
        fields.put("order-date", date);
        fields.put("quantity", quantity);
        return new RawRecord(++line, original, fields);
    }

    @SuppressWarnings("unchecked")
    private List<UniformOrder> saved() {
        ArgumentCaptor<List<UniformOrder>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    @Test
    void 완전히_같은_행은_두_번째부터_중복으로_표시하고_지우지_않는다() {
        List<RawRecord> records = List.of(
                row("10", "점퍼", "50", "2024-04-01", "3"),
                row("10", "점퍼", "50", "2024-04-01", "3"),
                row("10", "점퍼", "50", "2024-04-01", "3"),
                row("10", "점퍼", "55", "2024-04-01", "3"));

        LoadSummary summary = loader.load(new LoadContext(1, UniformSources.ORDER_INFO, 2026), records);

        List<UniformOrder> rows = saved();
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(UniformOrder::isDuplicate).containsExactly(false, true, true, false);
        assertThat(summary).isEqualTo(new LoadSummary(4, 2, 0));
    }

    @Test
    void 행해시에_기준연이_들어가_다른_해_파일의_같은_행과_겹치지_않는다() {
        RawRecord record = row("10", "점퍼", "50", "2024-04-01", "3");
        UniformOrder in2026 = loader.toOrder(new LoadContext(1, UniformSources.ORDER_INFO, 2026), record);
        UniformOrder in2027 = loader.toOrder(new LoadContext(2, UniformSources.ORDER_INFO, 2027), record);
        UniformOrder again2026 = loader.toOrder(new LoadContext(3, UniformSources.ORDER_INFO, 2026), record);

        assertThat(in2026.getRowHash()).isNotEqualTo(in2027.getRowHash());
        assertThat(in2026.getRowHash()).isEqualTo(again2026.getRowHash());
    }

    @Test
    void 결측과_모르는_코드는_적재하되_플래그를_단다() {
        List<RawRecord> records = List.of(
                row("10", "점퍼", "50", "2018-04-01", ""),
                row("10", "점퍼", "50", "2018-04-01", "많음"),
                row("11", "점퍼", "95", "2020-04-01", "2"),
                row("99", "모자", "50", "날짜아님", "1"));

        LoadSummary summary = loader.load(new LoadContext(1, UniformSources.ORDER_INFO, 2026), records);

        List<UniformOrder> rows = saved();
        assertThat(rows.get(0).flags()).containsExactly(QualityFlag.MISSING_QUANTITY);
        assertThat(rows.get(1).flags()).containsExactly(QualityFlag.INVALID_QUANTITY);
        assertThat(rows.get(2).flags()).containsExactly(QualityFlag.UNKNOWN_SIZE);
        assertThat(rows.get(3).flags()).containsExactlyInAnyOrder(QualityFlag.INVALID_DATE, QualityFlag.UNKNOWN_ITEM);
        assertThat(summary.flaggedCount()).isEqualTo(4);
    }

    @Test
    void 원본_값을_보존하고_표준_필드로_변환한다() {
        UniformOrder order = loader.toOrder(new LoadContext(7, UniformSources.ORDER_INFO, 2026),
                row("11", "동계 점퍼", "60.0", "20230815", "12"));

        assertThat(order.getLoadId()).isEqualTo(7);
        assertThat(order.getBaseYear()).isEqualTo(2026);
        assertThat(order.getOrderDate()).isEqualTo(LocalDate.of(2023, 8, 15));
        assertThat(order.getItem()).isEqualTo("점퍼");
        assertThat(order.getRawItem()).isEqualTo("동계 점퍼");
        assertThat(order.getItemGroup()).isEqualTo(UniformItemGroup.JUMPER);
        assertThat(order.getRawSize()).isEqualTo("60");
        assertThat(order.getQuantity()).isEqualTo(12);
        assertThat(order.getRawValues()).contains("\"근무복명\":\"동계 점퍼\"").contains("\"사이즈\":\"60.0\"");
    }

    @Test
    void 기준연_기본값은_파일_안_최대_주문일의_연도() {
        List<RawRecord> records = List.of(
                row("10", "점퍼", "50", "2017-11-02", "1"),
                row("10", "점퍼", "50", "2026-08-30", "1"),
                row("10", "점퍼", "50", "", "1"));

        assertThat(loader.suggestBaseYear(records)).hasValue(2026);
    }

    @Test
    void 저장소에_한_번에_저장한다() {
        loader.load(new LoadContext(1, UniformSources.ORDER_INFO, 2026), List.of(row("10", "점퍼", "50", "2024-04-01", "1")));
        verify(repository).saveAll(anyList());
    }
}
