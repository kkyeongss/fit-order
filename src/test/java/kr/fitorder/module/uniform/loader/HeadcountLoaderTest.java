package kr.fitorder.module.uniform.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceSpec;
import kr.fitorder.module.uniform.domain.Headcount;
import kr.fitorder.module.uniform.domain.HeadcountRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class HeadcountLoaderTest {

    private final HeadcountRepository repository = mock(HeadcountRepository.class);
    private final HeadcountLoader loader = new HeadcountLoader(repository);
    private final SourceSpec spec = new SourceSpec("정원현황", null, null, null, null,
            Map.of("base-date", "기준일자", "department", "부서명"));

    private LoadContext context() {
        return new LoadContext(1, "headcount", 2026, spec);
    }

    private static RawRecord row(String date, String dept, String... cells) {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("기준일자", date);
        original.put("부서명", dept);
        for (int i = 0; i < cells.length; i += 2) {
            original.put(cells[i], cells[i + 1]);
        }
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("base-date", date);
        fields.put("department", dept);
        return new RawRecord(2, original, fields);
    }

    @SuppressWarnings("unchecked")
    private List<Headcount> saved() {
        ArgumentCaptor<List<Headcount>> captor = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(captor.capture());
        return new ArrayList<>(captor.getValue());
    }

    @Test
    void 넓은_표를_직군_직급별_행으로_편다() {
        LoadSummary summary = loader.load(context(), List.of(
                row("2026-01-01", "경영지원팀", "임원수", "1", "일반직3급", "2", "기술직5급", "0", "계약직", "4")));

        List<Headcount> rows = saved();
        assertThat(rows).extracting(Headcount::getJobFamily, Headcount::getGrade, Headcount::getQuota)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("임원", "", 1),
                        org.assertj.core.groups.Tuple.tuple("일반직", "3급", 2),
                        org.assertj.core.groups.Tuple.tuple("계약직", "", 4));
        assertThat(rows.getFirst().getBaseDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(rows.getFirst().getDepartment()).isEqualTo("경영지원팀");
        assertThat(summary).isEqualTo(new LoadSummary(1, 0, 0));
    }

    @Test
    void 날짜가_없거나_숫자가_아닌_칸이_있으면_품질_건수로_센다() {
        LoadSummary summary = loader.load(context(), List.of(
                row("", "A팀", "업무직", "1"),
                row("2026-01-01", "B팀", "업무직", "x", "지원직", "2")));

        assertThat(saved()).extracting(Headcount::getDepartment).containsExactly("B팀");
        assertThat(summary.flaggedCount()).isEqualTo(2);
    }

    @Test
    void 기준연_기본값은_최근_기준일자의_연도() {
        assertThat(loader.suggestBaseYear(List.of(row("2025-01-01", "A"), row("2026-01-01", "A")))).hasValue(2026);
    }

    @Test
    void 컬럼명을_직군과_직급으로_나눈다() {
        assertThat(HeadcountLoader.familyGrade("일반직1급")).containsExactly("일반직", "1급");
        assertThat(HeadcountLoader.familyGrade("임원수")).containsExactly("임원", "");
        assertThat(HeadcountLoader.familyGrade("업무직")).containsExactly("업무직", "");
    }
}
