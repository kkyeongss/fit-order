package kr.fitorder.ingestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.DataIngestion;
import kr.fitorder.common.IngestionRequest;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.RawData;
import kr.fitorder.common.RawRecord;
import kr.fitorder.common.SourceSpec;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 공공데이터포털 파일데이터 Open API(api.odcloud.kr)에서 전체 행을 페이지 단위로 받는다.
 * 응답 형식: {page, perPage, totalCount, currentCount, data: [{컬럼명: 값}]}
 */
@Component
public class ApiIngestion implements DataIngestion {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {
    };

    private final ExternalApiProperties properties;
    private final RestClient client;

    public ApiIngestion(ExternalApiProperties properties) {
        this.properties = properties;
        this.client = RestClient.create();
    }

    @Override
    public LoadMethod method() {
        return LoadMethod.API;
    }

    @Override
    public RawData read(IngestionRequest request) {
        SourceSpec spec = request.spec();
        if (spec.url() == null || spec.url().isBlank()) {
            throw new BusinessException(spec.name() + "의 API 주소가 설정되지 않았습니다.");
        }
        String key = serviceKey();

        List<RawRecord> records = new ArrayList<>();
        long line = 1;
        for (int page = 1; ; page++) {
            Map<String, Object> body = fetch(spec, key, page);
            List<Map<String, Object>> data = rows(body);
            for (Map<String, Object> row : data) {
                Map<String, String> original = new LinkedHashMap<>();
                row.forEach((k, v) -> original.put(k, v == null ? null : String.valueOf(v)));
                Map<String, String> fields = new LinkedHashMap<>();
                spec.columns().forEach((field, column) -> fields.put(field, original.get(column)));
                records.add(new RawRecord(++line, original, fields));
            }
            long total = ((Number) body.getOrDefault("totalCount", 0)).longValue();
            if (data.isEmpty() || records.size() >= total) {
                break;
            }
        }
        List<String> columns = records.isEmpty() ? List.of() : List.copyOf(records.getFirst().original().keySet());
        return new RawData(columns, records);
    }

    private Map<String, Object> fetch(SourceSpec spec, String key, int page) {
        var uri = UriComponentsBuilder.fromUriString(spec.url())
                .queryParam("page", page)
                .queryParam("perPage", spec.perPage())
                .queryParam("serviceKey", key)
                .encode()
                .build()
                .toUri();
        try {
            Map<String, Object> body = client.get().uri(uri).retrieve().body(JSON_MAP);
            if (body == null) {
                throw new BusinessException("API 응답이 비어 있습니다.");
            }
            return body;
        } catch (RestClientResponseException e) {
            String hint = e.getStatusCode().value() == 401 ? " 인증키를 확인하세요(활용신청 승인 후 반영까지 시간이 걸릴 수 있음)." : "";
            throw new BusinessException("API 호출 실패(" + e.getStatusCode().value() + ")." + hint);
        } catch (RestClientException e) {
            throw new BusinessException("API에 연결할 수 없습니다. 망분리 환경이면 적재 방식을 UPLOAD로 바꾸세요.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Map<String, Object> body) {
        Object data = body.get("data");
        return data instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
    }

    private String serviceKey() {
        return properties.dataGoKr().decodedKey();
    }

    /** 인증키가 설정돼 있는지(값은 내보내지 않는다). */
    public boolean keyConfigured() {
        return properties.dataGoKr().configured();
    }
}
