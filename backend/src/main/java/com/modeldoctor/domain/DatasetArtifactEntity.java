package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "dataset_artifacts")
public class DatasetArtifactEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", length = 512, nullable = false)
    private String storagePath;

    @Column(name = "dataset_format", length = 64, nullable = false)
    private String datasetFormat; // csv, parquet, json

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "row_count")
    private Long rowCount;

    @Column(name = "column_count")
    private Integer columnCount;

    @Column(name = "column_names_json", columnDefinition = "TEXT")
    private String columnNamesJson;

    @Column(name = "dtypes_json", columnDefinition = "TEXT")
    private String dtypesJson;

    @Column(name = "schema_summary_json", columnDefinition = "TEXT")
    private String schemaSummaryJson;

    @Column(name = "status", length = 32, nullable = false)
    private String status; // READY, FAILED

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public DatasetArtifactEntity() {}

    public DatasetArtifactEntity(String id, String originalFilename, String storagePath, String datasetFormat,
                                 Long fileSize, String sha256, Long rowCount, Integer columnCount,
                                 String columnNamesJson, String dtypesJson, String schemaSummaryJson, String status,
                                 String errorMessage, Instant createdAt, Boolean isDeleted, Instant deletedAt) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.datasetFormat = datasetFormat;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.columnNamesJson = columnNamesJson;
        this.dtypesJson = dtypesJson;
        this.schemaSummaryJson = schemaSummaryJson;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.isDeleted = isDeleted != null ? isDeleted : false;
        this.deletedAt = deletedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getDatasetFormat() { return datasetFormat; }
    public void setDatasetFormat(String datasetFormat) { this.datasetFormat = datasetFormat; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public String getColumnNamesJson() { return columnNamesJson; }
    public void setColumnNamesJson(String columnNamesJson) { this.columnNamesJson = columnNamesJson; }

    public String getDtypesJson() { return dtypesJson; }
    public void setDtypesJson(String dtypesJson) { this.dtypesJson = dtypesJson; }

    public String getSchemaSummaryJson() { return schemaSummaryJson; }
    public void setSchemaSummaryJson(String schemaSummaryJson) { this.schemaSummaryJson = schemaSummaryJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Boolean getIsDeleted() { return isDeleted != null && isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String originalFilename;
        private String storagePath;
        private String datasetFormat;
        private Long fileSize;
        private String sha256;
        private Long rowCount;
        private Integer columnCount;
        private String columnNamesJson;
        private String dtypesJson;
        private String schemaSummaryJson;
        private String status = "READY";
        private String errorMessage;
        private Instant createdAt = Instant.now();
        private Boolean isDeleted = false;
        private Instant deletedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder originalFilename(String originalFilename) { this.originalFilename = originalFilename; return this; }
        public Builder storagePath(String storagePath) { this.storagePath = storagePath; return this; }
        public Builder datasetFormat(String datasetFormat) { this.datasetFormat = datasetFormat; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder sha256(String sha256) { this.sha256 = sha256; return this; }
        public Builder rowCount(Long rowCount) { this.rowCount = rowCount; return this; }
        public Builder columnCount(Integer columnCount) { this.columnCount = columnCount; return this; }
        public Builder columnNamesJson(String columnNamesJson) { this.columnNamesJson = columnNamesJson; return this; }
        public Builder dtypesJson(String dtypesJson) { this.dtypesJson = dtypesJson; return this; }
        public Builder schemaSummaryJson(String schemaSummaryJson) { this.schemaSummaryJson = schemaSummaryJson; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        public DatasetArtifactEntity build() {
            return new DatasetArtifactEntity(id, originalFilename, storagePath, datasetFormat, fileSize, sha256,
                    rowCount, columnCount, columnNamesJson, dtypesJson, schemaSummaryJson, status, errorMessage, createdAt, isDeleted, deletedAt);
        }
    }
}
