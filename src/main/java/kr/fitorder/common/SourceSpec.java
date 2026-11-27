package kr.fitorder.common;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 원천 데이터 하나의 읽기 설정(application.yml의 fitorder.ingestion.sources).
 *
 * @param name    화면에 보일 이름
 * @param method  적재 방식. 비우면 ESG 항목의 적재 방식(esg_item.load_method)을 따른다
 * @param url     API 적재 주소
 * @param perPage API 한 번에 받을 행 수
 * @param charset 업로드 파일 인코딩
 * @param columns 표준 필드명 → 원본 컬럼명
 */
public record SourceSpec(String name, LoadMethod method, String url, Integer perPage, String charset,
                         Map<String, String> columns) {

    public SourceSpec {
        charset = charset == null || charset.isBlank() ? "CP949" : charset;
        perPage = perPage == null ? 1000 : perPage;
        columns = columns == null ? Map.of() : Map.copyOf(columns);
    }

    /** 매핑된 원본 컬럼 중 실제 컬럼에 없는 것. */
    public List<String> missingColumns(Collection<String> actualColumns) {
        return columns.values().stream().filter(c -> !actualColumns.contains(c)).toList();
    }

    /** 업로드 파일 설정만 있는 원천. */
    public static SourceSpec upload(String name, String charset, Map<String, String> columns) {
        return new SourceSpec(name, null, null, null, charset, columns);
    }
}
