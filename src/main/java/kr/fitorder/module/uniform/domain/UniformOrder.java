package kr.fitorder.module.uniform.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import kr.fitorder.common.QualityFlag;

/** 근무복 주문 원본 한 행. 두 CSV 모두 여기에 들어가고 source로 구분한다. */
@Entity
@Table(name = "uniform_order")
public class UniformOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "load_id", nullable = false)
    private long loadId;

    @Column(nullable = false)
    private String source;

    @Column(name = "base_year", nullable = false)
    private int baseYear;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "order_date")
    private LocalDate orderDate;

    @Column(name = "item_code")
    private String itemCode;

    @Column(name = "raw_item")
    private String rawItem;

    private String item;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_group", nullable = false)
    private UniformItemGroup itemGroup;

    private String season;

    @Column(name = "raw_size")
    private String rawSize;

    private Integer quantity;

    @Column(name = "row_hash", nullable = false)
    private String rowHash;

    @Column(nullable = false)
    private boolean duplicate;

    @Column(name = "quality_flags", nullable = false)
    private String qualityFlags = "";

    @Column(name = "raw_values", nullable = false)
    private String rawValues;

    protected UniformOrder() {
    }

    public UniformOrder(long loadId, String source, int baseYear, int lineNo, LocalDate orderDate, String itemCode,
                        String rawItem, String item, UniformItemGroup itemGroup, String season, String rawSize,
                        Integer quantity, String rowHash, String rawValues) {
        this.loadId = loadId;
        this.source = source;
        this.baseYear = baseYear;
        this.lineNo = lineNo;
        this.orderDate = orderDate;
        this.itemCode = itemCode;
        this.rawItem = rawItem;
        this.item = item;
        this.itemGroup = itemGroup;
        this.season = season;
        this.rawSize = rawSize;
        this.quantity = quantity;
        this.rowHash = rowHash;
        this.rawValues = rawValues;
    }

    public void markDuplicate() {
        this.duplicate = true;
    }

    public void addFlag(QualityFlag flag) {
        Set<QualityFlag> flags = flags();
        if (flags.add(flag)) {
            this.qualityFlags = flags.stream().map(Enum::name).collect(Collectors.joining(","));
        }
    }

    public boolean hasFlag(QualityFlag flag) {
        return flags().contains(flag);
    }

    public Set<QualityFlag> flags() {
        Set<QualityFlag> flags = EnumSet.noneOf(QualityFlag.class);
        if (!qualityFlags.isEmpty()) {
            Arrays.stream(qualityFlags.split(",")).map(QualityFlag::valueOf).forEach(flags::add);
        }
        return flags;
    }

    public Long getId() {
        return id;
    }

    public long getLoadId() {
        return loadId;
    }

    public String getSource() {
        return source;
    }

    public int getBaseYear() {
        return baseYear;
    }

    public int getLineNo() {
        return lineNo;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getRawItem() {
        return rawItem;
    }

    public String getItem() {
        return item;
    }

    public UniformItemGroup getItemGroup() {
        return itemGroup;
    }

    public String getSeason() {
        return season;
    }

    public String getRawSize() {
        return rawSize;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public String getRowHash() {
        return rowHash;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public String getRawValues() {
        return rawValues;
    }
}
