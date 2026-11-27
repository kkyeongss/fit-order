package kr.fitorder.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 외부 API 인증 정보. 값은 .env 또는 환경변수에서 온다. */
@ConfigurationProperties(prefix = "fitorder.external")
public record ExternalApiProperties(DataGoKr dataGoKr) {

    public record DataGoKr(String serviceKey) {
    }

    public ExternalApiProperties {
        dataGoKr = dataGoKr == null ? new DataGoKr(null) : dataGoKr;
    }
}
