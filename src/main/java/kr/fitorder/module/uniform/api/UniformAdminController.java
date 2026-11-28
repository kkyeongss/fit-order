package kr.fitorder.module.uniform.api;

import java.util.List;
import kr.fitorder.module.uniform.analysis.UnitPriceService;
import kr.fitorder.module.uniform.analysis.UnitPriceService.Change;
import kr.fitorder.module.uniform.analysis.UnitPriceService.PriceView;
import kr.fitorder.module.uniform.cleansing.QualityReportService;
import kr.fitorder.module.uniform.cleansing.QualityReportService.SourceQuality;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 데이터 관리 화면: 품질 리포트, 단가. */
@RestController
@RequestMapping("/api/admin")
public class UniformAdminController {

    private final QualityReportService quality;
    private final UnitPriceService prices;

    public UniformAdminController(QualityReportService quality, UnitPriceService prices) {
        this.quality = quality;
        this.prices = prices;
    }

    @GetMapping("/quality")
    public List<SourceQuality> quality() {
        return quality.report();
    }

    @GetMapping("/prices")
    public List<PriceView> prices() {
        return prices.list();
    }

    /** 단가 입력. 오늘부터 적용되고, 절감액 지표가 다시 계산된다. */
    @PutMapping("/prices")
    public List<PriceView> updatePrices(@RequestBody List<Change> changes, Authentication auth) {
        return prices.update(changes, auth.getName());
    }
}
