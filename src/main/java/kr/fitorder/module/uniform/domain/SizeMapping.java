package kr.fitorder.module.uniform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/** 출처별 원본 사이즈 코드 → 표준 사이즈. 매핑이 없으면 원본 코드를 그대로 보여준다. */
@Entity
@Table(name = "size_mapping")
public class SizeMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String source;

    @Column(name = "raw_size", nullable = false)
    private String rawSize;

    @Column(name = "standard_size", nullable = false)
    private String standardSize;

    @Column(name = "updated_by", nullable = false)
    private String updatedBy;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected SizeMapping() {
    }

    public SizeMapping(String source, String rawSize, String standardSize, String updatedBy) {
        this.source = source;
        this.rawSize = rawSize;
        change(standardSize, updatedBy);
    }

    public void change(String standardSize, String updatedBy) {
        this.standardSize = standardSize;
        this.updatedBy = updatedBy;
        this.updatedAt = OffsetDateTime.now();
    }

    public String getSource() {
        return source;
    }

    public String getRawSize() {
        return rawSize;
    }

    public String getStandardSize() {
        return standardSize;
    }
}
