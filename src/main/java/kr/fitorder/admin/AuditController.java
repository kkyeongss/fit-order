package kr.fitorder.admin;

import java.util.List;
import java.util.Map;
import kr.fitorder.admin.AuditQueryService.Row;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 변경 기록 조회(부서장·관리자). 기록은 서버가 남기며 이 API로는 바꿀 수 없다. */
@RestController
@RequestMapping("/api/audit")
public class AuditController {

    private final AuditQueryService service;

    public AuditController(AuditQueryService service) {
        this.service = service;
    }

    @GetMapping
    public List<Row> recent(@RequestParam(defaultValue = "200") int limit,
                            @RequestParam(required = false) String action,
                            @RequestParam(required = false) String username) {
        return service.recent(limit, action, username);
    }

    /** 필터 선택지. */
    @GetMapping("/filters")
    public Map<String, List<String>> filters() {
        return Map.of("actions", service.actions(), "usernames", service.usernames());
    }
}
