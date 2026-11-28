package kr.fitorder.module.uniform.cleansing;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.EsgDataChangedEvent;
import kr.fitorder.ingestion.DataSourceService;
import kr.fitorder.module.uniform.UniformModule;
import kr.fitorder.module.uniform.domain.SizeMapping;
import kr.fitorder.module.uniform.domain.SizeMappingRepository;
import kr.fitorder.module.uniform.domain.UniformOrderRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 출처별 사이즈 코드를 표준 사이즈에 연결한다.
 * 매핑이 없으면 '코드 (출처)'로 보여 두 사이즈 체계를 구분한다.
 */
@Service
public class SizeMappingService {

    public record SizeRow(String source, String sourceName, String rawSize, long rowCount, String standardSize) {
    }

    public record Change(String source, String rawSize, String standardSize) {
    }

    private record Key(String source, String rawSize) {
    }

    private static final Comparator<String> SIZE_ORDER = Comparator
            .comparing((String s) -> !s.matches("\\d+"))
            .thenComparing(s -> s.matches("\\d+") ? Integer.parseInt(s) : 0)
            .thenComparing(Comparator.naturalOrder());

    private final UniformOrderRepository orders;
    private final SizeMappingRepository mappings;
    private final DataSourceService sources;
    private final AuditService audit;
    private final ApplicationEventPublisher events;

    public SizeMappingService(UniformOrderRepository orders, SizeMappingRepository mappings,
                              DataSourceService sources, AuditService audit, ApplicationEventPublisher events) {
        this.orders = orders;
        this.mappings = mappings;
        this.sources = sources;
        this.audit = audit;
        this.events = events;
    }

    /** 활성 데이터에 나오는 사이즈 코드 전체와 현재 매핑. */
    @Transactional(readOnly = true)
    public List<SizeRow> list() {
        Map<String, String> names = sources.names();
        return orders.findSizeUsage().stream()
                .map(u -> new SizeRow(u.getSource(), names.getOrDefault(u.getSource(), u.getSource()), u.getRawSize(), u.getRowCount(),
                        u.getStandardSize()))
                .sorted(Comparator.comparing(SizeRow::source).thenComparing(SizeRow::rawSize, SIZE_ORDER))
                .toList();
    }

    /** standardSize가 비어 있으면 매핑을 지운다. */
    @Transactional
    public void update(List<Change> changes, String user) {
        for (Change change : changes) {
            if (change.source() == null || change.rawSize() == null) {
                throw new BusinessException("출처와 원본 사이즈는 필수입니다.");
            }
            String standard = change.standardSize() == null ? "" : change.standardSize().strip();
            var existing = mappings.findBySourceAndRawSize(change.source(), change.rawSize());
            if (standard.isEmpty()) {
                existing.ifPresent(mappings::delete);
            } else if (existing.isPresent()) {
                existing.get().change(standard, user);
            } else {
                mappings.save(new SizeMapping(change.source(), change.rawSize(), standard, user));
            }
            audit.record(user, "SIZE_MAPPING", change.source() + "/" + change.rawSize(),
                    standard.isEmpty() ? "삭제" : "→ " + standard);
        }
        events.publishEvent(new EsgDataChangedEvent(UniformModule.CODE, "사이즈 매핑 변경"));
    }

    /** 화면·엑셀에 쓸 사이즈 이름표를 만든다. 한 번 읽어 두고 여러 행에 쓴다. */
    @Transactional(readOnly = true)
    public SizeLabeler labeler() {
        Map<Key, String> map = new HashMap<>();
        mappings.findAll().forEach(m -> map.put(new Key(m.getSource(), m.getRawSize()), m.getStandardSize()));
        Map<String, String> names = sources.names();
        return (source, rawSize) -> {
            if (rawSize == null) {
                return "미기재";
            }
            String standard = map.get(new Key(source, rawSize));
            return standard != null ? standard : rawSize + " (" + names.getOrDefault(source, source) + ")";
        };
    }

    @FunctionalInterface
    public interface SizeLabeler {
        String label(String source, String rawSize);
    }
}
