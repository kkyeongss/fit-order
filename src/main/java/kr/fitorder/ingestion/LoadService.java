package kr.fitorder.ingestion;

import java.util.List;
import java.util.stream.Collectors;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.DataIngestion;
import kr.fitorder.common.Hashes;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.Json;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceSpec;
import kr.fitorder.common.esg.EsgItem;
import kr.fitorder.common.esg.EsgItemRepository;
import kr.fitorder.common.esg.EsgModuleRegistry;
import kr.fitorder.common.esg.EsgModuleRegistry.RegisteredLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 적재 흐름: 원천 확인 → 공통 레코드로 읽기 → 같은 데이터 거르기 → 항목 적재기로 저장 → 활성 적재 갱신.
 * 적재 방식(업로드·API·배치)이 바뀌어도 이 흐름과 항목 적재기는 그대로다.
 * 원천의 적재 방식은 설정(sources.*.method)이 우선하고, 없으면 ESG 항목의 적재 방식을 따른다.
 */
@Service
public class LoadService {

    private record Target(RegisteredLoader registered, EsgItem item, SourceSpec spec, LoadMethod method) {
    }

    private final EsgModuleRegistry registry;
    private final EsgItemRepository items;
    private final List<DataIngestion> ingestions;
    private final IngestionProperties properties;
    private final LoadHistoryRepository histories;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public LoadService(EsgModuleRegistry registry, EsgItemRepository items, List<DataIngestion> ingestions,
                       IngestionProperties properties, LoadHistoryRepository histories, AuditService audit,
                       TransactionTemplate tx) {
        this.registry = registry;
        this.items = items;
        this.ingestions = ingestions;
        this.properties = properties;
        this.histories = histories;
        this.audit = audit;
        this.tx = tx;
    }

    /**
     * 업로드한 파일 하나를 적재한다. 같은 파일(파일 해시)은 두 번 적재하지 않는다.
     *
     * @param baseYear 데이터 기준연. null이면 적재기가 제안하는 값(파일 안 최대 날짜의 연도)
     */
    public LoadHistory upload(String sourceCode, Integer baseYear, String fileName, byte[] content, String user) {
        Target target = target(sourceCode, LoadMethod.UPLOAD);
        String hash = Hashes.sha256(content);
        if (histories.existsByFileHashAndStatus(hash, LoadStatus.SUCCESS)) {
            throw new BusinessException("이미 적재한 파일입니다: " + fileName);
        }
        return run(target, baseYear, fileName, user, "CSV_UPLOAD",
                () -> new Read(read(target, fileName, content), hash));
    }

    /** API 원천에서 전체 데이터를 받아 적재한다. 마지막 적재와 내용이 같으면 적재하지 않는다. */
    public LoadHistory fetch(String sourceCode, Integer baseYear, String user) {
        Target target = target(sourceCode, LoadMethod.API);
        String label = "API " + target.spec().url().substring(target.spec().url().lastIndexOf('/') + 1);
        return run(target, baseYear, label, user, "API_LOAD", () -> {
            List<RawRecord> records = read(target, null, null);
            String hash = Hashes.sha256(records.stream().map(r -> Json.object(r.original()))
                    .collect(Collectors.joining("\n")));
            if (histories.existsByFileHashAndStatus(hash, LoadStatus.SUCCESS)) {
                throw new BusinessException("마지막 적재 이후 바뀐 데이터가 없습니다.");
            }
            return new Read(records, hash);
        });
    }

    public List<LoadHistory> history() {
        return histories.findAllByOrderByLoadedAtDesc();
    }

    private record Read(List<RawRecord> records, String hash) {
    }

    private LoadHistory run(Target target, Integer baseYear, String fileName, String user, String action,
                            java.util.function.Supplier<Read> reader) {
        String sourceCode = target.registered().loader().sourceCode();
        String hash = null;
        try {
            Read read = reader.get();
            hash = read.hash();
            if (read.records().isEmpty()) {
                throw new BusinessException("데이터 행이 없습니다.");
            }
            int year = baseYear != null ? baseYear : target.registered().loader().suggestBaseYear(read.records())
                    .orElseThrow(() -> new BusinessException("기준연을 입력하세요."));
            String fileHash = hash;
            return tx.execute(status -> {
                LoadHistory history = histories.save(
                        LoadHistory.succeeded(target.item().getCode(), sourceCode, year, fileName, fileHash, user));
                LoadSummary summary = target.registered().loader()
                        .load(new LoadContext(history.getId(), sourceCode, year), read.records());
                history.record(summary);
                activateIfLatest(history);
                audit.record(user, action, sourceCode + "/" + year,
                        fileName + ", " + summary.rowCount() + "행, 중복 " + summary.duplicateCount()
                                + ", 품질 플래그 " + summary.flaggedCount());
                return history;
            });
        } catch (RuntimeException e) {
            String message = e instanceof BusinessException ? e.getMessage() : "적재 중 오류: " + e.getMessage();
            String failedHash = hash;
            tx.executeWithoutResult(status -> histories.save(LoadHistory.failed(
                    target.item().getCode(), sourceCode, baseYear, fileName, failedHash, user, message)));
            throw e instanceof BusinessException be ? be : new IllegalStateException(message, e);
        }
    }

    private Target target(String sourceCode, LoadMethod requested) {
        RegisteredLoader registered = registry.loader(sourceCode)
                .orElseThrow(() -> new BusinessException("알 수 없는 원천입니다: " + sourceCode));
        EsgItem item = items.findById(registered.module().code())
                .orElseThrow(() -> new IllegalStateException("esg_item 미등록: " + registered.module().code()));
        if (!item.isEnabled()) {
            throw new BusinessException("사용하지 않는 ESG 항목입니다: " + item.getName());
        }
        SourceSpec spec = properties.spec(sourceCode);
        LoadMethod method = spec.method() != null ? spec.method() : item.getLoadMethod();
        if (method != requested) {
            throw new BusinessException(spec.name() + "의 적재 방식은 " + method + "입니다. "
                    + (method == LoadMethod.API ? "API 적재를 실행하세요." : "파일을 업로드하세요."));
        }
        return new Target(registered, item, spec, method);
    }

    private List<RawRecord> read(Target target, String fileName, byte[] content) {
        DataIngestion ingestion = ingestions.stream()
                .filter(i -> i.method() == target.method())
                .findFirst()
                .orElseThrow(() -> new BusinessException(target.method() + " 적재는 아직 구현되지 않았습니다."));
        return ingestion.read(new IngestionRequest(target.registered().loader().sourceCode(), target.spec(),
                fileName, content));
    }

    /** 출처별로 기준연이 가장 최근인 적재 하나만 집계에 쓴다. 같은 기준연이면 나중 적재가 이긴다. */
    private void activateIfLatest(LoadHistory history) {
        boolean latest = histories.findFirstBySourceAndActiveTrue(history.getSource())
                .map(current -> history.getBaseYear() >= current.getBaseYear())
                .orElse(true);
        if (latest) {
            histories.deactivateAll(history.getSource());
            history.activate();
        }
    }
}
