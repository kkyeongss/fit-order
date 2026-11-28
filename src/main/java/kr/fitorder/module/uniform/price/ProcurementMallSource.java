package kr.fitorder.module.uniform.price;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.ExternalSource;
import kr.fitorder.ingestion.ExternalApiProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 조달청_나라장터쇼핑몰 품목정보 서비스(공공데이터포털 15129471)에서 근무복 계약상품을 받는다.
 * 참고자료 v1.3: getMASCntrctPrdctInfoList 등, 등록일시 범위 최대 1년, JSON 응답 response.body.items.
 * 망분리 환경에서는 MarketItemCsvReader로 같은 항목을 파일로 받는다.
 */
@Component
public class ProcurementMallSource implements ExternalSource<MarketItem> {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() {
    };
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
    private static final int PAGE_SIZE = 100;   // 이보다 크게 요청하면 10건만 온다
    private static final int MAX_PASSES = 3;

    private final MarketPriceProperties props;
    private final ExternalApiProperties keys;
    private final RestClient client = RestClient.create();

    public ProcurementMallSource(MarketPriceProperties props, ExternalApiProperties keys) {
        this.props = props;
        this.keys = keys;
    }

    @Override
    public String code() {
        return "pps-shopping-mall";
    }

    /**
     * 최근 N개월 등록된 근무복 계약상품 전부(오퍼레이션 여러 개를 합치고 같은 계약은 한 번만).
     * 이 API는 페이지 순서가 고정되지 않아 같은 상품이 두 번 나오고 일부가 빠진다(2026-11 확인: 423건 중 1회 393~411건).
     * 그래서 새 상품이 더 나오지 않거나 totalCount만큼 모일 때까지 전체를 다시 읽는다(최대 MAX_PASSES번).
     */
    @Override
    public List<MarketItem> fetch() {
        String key = keys.dataGoKr().decodedKey();
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minusMonths(props.months());
        Map<String, MarketItem> items = new LinkedHashMap<>();
        for (String operation : props.operations()) {
            Map<String, MarketItem> ofOperation = new LinkedHashMap<>();
            long total = 0;
            for (int pass = 0; pass < MAX_PASSES; pass++) {
                int before = ofOperation.size();
                for (int page = 1; ; page++) {
                    Map<String, Object> body = call(operation, key, from, to, page);
                    List<Map<String, Object>> rows = rows(body);
                    rows.stream().map(ProcurementMallSource::toItem).forEach(i -> ofOperation.putIfAbsent(i.key(), i));
                    total = ((Number) body.getOrDefault("totalCount", 0)).longValue();
                    if (rows.isEmpty() || (long) page * PAGE_SIZE >= total) {
                        break;
                    }
                }
                if (ofOperation.size() >= total || ofOperation.size() == before) {
                    break;
                }
            }
            ofOperation.forEach(items::putIfAbsent);
        }
        return new ArrayList<>(items.values());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> call(String operation, String key, LocalDateTime from, LocalDateTime to, int page) {
        var uri = UriComponentsBuilder.fromUriString(props.url() + "/" + operation)
                .queryParam("ServiceKey", key)
                .queryParam("type", "json")
                .queryParam("pageNo", page)
                .queryParam("numOfRows", PAGE_SIZE)
                .queryParam("prdctClsfcNoNm", props.itemName())
                .queryParam("rgstDtBgnDt", from.format(DT))
                .queryParam("rgstDtEndDt", to.format(DT))
                .encode().build().toUri();
        Map<String, Object> json;
        try {
            json = client.get().uri(uri).retrieve().body(JSON);
        } catch (RestClientException e) {
            throw new BusinessException("조달청 쇼핑몰 API에 연결할 수 없습니다. 망분리 환경이면 시장가 파일을 올리세요.");
        }
        if (json == null) {
            throw new BusinessException("조달청 쇼핑몰 API 응답이 비어 있습니다.");
        }
        if (json.containsKey("OpenAPI_ServiceResponse")) {
            Map<String, Object> header = (Map<String, Object>) ((Map<String, Object>) json.get("OpenAPI_ServiceResponse"))
                    .getOrDefault("cmmMsgHeader", Map.of());
            throw new BusinessException("조달청 쇼핑몰 API 오류: " + header.get("returnAuthMsg")
                    + " (활용신청 승인 여부와 인증키를 확인하세요)");
        }
        Map<String, Object> response = (Map<String, Object>) json.get("response");
        Map<String, Object> header = (Map<String, Object>) response.get("header");
        if (!"00".equals(String.valueOf(header.get("resultCode")))) {
            throw new BusinessException("조달청 쇼핑몰 API 오류: " + header.get("resultMsg"));
        }
        return (Map<String, Object>) response.get("body");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> body) {
        Object items = body.get("items");
        return items instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    static MarketItem toItem(Map<String, Object> row) {
        String key = row.get("shopngCntrctNo") + "-" + row.get("shopngCntrctSno");
        return new MarketItem(key, String.valueOf(row.get("prdctSpecNm")),
                new BigDecimal(String.valueOf(row.get("cntrctPrceAmt")).strip()), str(row.get("prdctUnit")),
                MarketItem.isEco(str(row.get("dutyPurchsObjCertNm"))));
    }

    private static String str(Object v) {
        return v == null ? null : String.valueOf(v);
    }
}
