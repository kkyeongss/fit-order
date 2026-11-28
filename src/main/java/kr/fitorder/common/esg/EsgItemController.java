package kr.fitorder.common.esg;

import java.util.List;
import kr.fitorder.common.Basis;
import kr.fitorder.common.esg.EsgItemService.Change;
import kr.fitorder.common.esg.EsgItemService.ItemView;
import kr.fitorder.common.esg.EsgItemService.MetricValues;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** ESG 항목 API. 항목을 가르는 조건문 없이, 등록된 모든 모듈이 같은 경로로 나온다. */
@RestController
@RequestMapping("/api/esg/items")
public class EsgItemController {

    private final EsgItemService service;

    public EsgItemController(EsgItemService service) {
        this.service = service;
    }

    @GetMapping
    public List<ItemView> list() {
        return service.list();
    }

    /** 사용 여부·적재 방식 변경 (ADMIN). */
    @PutMapping("/{code}")
    public ItemView update(@PathVariable String code, @RequestBody Change change, Authentication auth) {
        return service.update(code, change, auth.getName());
    }

    @GetMapping("/{code}/metrics")
    public List<MetricValues> metrics(@PathVariable String code,
                                      @RequestParam(defaultValue = "dedup") Basis basis,
                                      @RequestParam(required = false) String period) {
        return service.metrics(code, basis, period);
    }
}
