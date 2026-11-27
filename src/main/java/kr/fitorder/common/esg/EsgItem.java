package kr.fitorder.common.esg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import kr.fitorder.common.EsgCategory;
import kr.fitorder.common.LoadMethod;

@Entity
@Table(name = "esg_item")
public class EsgItem {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EsgCategory category;

    @Column(nullable = false)
    private boolean enabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "load_method", nullable = false)
    private LoadMethod loadMethod;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected EsgItem() {
    }

    public EsgItem(String code, String name, EsgCategory category, LoadMethod loadMethod) {
        this.code = code;
        this.name = name;
        this.category = category;
        this.loadMethod = loadMethod;
        this.enabled = true;
        this.createdAt = OffsetDateTime.now();
    }

    public void change(boolean enabled, LoadMethod loadMethod) {
        this.enabled = enabled;
        this.loadMethod = loadMethod;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public EsgCategory getCategory() {
        return category;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LoadMethod getLoadMethod() {
        return loadMethod;
    }
}
