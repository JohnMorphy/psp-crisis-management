package pl.lublin.dashboard.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "entity_import_batch")
public class EntityImportBatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_code", nullable = false, length = 50)
    private String sourceCode;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "records_total")
    private Integer recordsTotal;

    @Column(name = "records_new")
    private Integer recordsNew;

    @Column(name = "records_updated")
    private Integer recordsUpdated;

    @Column(name = "records_skipped")
    private Integer recordsSkipped;

    @Column(name = "error_log", columnDefinition = "TEXT")
    private String errorLog;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getRecordsTotal() { return recordsTotal; }
    public void setRecordsTotal(Integer recordsTotal) { this.recordsTotal = recordsTotal; }

    public Integer getRecordsNew() { return recordsNew; }
    public void setRecordsNew(Integer recordsNew) { this.recordsNew = recordsNew; }

    public Integer getRecordsUpdated() { return recordsUpdated; }
    public void setRecordsUpdated(Integer recordsUpdated) { this.recordsUpdated = recordsUpdated; }

    public Integer getRecordsSkipped() { return recordsSkipped; }
    public void setRecordsSkipped(Integer recordsSkipped) { this.recordsSkipped = recordsSkipped; }

    public String getErrorLog() { return errorLog; }
    public void setErrorLog(String errorLog) { this.errorLog = errorLog; }
}
