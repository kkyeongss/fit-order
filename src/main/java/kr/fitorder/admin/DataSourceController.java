package kr.fitorder.admin;

import java.io.IOException;
import java.util.List;
import kr.fitorder.admin.UploadController.LoadHistoryResponse;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.ingestion.DataSourceService;
import kr.fitorder.ingestion.DataSourceService.Preview;
import kr.fitorder.ingestion.DataSourceService.SourceView;
import kr.fitorder.ingestion.DataSourceService.Update;
import kr.fitorder.ingestion.LoadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 데이터 원천 관리 (데이터 관리 화면의 '원천' 탭). */
@RestController
@RequestMapping("/api/admin/sources")
public class DataSourceController {

    private final DataSourceService sources;
    private final LoadService loadService;

    public DataSourceController(DataSourceService sources, LoadService loadService) {
        this.sources = sources;
        this.loadService = loadService;
    }

    @GetMapping
    public List<SourceView> list() {
        return sources.list();
    }

    @PutMapping("/{code}")
    public SourceView update(@PathVariable String code, @RequestBody Update update, Authentication auth) {
        return sources.update(code, update, auth.getName());
    }

    /**
     * 원천을 읽어 컬럼과 앞쪽 행을 보여준다(적재 안 함). 저장 전 설정을 시험하려면 method·url·charset을 함께 보낸다.
     * 컬럼 매핑 시험은 저장된 매핑 기준이다.
     */
    @PostMapping("/{code}/preview")
    public Preview preview(@PathVariable String code,
                           @RequestParam(required = false) LoadMethod method,
                           @RequestParam(required = false) String url,
                           @RequestParam(required = false) String charset,
                           @RequestPart(required = false) MultipartFile file) throws IOException {
        Update draft = method == null && url == null && charset == null ? null
                : new Update(method, url, null, charset, null);
        return sources.preview(code, draft, file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getBytes());
    }

    /** API 원천을 지금 받아 적재한다. */
    @PostMapping("/{code}/fetch")
    @ResponseStatus(HttpStatus.CREATED)
    public LoadHistoryResponse fetch(@PathVariable String code,
                                     @RequestParam(required = false) Integer baseYear,
                                     Authentication auth) {
        return LoadHistoryResponse.of(loadService.fetch(code, baseYear, auth.getName()));
    }
}
