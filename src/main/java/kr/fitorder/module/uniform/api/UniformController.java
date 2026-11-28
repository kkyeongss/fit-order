package kr.fitorder.module.uniform.api;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.Basis;
import kr.fitorder.module.uniform.analysis.Backtester;
import kr.fitorder.module.uniform.analysis.RecommendationService;
import kr.fitorder.module.uniform.analysis.RecommendationService.Recommendation;
import kr.fitorder.module.uniform.analysis.RecommendationService.Request;
import kr.fitorder.module.uniform.analysis.UniformReportService;
import kr.fitorder.module.uniform.analysis.UniformReportService.HeadcountView;
import kr.fitorder.module.uniform.analysis.UniformReportService.SizeAnalysis;
import kr.fitorder.module.uniform.analysis.UniformReportService.Summary;
import kr.fitorder.module.uniform.domain.UniformItemGroup;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 근무복 화면 API: 대시보드·사이즈 분석·발주 추천. 집계는 모두 basis=dedup|raw(기본 dedup). */
@RestController
@RequestMapping("/api/uniform")
public class UniformController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final UniformReportService reports;
    private final RecommendationService recommendations;

    public UniformController(UniformReportService reports, RecommendationService recommendations) {
        this.reports = reports;
        this.recommendations = recommendations;
    }

    /** 연도·월·품목별 주문량, 중복 구분, 1인당 지급량. */
    @GetMapping("/summary")
    public Summary summary(@RequestParam(defaultValue = "dedup") Basis basis) {
        return reports.summary(basis);
    }

    /** 품목군별 사이즈 비율, 두 구간 비교, 미스매치. */
    @GetMapping("/sizes")
    public List<SizeAnalysis> sizes(@RequestParam(defaultValue = "dedup") Basis basis,
                                    @RequestParam(required = false) UniformItemGroup group) {
        return reports.sizes(basis, group);
    }

    /** 연도별 정원. 추천 인원 기본값은 latest. */
    @GetMapping("/headcount")
    public HeadcountView headcount() {
        return reports.headcount();
    }

    @PostMapping("/recommendation")
    public Recommendation recommend(@RequestBody Request request) {
        return recommendations.recommend(request);
    }

    /** 발주표 엑셀. 추천과 같은 입력값을 쿼리 파라미터로 받아 다시 계산한다. */
    @GetMapping("/recommendation/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Integer year,
                                         @RequestParam(required = false) List<UniformItemGroup> groups,
                                         @RequestParam(required = false) Integer headcount,
                                         @RequestParam(defaultValue = "dedup") Basis basis,
                                         @RequestParam(required = false) String totals) {
        Recommendation rec = recommendations.recommend(new Request(year, groups, headcount, basis, parseTotals(totals)));
        String fileName = URLEncoder.encode(rec.year() + "년_근무복_발주표.xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + fileName)
                .body(RecommendationExcel.write(rec));
    }

    /** 'SHIRT:500,JACKET:20' 형식의 총량 직접 지정값. */
    static Map<UniformItemGroup, Integer> parseTotals(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Map<UniformItemGroup, Integer> result = new LinkedHashMap<>();
        for (String part : text.split(",")) {
            String[] kv = part.strip().split(":");
            try {
                result.put(UniformItemGroup.valueOf(kv[0].strip()), Integer.parseInt(kv[1].strip()));
            } catch (RuntimeException e) {
                throw new BusinessException("총량 지정 형식이 올바르지 않습니다(예: SHIRT:500,JACKET:20): " + part);
            }
        }
        return result;
    }

    /** 추천 로직 백테스트: 기준선(직전 1년, 직전 2년 평균)과 비교. */
    @GetMapping("/backtest")
    public Backtester.Result backtest(@RequestParam(defaultValue = "dedup") Basis basis) {
        return recommendations.backtest(basis);
    }
}
