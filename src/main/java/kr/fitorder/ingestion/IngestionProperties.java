package kr.fitorder.ingestion;

import java.util.Map;
import kr.fitorder.common.BusinessException;
import kr.fitorder.common.SourceSpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fitorder.ingestion")
public record IngestionProperties(Map<String, SourceSpec> sources) {

    public IngestionProperties {
        sources = sources == null ? Map.of() : Map.copyOf(sources);
    }

    public SourceSpec spec(String sourceCode) {
        SourceSpec spec = sources.get(sourceCode);
        if (spec == null) {
            throw new BusinessException("읽기 설정이 없는 원천입니다: " + sourceCode);
        }
        return spec;
    }
}
