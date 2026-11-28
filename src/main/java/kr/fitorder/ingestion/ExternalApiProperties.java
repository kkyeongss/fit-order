package kr.fitorder.ingestion;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import kr.fitorder.common.BusinessException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 외부 API 인증 정보. 값은 .env 또는 환경변수에서 온다. */
@ConfigurationProperties(prefix = "fitorder.external")
public record ExternalApiProperties(DataGoKr dataGoKr) {

    /** 공공데이터포털 인증키 하나로 활용신청한 모든 API를 부른다. */
    public record DataGoKr(String serviceKey) {

        public boolean configured() {
            return serviceKey != null && !serviceKey.isBlank();
        }

        /** 포털은 Encoding 키(%2B 등 포함)와 Decoding 키를 함께 준다. 어느 쪽이든 원래 값으로 바꿔 한 번만 인코딩하게 한다. */
        public String decodedKey() {
            if (!configured()) {
                throw new BusinessException("공공데이터포털 인증키(DATA_GO_KR_SERVICE_KEY)가 설정되지 않았습니다.");
            }
            String key = serviceKey.strip();
            return key.contains("%") ? URLDecoder.decode(key, StandardCharsets.UTF_8) : key;
        }
    }

    public ExternalApiProperties {
        dataGoKr = dataGoKr == null ? new DataGoKr(null) : dataGoKr;
    }
}
