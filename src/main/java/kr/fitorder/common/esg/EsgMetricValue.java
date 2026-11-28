package kr.fitorder.common.esg;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import kr.fitorder.common.Basis;

/** 미리 계산해 둔 지표 값. 화면은 이 값만 읽는다. */
@Entity
@Table(name = "esg_metric_value")
public class EsgMetricValue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "metric_code", nullable = false)
    private String metricCode;

    @Column(nullable = false)
    private String period;

    @Column(nullable = false)
    private String dimension;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Basis basis;

    private BigDecimal value;

    @Column(name = "calculated_at", nullable = false)
    private OffsetDateTime calculatedAt;

    protected EsgMetricValue() {
    }

    public EsgMetricValue(String metricCode, String period, String dimension, Basis basis, BigDecimal value,
                          OffsetDateTime calculatedAt) {
        this.metricCode = metricCode;
        this.period = period;
        this.dimension = dimension;
        this.basis = basis;
        this.value = value;
        this.calculatedAt = calculatedAt;
    }

    public String getMetricCode() {
        return metricCode;
    }

    public String getPeriod() {
        return period;
    }

    public String getDimension() {
        return dimension;
    }

    public Basis getBasis() {
        return basis;
    }

    public BigDecimal getValue() {
        return value;
    }

    public OffsetDateTime getCalculatedAt() {
        return calculatedAt;
    }
}
