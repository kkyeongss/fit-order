package kr.fitorder.module.uniform.api;

import java.util.List;
import kr.fitorder.module.uniform.cleansing.SizeMappingService;
import kr.fitorder.module.uniform.cleansing.SizeMappingService.Change;
import kr.fitorder.module.uniform.cleansing.SizeMappingService.SizeRow;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/size-mappings")
public class SizeMappingController {

    private final SizeMappingService service;

    public SizeMappingController(SizeMappingService service) {
        this.service = service;
    }

    @GetMapping
    public List<SizeRow> list() {
        return service.list();
    }

    @PutMapping
    public List<SizeRow> update(@RequestBody List<Change> changes, Authentication auth) {
        service.update(changes, auth.getName());
        return service.list();
    }
}
