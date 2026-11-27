package kr.fitorder.module.uniform.loader;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import kr.fitorder.common.Hashes;
import kr.fitorder.common.Json;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.QualityChecker;
import kr.fitorder.common.QualityFlag;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceField;
import kr.fitorder.common.SourceLoader;
import kr.fitorder.module.uniform.domain.UniformOrder;
import kr.fitorder.module.uniform.domain.UniformOrderRepository;

/**
 * 근무복 CSV 한 종을 uniform_order에 적재한다. 두 CSV는 컬럼 매핑만 다르고 처리는 같다.
 * 행은 지우지 않고, 중복·품질 문제는 표시만 한다.
 */
public class UniformOrderLoader implements SourceLoader {

    /** 표준 필드명. application.yml의 columns 왼쪽 키와 같다. */
    static final String ORDER_DATE = "order-date";
    static final String ITEM_CODE = "item-code";
    static final String ITEM = "item";
    static final String SEASON = "season";
    static final String SIZE = "size";
    static final String QUANTITY = "quantity";

    private static final String SEP = "\u001F";

    private static final List<SourceField> FIELDS = List.of(
            new SourceField(ORDER_DATE, "주문일", true),
            new SourceField(ITEM_CODE, "품목코드", false),
            new SourceField(ITEM, "품목명", true),
            new SourceField(SEASON, "계절", false),
            new SourceField(SIZE, "사이즈", true),
            new SourceField(QUANTITY, "수량", true),
            new SourceField("order-status", "주문상태", false),
            new SourceField("gender-code", "성별코드", false));

    private final String sourceCode;
    private final ItemClassifier classifier;
    private final List<QualityChecker<UniformOrder>> checkers;
    private final UniformOrderRepository repository;

    public UniformOrderLoader(String sourceCode, ItemClassifier classifier,
                              List<QualityChecker<UniformOrder>> checkers, UniformOrderRepository repository) {
        this.sourceCode = sourceCode;
        this.classifier = classifier;
        this.checkers = checkers;
        this.repository = repository;
    }

    @Override
    public String sourceCode() {
        return sourceCode;
    }

    @Override
    public List<SourceField> fields() {
        return FIELDS;
    }

    @Override
    public LoadSummary load(LoadContext context, List<RawRecord> records) {
        List<UniformOrder> rows = records.stream().map(r -> toOrder(context, r)).toList();
        checkers.forEach(checker -> checker.inspect(rows));
        repository.saveAll(rows);
        int duplicates = (int) rows.stream().filter(UniformOrder::isDuplicate).count();
        int flagged = (int) rows.stream().filter(r -> !r.flags().isEmpty()).count();
        return new LoadSummary(rows.size(), duplicates, flagged);
    }

    /** 파일 안 최대 주문일의 연도. */
    @Override
    public OptionalInt suggestBaseYear(List<RawRecord> records) {
        return records.stream()
                .map(r -> RecordParser.parseDate(r.field(ORDER_DATE)))
                .flatMap(Optional::stream)
                .mapToInt(LocalDate::getYear)
                .max();
    }

    UniformOrder toOrder(LoadContext context, RawRecord record) {
        String rawDate = record.field(ORDER_DATE);
        String rawQuantity = record.field(QUANTITY);
        Optional<LocalDate> date = RecordParser.parseDate(rawDate);
        Optional<Integer> quantity = RecordParser.parseQuantity(rawQuantity);
        String itemCode = record.field(ITEM_CODE);
        String rawItem = record.field(ITEM);
        ItemClassifier.Classification item = classifier.classify(sourceCode, itemCode, rawItem);

        UniformOrder order = new UniformOrder(context.loadId(), sourceCode, context.baseYear(),
                (int) record.lineNo(), date.orElse(null), itemCode, rawItem, item.item(), item.group(),
                record.field(SEASON), RecordParser.normalizeSize(record.field(SIZE)), quantity.orElse(null),
                rowHash(context, record), Json.object(record.original()));

        if (rawDate != null && date.isEmpty()) {
            order.addFlag(QualityFlag.INVALID_DATE);
        }
        if (rawQuantity != null && quantity.isEmpty()) {
            order.addFlag(QualityFlag.INVALID_QUANTITY);
        }
        return order;
    }

    /** 출처 + 기준연 + 원본 컬럼 값 전체. 기준연이 들어가 있어 다른 해 파일의 같은 행과는 겹치지 않는다. */
    private String rowHash(LoadContext context, RawRecord record) {
        StringBuilder sb = new StringBuilder(sourceCode).append(SEP).append(context.baseYear());
        record.original().values().forEach(v -> sb.append(SEP).append(v == null ? "" : v.strip()));
        return Hashes.sha256(sb.toString());
    }
}
