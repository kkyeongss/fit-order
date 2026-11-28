package kr.fitorder.module.uniform.api;

import java.util.List;
import kr.fitorder.common.Basis;
import kr.fitorder.module.uniform.analysis.DuplicateExportService;
import kr.fitorder.module.uniform.analysis.PlanService;
import kr.fitorder.module.uniform.analysis.PlanService.Compare;
import kr.fitorder.module.uniform.analysis.PlanService.PlanSummary;
import kr.fitorder.module.uniform.analysis.PlanService.SaveRequest;
import kr.fitorder.module.uniform.analysis.StockService;
import kr.fitorder.module.uniform.analysis.StockService.Change;
import kr.fitorder.module.uniform.analysis.StockService.StockRow;
import kr.fitorder.module.uniform.analysis.UnitPriceService;
import kr.fitorder.module.uniform.analysis.UnitPriceService.PriceView;
import kr.fitorder.module.uniform.analysis.UniformReportService;
import kr.fitorder.module.uniform.analysis.UniformReportService.SavingsReport;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자(USER)가 직접 하는 일: 단가 입력, 재고 입력, 발주안 저장·비교.
 */
@RestController
@RequestMapping("/api/uniform")
public class UniformWorkController {

    private final UnitPriceService prices;
    private final StockService stock;
    private final UniformReportService reports;
    private final DuplicateExportService duplicates;
    private final PlanService plans;

    public UniformWorkController(UnitPriceService prices, StockService stock, UniformReportService reports,
                                 DuplicateExportService duplicates, PlanService plans) {
        this.prices = prices;
        this.stock = stock;
        this.reports = reports;
        this.duplicates = duplicates;
        this.plans = plans;
    }

    // 단가 ------------------------------------------------------------------------

    @GetMapping("/prices")
    public List<PriceView> prices() {
        return prices.list();
    }

    @PutMapping("/prices")
    public List<PriceView> updatePrices(@RequestBody List<UnitPriceService.Change> changes, Authentication auth) {
        return prices.update(changes, auth.getName());
    }

    /** 직접 입력한 단가를 지우고 시장가·추정 단가로 되돌린다. */
    @DeleteMapping("/prices/{group}")
    public List<PriceView> clearPrice(@PathVariable UniformItemGroup group, Authentication auth) {
        return prices.clear(group, auth.getName());
    }

    // 재고 ------------------------------------------------------------------------

    @GetMapping("/stock")
    public List<StockRow> stock() {
        return stock.list();
    }

    @PutMapping("/stock")
    public List<StockRow> updateStock(@RequestBody List<Change> changes, Authentication auth) {
        return stock.update(changes, auth.getName());
    }

    // 절감 효과 근거 -----------------------------------------------------------------

    @GetMapping("/savings")
    public SavingsReport savings(@RequestParam(defaultValue = "dedup") Basis basis) {
        return reports.savings(basis);
    }

    // 중복 행 목록(기관 확인 요청용) ---------------------------------------------------

    @GetMapping("/duplicates/export")
    public ResponseEntity<byte[]> duplicatesExport() {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''"
                        + java.net.URLEncoder.encode("근무복_완전중복행.csv", java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                .body(duplicates.csv());
    }

    // 저장한 발주안 --------------------------------------------------------------------

    @GetMapping("/plans")
    public List<PlanSummary> plans() {
        return plans.list();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    public PlanSummary savePlan(@RequestBody SaveRequest request, Authentication auth) {
        return plans.save(request, auth.getName());
    }

    @GetMapping("/plans/{id}/compare")
    public Compare compare(@PathVariable long id) {
        return plans.compare(id);
    }

    @DeleteMapping("/plans/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePlan(@PathVariable long id, Authentication auth) {
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        plans.delete(id, auth.getName(), admin);
    }
}
