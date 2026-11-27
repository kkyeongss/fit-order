package kr.fitorder.ingestion;

import java.util.List;
import kr.fitorder.admin.AuditService;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.DataIngestion;
import kr.fitorder.common.Hashes;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.LoadContext;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.LoadSummary;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.esg.EsgItem;
import kr.fitorder.common.esg.EsgItemRepository;
import kr.fitorder.common.esg.EsgModuleRegistry;
import kr.fitorder.common.esg.EsgModuleRegistry.RegisteredLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 적재 흐름: 원천 확인 → 같은 파일 거르기 → 공통 레코드로 읽기 → 항목 적재기로 저장 → 활성 적재 갱신.
 * 적재 방식(업로드·API·배치)이 바뀌어도 이 흐름과 항목 적재기는 그대로다.
 */
@Service
public class LoadService {

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
     * 파일 하나를 적재한다.
     *
     * @param baseYear 데이터 기준연. null이면 적재기가 제안하는 값(근무복: 파일 안 최대 주문일의 연도)
     */
    public LoadHistory upload(String sourceCode, Integer baseYear, String fileName, byte[] content, String user) {
        RegisteredLoader target = registry.loader(sourceCode)
                .orElseThrow(() -> new BusinessException("알 수 없는 원천입니다: " + sourceCode));
        EsgItem item = items.findById(target.module().code())
                .orElseThrow(() -> new IllegalStateException("esg_item 미등록: " + target.module().code()));
        if (!item.isEnabled()) {
            throw new BusinessException("사용하지 않는 ESG 항목입니다: " + item.getName());
        }
        if (item.getLoadMethod() != LoadMethod.UPLOAD) {
            throw new BusinessException(item.getName() + " 항목의 적재 방식이 " + item.getLoadMethod() + "입니다. 업로드로 바꾼 뒤 올리세요.");
        }

        String fileHash = Hashes.sha256(content);
        if (histories.existsByFileHashAndStatus(fileHash, LoadStatus.SUCCESS)) {
            throw new BusinessException("이미 적재한 파일입니다: " + fileName);
        }

        try {
            List<RawRecord> records = ingestion(item.getLoadMethod())
                    .read(new IngestionRequest(sourceCode, properties.spec(sourceCode), fileName, content));
            if (records.isEmpty()) {
                throw new BusinessException("데이터 행이 없습니다.");
            }
            int year = baseYear != null ? baseYear : target.loader().suggestBaseYear(records)
                    .orElseThrow(() -> new BusinessException("기준연을 입력하세요."));

            return tx.execute(status -> {
                LoadHistory history = histories.save(
                        LoadHistory.succeeded(item.getCode(), sourceCode, year, fileName, fileHash, user));
                LoadSummary summary = target.loader().load(new LoadContext(history.getId(), sourceCode, year), records);
                history.record(summary);
                activateIfLatest(history);
                audit.record(user, "CSV_UPLOAD", sourceCode + "/" + year,
                        fileName + ", " + summary.rowCount() + "행, 중복 " + summary.duplicateCount()
                                + ", 품질 플래그 " + summary.flaggedCount());
                return history;
            });
        } catch (RuntimeException e) {
            String message = e instanceof BusinessException ? e.getMessage() : "적재 중 오류: " + e.getMessage();
            tx.executeWithoutResult(status -> histories.save(
                    LoadHistory.failed(item.getCode(), sourceCode, baseYear, fileName, fileHash, user, message)));
            throw e instanceof BusinessException be ? be : new IllegalStateException(message, e);
        }
    }

    public List<LoadHistory> history() {
        return histories.findAllByOrderByLoadedAtDesc();
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

    private DataIngestion ingestion(LoadMethod method) {
        return ingestions.stream()
                .filter(i -> i.method() == method)
                .findFirst()
                .orElseThrow(() -> new BusinessException(method + " 적재는 아직 구현되지 않았습니다."));
    }
}
