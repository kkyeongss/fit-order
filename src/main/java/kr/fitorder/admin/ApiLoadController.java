package kr.fitorder.admin;

import kr.fitorder.admin.UploadController.LoadHistoryResponse;
import kr.fitorder.ingestion.LoadService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 원천(정원현황 등)을 지금 받아 적재한다. */
@RestController
public class ApiLoadController {

    private final LoadService loadService;

    public ApiLoadController(LoadService loadService) {
        this.loadService = loadService;
    }

    @PostMapping("/api/admin/sources/{source}/fetch")
    @ResponseStatus(HttpStatus.CREATED)
    public LoadHistoryResponse fetch(@PathVariable String source,
                                     @RequestParam(required = false) Integer baseYear,
                                     Authentication auth) {
        return LoadHistoryResponse.of(loadService.fetch(source, baseYear, auth.getName()));
    }
}
