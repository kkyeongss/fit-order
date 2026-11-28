package kr.fitorder.module.uniform.api;

import java.util.List;
import kr.fitorder.module.uniform.analysis.UnitPriceService;
import kr.fitorder.module.uniform.analysis.UnitPriceService.Change;
import kr.fitorder.module.uniform.analysis.UnitPriceService.PriceView;
import kr.fitorder.module.uniform.cleansing.QualityReportService;
import kr.fitorder.module.uniform.cleansing.QualityReportService.SourceQuality;
import kr.fitorder.module.uniform.price.GreenProductService;
import kr.fitorder.module.uniform.price.MarketPriceService;
import kr.fitorder.module.uniform.price.MarketPriceService.FetchResult;
import kr.fitorder.module.uniform.price.MarketPriceService.MarketPrice;
import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 데이터 관리 화면: 품질 리포트, 단가, 조달청 시장가. */
@RestController
@RequestMapping("/api/admin")
public class UniformAdminController {

    private final QualityReportService quality;
    private final UnitPriceService prices;
    private final MarketPriceService market;
    private final GreenProductService green;

    public UniformAdminController(QualityReportService quality, UnitPriceService prices, MarketPriceService market,
                                  GreenProductService green) {
        this.quality = quality;
        this.prices = prices;
        this.market = market;
        this.green = green;
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

    /** 품목군별 조달청 쇼핑몰 시장가와 환경표지제품 수. */
    @GetMapping("/market-prices")
    public List<MarketPrice> marketPrices() {
        return market.list();
    }

    /** 조달청 쇼핑몰 API에서 최근 1년 근무복 계약가격을 받아 시장가를 바꾼다. */
    @PostMapping("/market-prices/fetch")
    public FetchResult fetchMarketPrices(Authentication auth) {
        return market.fetch(auth.getName());
    }

    /** 망분리 환경: 같은 항목(물품규격명, 계약가격금액 등)을 CSV로 올린다. */
    @PostMapping("/market-prices/upload")
    public FetchResult uploadMarketPrices(@RequestParam MultipartFile file, Authentication auth) throws IOException {
        return market.upload(file.getBytes(), auth.getName());
    }

    /** 환경표지 인증 근무복 목록. */
    @GetMapping("/green-products")
    public List<GreenProductService.ProductView> greenProducts() {
        return green.list();
    }

    /** 녹색제품 정보조회 API에서 환경표지 근무복을 받아 목록을 바꾼다. */
    @PostMapping("/green-products/fetch")
    public GreenProductService.FetchResult fetchGreenProducts(Authentication auth) {
        return green.fetch(auth.getName());
    }

    /** 망분리 환경: 녹색제품 목록을 CSV로 올린다. */
    @PostMapping("/green-products/upload")
    public GreenProductService.FetchResult uploadGreenProducts(@RequestParam MultipartFile file, Authentication auth)
            throws IOException {
        return green.upload(file.getBytes(), auth.getName());
    }
}
