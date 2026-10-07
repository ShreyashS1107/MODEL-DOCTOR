package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DatasetArtifactResponseDto {
    private String id;
    private String filename;
    private String datasetFormat;
    private Long sizeBytes;
    private String sha256;
    private Long rowCount;
    private Integer columnCount;
    private List<String> columnNames;
    private Map<String, String> dtypes;
    private DatasetSchemaSummaryDto schemaSummary;
    private String status;
    private Instant createdAt;
    private Boolean isDeleted;
    private Instant deletedAt;

    public DatasetArtifactResponseDto() {}

    public DatasetArtifactResponseDto(String id, String filename, String datasetFormat, Long sizeBytes, String sha256,
                                      Long rowCount, Integer columnCount, List<String> columnNames,
                                      Map<String, String> dtypes, DatasetSchemaSummaryDto schemaSummary,
                                      String status, Instant createdAt, Boolean isDeleted, Instant deletedAt) {
        this.id = id;
        this.filename = filename;
        this.datasetFormat = datasetFormat;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.columnNames = columnNames;
        this.dtypes = dtypes;
        this.schemaSummary = schemaSummary;
        this.status = status;
        this.createdAt = createdAt;
        this.isDeleted = isDeleted != null ? isDeleted : false;
        this.deletedAt = deletedAt;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }

    public String getOriginalFilename() { return filename; }
    public void setOriginalFilename(String originalFilename) { this.filename = originalFilename; }

    public String getDatasetFormat() { return datasetFormat; }
    public void setDatasetFormat(String datasetFormat) { this.datasetFormat = datasetFormat; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Long getRowCount() { return rowCount; }
    public void setRowCount(Long rowCount) { this.rowCount = rowCount; }

    public Integer getColumnCount() { return columnCount; }
    public void setColumnCount(Integer columnCount) { this.columnCount = columnCount; }

    public List<String> getColumnNames() { return columnNames; }
    public void setColumnNames(List<String> columnNames) { this.columnNames = columnNames; }

    public Map<String, String> getDtypes() { return dtypes; }
    public void setDtypes(Map<String, String> dtypes) { this.dtypes = dtypes; }

    public DatasetSchemaSummaryDto getSchemaSummary() { return schemaSummary; }
    public void setSchemaSummary(DatasetSchemaSummaryDto schemaSummary) { this.schemaSummary = schemaSummary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Boolean getIsDeleted() { return isDeleted != null && isDeleted; }
    public void setIsDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; }

    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String filename;
        private String datasetFormat;
        private Long sizeBytes;
        private String sha256;
        private Long rowCount;
        private Integer columnCount;
        private List<String> columnNames;
        private Map<String, String> dtypes;
        private DatasetSchemaSummaryDto schemaSummary;
        private String status;
        private Instant createdAt;
        private Boolean isDeleted = false;
        private Instant deletedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder datasetFormat(String datasetFormat) { this.datasetFormat = datasetFormat; return this; }
        public Builder sizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder sha256(String sha256) { this.sha256 = sha256; return this; }
        public Builder rowCount(Long rowCount) { this.rowCount = rowCount; return this; }
        public Builder columnCount(Integer columnCount) { this.columnCount = columnCount; return this; }
        public Builder columnNames(List<String> columnNames) { this.columnNames = columnNames; return this; }
        public Builder dtypes(Map<String, String> dtypes) { this.dtypes = dtypes; return this; }
        public Builder schemaSummary(DatasetSchemaSummaryDto schemaSummary) { this.schemaSummary = schemaSummary; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        public DatasetArtifactResponseDto build() {
            return new DatasetArtifactResponseDto(id, filename, datasetFormat, sizeBytes, sha256, rowCount, columnCount, columnNames, dtypes, schemaSummary, status, createdAt, isDeleted, deletedAt);
        }
    }
}
