package kr.fitorder.admin;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import kr.fitorder.ingestion.LoadHistory;
import kr.fitorder.ingestion.LoadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/uploads")
public class UploadController {

    public record LoadHistoryResponse(long id, String itemCode, String source, Integer baseYear, String fileName,
                                      int rowCount, int duplicateCount, int flaggedCount, String status,
                                      String message, boolean active, String loadedBy, OffsetDateTime loadedAt) {

        static LoadHistoryResponse of(LoadHistory h) {
            return new LoadHistoryResponse(h.getId(), h.getItemCode(), h.getSource(), h.getBaseYear(), h.getFileName(),
                    h.getRowCount(), h.getDuplicateCount(), h.getFlaggedCount(), h.getStatus().name(),
                    h.getMessage(), h.isActive(), h.getLoadedBy(), h.getLoadedAt());
        }
    }

    private final LoadService loadService;

    public UploadController(LoadService loadService) {
        this.loadService = loadService;
    }

    /** CSV 업로드·적재. baseYear를 비우면 파일 안 최대 주문일의 연도를 쓴다. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoadHistoryResponse upload(@RequestParam String source,
                                      @RequestParam(required = false) Integer baseYear,
                                      @RequestParam MultipartFile file,
                                      Authentication auth) throws IOException {
        return LoadHistoryResponse.of(
                loadService.upload(source, baseYear, file.getOriginalFilename(), file.getBytes(), auth.getName()));
    }

    @GetMapping
    public List<LoadHistoryResponse> history() {
        return loadService.history().stream().map(LoadHistoryResponse::of).toList();
    }
}
