package kr.fitorder.ingestion;

import java.util.Map;
import kr.fitorder.common.SourceSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 데이터 원천 초기값. 처음 시작할 때 data_source에 한 번 넣고, 이후에는 DB 값이 우선한다. */
@ConfigurationProperties(prefix = "fitorder.ingestion")
public record IngestionProperties(Map<String, SourceSpec> sources) {

    public IngestionProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }
}
