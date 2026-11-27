package kr.fitorder.ingestion;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import kr.fitorder.common.LoadMethod;
import kr.fitorder.common.SourceSpec;

/** 데이터 원천 하나의 설정. 적재 방식·API 주소·컬럼 매핑을 화면에서 바꾼다. */
@Entity
@Table(name = "data_source")
public class DataSource {

    @Id
    private String code;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadMethod method;

    private String url;

    @Column(name = "per_page", nullable = false)
    private int perPage;

    @Column(nullable = false)
    private String charset;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "data_source_column", joinColumns = @JoinColumn(name = "source_code"))
    @MapKeyColumn(name = "field")
    @Column(name = "column_name", nullable = false)
    private Map<String, String> columns = new LinkedHashMap<>();

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DataSource() {
    }

    public DataSource(String code, String itemCode, SourceSpec spec, LoadMethod method, String updatedBy) {
        this.code = code;
        this.itemCode = itemCode;
        this.name = spec.name() != null ? spec.name() : code;
        change(method, spec.url(), spec.perPage(), spec.charset(), spec.columns(), updatedBy);
    }

    public void change(LoadMethod method, String url, int perPage, String charset, Map<String, String> columns,
                       String updatedBy) {
        this.method = method;
        this.url = url;
        this.perPage = perPage;
        this.charset = charset;
        this.columns.clear();
        this.columns.putAll(columns);
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public SourceSpec toSpec() {
        return new SourceSpec(name, method, url, perPage, charset, new LinkedHashMap<>(columns));
    }

    public String getCode() {
        return code;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getName() {
        return name;
    }

    public LoadMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public int getPerPage() {
        return perPage;
    }

    public String getCharset() {
        return charset;
    }

    public Map<String, String> getColumns() {
        return Map.copyOf(columns);
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
