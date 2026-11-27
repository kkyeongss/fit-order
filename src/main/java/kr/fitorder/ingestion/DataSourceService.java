package kr.fitorder.ingestion;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.DataIngestion;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.RawData;
import kr.fitorder.common.SourceField;
import kr.fitorder.common.SourceLoader;
import kr.fitorder.common.SourceSpec;
import kr.fitorder.common.esg.EsgModuleRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데이터 원천 관리: 원천별 적재 방식·API 주소·컬럼 매핑을 보고 고친다.
 * CSV로만 제공되던 데이터가 Open API로 바뀌면 방식과 주소만 바꾸면 된다(코드·배포 없음).
 */
@Service
public class DataSourceService {

    public record LastLoad(OffsetDateTime at, String status, String message, int rowCount) {
    }

    public record SourceView(String code, String itemCode, String name, LoadMethod method, String url, int perPage,
                             String charset, Map<String, String> columns, List<SourceField> fields,
                             boolean apiKeyConfigured, LastLoad lastLoad, Integer activeBaseYear,
                             String updatedBy, OffsetDateTime updatedAt) {
    }

    public record Update(LoadMethod method, String url, Integer perPage, String charset, Map<String, String> columns) {
    }

    /**
     * 원천을 실제로 읽어 본 결과. 적재하지 않는다.
     *
     * @param missingColumns 매핑했는데 원천에 없는 컬럼
     * @param unmappedFields 매핑되지 않은 필수 필드
     */
    public record Preview(List<String> columns, List<Map<String, String>> sampleRows, int rowCount,
                          Map<String, String> mapping, List<String> missingColumns, List<String> unmappedFields) {
    }

    private static final int SAMPLE_ROWS = 5;

    private final DataSourceRepository sources;
    private final EsgModuleRegistry registry;
    private final List<DataIngestion> ingestions;
    private final ApiIngestion apiIngestion;
    private final LoadHistoryRepository histories;
    private final AuditService audit;

    public DataSourceService(DataSourceRepository sources, EsgModuleRegistry registry, List<DataIngestion> ingestions,
                             ApiIngestion apiIngestion, LoadHistoryRepository histories, AuditService audit) {
        this.sources = sources;
        this.registry = registry;
        this.ingestions = ingestions;
        this.apiIngestion = apiIngestion;
        this.histories = histories;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public SourceSpec spec(String code) {
        return find(code).toSpec();
    }

    /** 화면에 보일 원천 이름. 없는 코드면 코드를 그대로. */
    @Transactional(readOnly = true)
    public Map<String, String> names() {
        return sources.findAll().stream().collect(Collectors.toMap(DataSource::getCode, DataSource::getName));
    }

    @Transactional(readOnly = true)
    public List<SourceView> list() {
        return sources.findAllByOrderByItemCodeAscCodeAsc().stream().map(this::view).toList();
    }

    @Transactional
    public SourceView update(String code, Update update, String user) {
        DataSource source = find(code);
        List<SourceField> fields = fields(code);

        LoadMethod method = update.method();
        if (method == null) {
            throw new BusinessException("적재 방식을 고르세요.");
        }
        ingestion(method);
        String url = blankToNull(update.url());
        if (method == LoadMethod.API && (url == null || !url.startsWith("https://"))) {
            throw new BusinessException("API 적재는 https:// 로 시작하는 주소가 필요합니다.");
        }
        int perPage = update.perPage() == null ? source.getPerPage() : update.perPage();
        if (perPage < 1 || perPage > 10_000) {
            throw new BusinessException("한 번에 받을 행 수는 1~10000 사이여야 합니다.");
        }
        String charset = blankToNull(update.charset()) == null ? source.getCharset() : update.charset().strip();
        try {
            CsvUploadIngestion.charsetOf(charset);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("알 수 없는 인코딩입니다: " + charset);
        }
        Map<String, String> columns = cleanColumns(update.columns(), fields);

        source.change(method, url, perPage, charset, columns, user);
        audit.record(user, "SOURCE_UPDATE", code, "방식 " + method + ", 주소 " + (url == null ? "-" : url)
                + ", 인코딩 " + charset + ", 컬럼 " + columns);
        return view(source);
    }

    /**
     * 원천을 읽어 실제 컬럼과 앞쪽 몇 행을 보여준다. 저장하기 전 설정으로도 시험할 수 있다.
     *
     * @param draft   시험할 설정(null이면 저장된 설정)
     * @param content 파일 원천일 때 올린 파일
     */
    @Transactional(readOnly = true)
    public Preview preview(String code, Update draft, String fileName, byte[] content) {
        DataSource source = find(code);
        SourceSpec saved = source.toSpec();
        SourceSpec spec = draft == null ? saved : new SourceSpec(saved.name(),
                draft.method() != null ? draft.method() : saved.method(),
                draft.url() != null ? blankToNull(draft.url()) : saved.url(),
                draft.perPage() != null ? draft.perPage() : saved.perPage(),
                blankToNull(draft.charset()) != null ? draft.charset().strip() : saved.charset(),
                draft.columns() != null ? draft.columns() : saved.columns());
        if (spec.method() == LoadMethod.UPLOAD && (content == null || content.length == 0)) {
            throw new BusinessException("파일 원천은 미리볼 파일을 올려 주세요.");
        }

        RawData data = ingestion(spec.method()).read(new IngestionRequest(code, spec, fileName, content));
        List<Map<String, String>> sample = data.records().stream().limit(SAMPLE_ROWS)
                .map(r -> (Map<String, String>) new LinkedHashMap<>(r.original())).toList();
        List<String> unmapped = fields(code).stream()
                .filter(f -> f.required() && blankToNull(spec.columns().get(f.name())) == null)
                .map(SourceField::label).toList();
        return new Preview(data.columns(), sample, data.records().size(), spec.columns(),
                spec.missingColumns(data.columns()), unmapped);
    }

    DataIngestion ingestion(LoadMethod method) {
        return ingestions.stream()
                .filter(i -> i.method() == method)
                .findFirst()
                .orElseThrow(() -> new BusinessException(method + " 적재는 아직 구현되지 않았습니다."));
    }

    private SourceView view(DataSource s) {
        LastLoad last = histories.findFirstBySourceOrderByLoadedAtDesc(s.getCode())
                .map(h -> new LastLoad(h.getLoadedAt(), h.getStatus().name(), h.getMessage(), h.getRowCount()))
                .orElse(null);
        Integer activeYear = histories.findFirstBySourceAndActiveTrue(s.getCode())
                .map(LoadHistory::getBaseYear).orElse(null);
        return new SourceView(s.getCode(), s.getItemCode(), s.getName(), s.getMethod(), s.getUrl(), s.getPerPage(),
                s.getCharset(), s.getColumns(), fields(s.getCode()), apiIngestion.keyConfigured(), last, activeYear,
                s.getUpdatedBy(), s.getUpdatedAt());
    }

    private List<SourceField> fields(String code) {
        return registry.loader(code).map(r -> r.loader().fields()).orElse(List.of());
    }

    private DataSource find(String code) {
        return sources.findById(code).orElseThrow(() -> new BusinessException("알 수 없는 원천입니다: " + code));
    }

    private static Map<String, String> cleanColumns(Map<String, String> requested, List<SourceField> fields) {
        Map<String, String> input = requested == null ? Map.of() : requested;
        Set<String> known = fields.stream().map(SourceField::name).collect(Collectors.toSet());
        List<String> unknown = input.keySet().stream().filter(k -> !known.contains(k)).toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("알 수 없는 필드입니다: " + unknown);
        }
        Map<String, String> columns = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();
        for (SourceField field : fields) {
            String column = blankToNull(input.get(field.name()));
            if (column != null) {
                columns.put(field.name(), column.strip());
            } else if (field.required()) {
                missing.add(field.label());
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException("필수 필드의 원본 컬럼명을 입력하세요: " + missing);
        }
        return columns;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
