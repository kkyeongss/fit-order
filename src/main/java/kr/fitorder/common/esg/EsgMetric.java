package kr.fitorder.common.esg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "esg_metric")
public class EsgMetric {

    @Id
    private String code;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String unit;

    protected EsgMetric() {
    }

    public EsgMetric(String code, String itemCode, String name, String unit) {
        this.code = code;
        this.itemCode = itemCode;
        this.name = name;
        this.unit = unit;
    }

    /** 코드의 이름·단위가 바뀌면 따라간다. */
    public void update(String name, String unit) {
        this.name = name;
        this.unit = unit;
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

    public String getUnit() {
        return unit;
    }
}
