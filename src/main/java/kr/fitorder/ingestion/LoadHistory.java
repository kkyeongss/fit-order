package kr.fitorder.ingestion;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import kr.fitorder.common.LoadSummary;

@Entity
@Table(name = "load_history")
public class LoadHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "item_code", nullable = false)
    private String itemCode;

    @Column(nullable = false)
    private String source;

    @Column(name = "base_year")
    private Integer baseYear;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "file_hash")
    private String fileHash;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "flagged_count", nullable = false)
    private int flaggedCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LoadStatus status;

    private String message;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "loaded_by", nullable = false)
    private String loadedBy;

    @Column(name = "loaded_at", nullable = false)
    private OffsetDateTime loadedAt;

    protected LoadHistory() {
    }

    private LoadHistory(String itemCode, String source, Integer baseYear, String fileName, String fileHash,
                        LoadStatus status, String loadedBy) {
        this.itemCode = itemCode;
        this.source = source;
        this.baseYear = baseYear;
        this.fileName = fileName;
        this.fileHash = fileHash;
        this.status = status;
        this.loadedBy = loadedBy;
        this.loadedAt = OffsetDateTime.now();
    }

    public static LoadHistory succeeded(String itemCode, String source, int baseYear, String fileName,
                                        String fileHash, String loadedBy) {
        return new LoadHistory(itemCode, source, baseYear, fileName, fileHash, LoadStatus.SUCCESS, loadedBy);
    }

    public static LoadHistory failed(String itemCode, String source, Integer baseYear, String fileName,
                                     String fileHash, String loadedBy, String message) {
        LoadHistory history = new LoadHistory(itemCode, source, baseYear, fileName, fileHash, LoadStatus.FAILED, loadedBy);
        history.message = message;
        return history;
    }

    public void record(LoadSummary summary) {
        this.rowCount = summary.rowCount();
        this.duplicateCount = summary.duplicateCount();
        this.flaggedCount = summary.flaggedCount();
    }

    public void activate() {
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public String getItemCode() {
        return itemCode;
    }

    public String getSource() {
        return source;
    }

    public Integer getBaseYear() {
        return baseYear;
    }

    public String getFileName() {
        return fileName;
    }

    public int getRowCount() {
        return rowCount;
    }

    public int getDuplicateCount() {
        return duplicateCount;
    }

    public int getFlaggedCount() {
        return flaggedCount;
    }

    public LoadStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }

    public boolean isActive() {
        return active;
    }

    public String getLoadedBy() {
        return loadedBy;
    }

    public OffsetDateTime getLoadedAt() {
        return loadedAt;
    }
}
