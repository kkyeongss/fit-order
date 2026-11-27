package kr.fitorder.module.uniform.loader;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceLoader;
import kr.fitorder.module.uniform.domain.Headcount;
import kr.fitorder.module.uniform.domain.HeadcountRepository;

/**
 * 정원현황을 headcount에 적재한다. 원본은 부서 한 행에 직군·직급별 정원 컬럼이 옆으로 늘어선 표다.
 * 기준일자·부서명 외의 컬럼을 모두 '직군 + 직급'으로 풀어 세로로 저장한다.
 * 예) 일반직3급 → (일반직, 3급), 임원수 → (임원, ""), 계약직 → (계약직, "")
 */
public class HeadcountLoader implements SourceLoader {

    public static final String SOURCE = "headcount";

    static final String BASE_DATE = "base-date";
    static final String DEPARTMENT = "department";

    private static final Pattern FAMILY_GRADE = Pattern.compile("(.+?)(\\d+급)");

    private final Set<String> keyColumns;
    private final HeadcountRepository repository;

    /** @param keyColumns 정원이 아닌 컬럼(설정의 columns 값: 기준일자, 부서명) */
    public HeadcountLoader(Set<String> keyColumns, HeadcountRepository repository) {
        this.keyColumns = Set.copyOf(keyColumns);
        this.repository = repository;
    }

    @Override
    public String sourceCode() {
        return SOURCE;
    }

    @Override
    public LoadSummary load(LoadContext context, List<RawRecord> records) {
        List<Headcount> rows = new ArrayList<>();
        int flagged = 0;
        for (RawRecord record : records) {
            Optional<LocalDate> date = RecordParser.parseDate(record.field(BASE_DATE));
            String department = RecordParser.normalizeName(record.field(DEPARTMENT));
            if (date.isEmpty() || department == null) {
                flagged++;
                continue;
            }
            boolean bad = false;
            for (var cell : record.original().entrySet()) {
                if (keyColumns.contains(cell.getKey()) || cell.getValue() == null || cell.getValue().isBlank()) {
                    continue;
                }
                Optional<Integer> quota = RecordParser.parseQuantity(cell.getValue());
                if (quota.isEmpty()) {
                    bad = true;
                } else if (quota.get() > 0) {
                    String[] familyGrade = familyGrade(cell.getKey());
                    rows.add(new Headcount(context.loadId(), date.get(), department, familyGrade[0], familyGrade[1],
                            quota.get()));
                }
            }
            if (bad) {
                flagged++;
            }
        }
        repository.saveAll(rows);
        return new LoadSummary(records.size(), 0, flagged);
    }

    /** 가장 최근 기준일자의 연도. */
    @Override
    public OptionalInt suggestBaseYear(List<RawRecord> records) {
        return records.stream()
                .map(r -> RecordParser.parseDate(r.field(BASE_DATE)))
                .flatMap(Optional::stream)
                .mapToInt(LocalDate::getYear)
                .max();
    }

    static String[] familyGrade(String column) {
        String name = column.strip();
        Matcher m = FAMILY_GRADE.matcher(name);
        if (m.matches()) {
            return new String[] {m.group(1), m.group(2)};
        }
        return new String[] {name.endsWith("수") ? name.substring(0, name.length() - 1) : name, ""};
    }
}
