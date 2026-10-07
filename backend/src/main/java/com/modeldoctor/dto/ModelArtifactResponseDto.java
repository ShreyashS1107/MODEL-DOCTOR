package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;

public class ModelArtifactResponseDto {
    private String id;
    private String filename;
    private String modelFormat;
    private String framework;
    private String taskType;
    private Long sizeBytes;
    private String sha256;
    private Integer featureCount;
    private List<String> featureNames;
    private String status;
    private Instant createdAt;
    private Boolean isDeleted;
    private Instant deletedAt;

    public ModelArtifactResponseDto() {}

    public ModelArtifactResponseDto(String id, String filename, String modelFormat, String framework, String taskType,
                                    Long sizeBytes, String sha256, Integer featureCount, List<String> featureNames,
                                    String status, Instant createdAt, Boolean isDeleted, Instant deletedAt) {
        this.id = id;
        this.filename = filename;
        this.modelFormat = modelFormat;
        this.framework = framework;
        this.taskType = taskType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.featureCount = featureCount;
        this.featureNames = featureNames;
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

    public String getModelFormat() { return modelFormat; }
    public void setModelFormat(String modelFormat) { this.modelFormat = modelFormat; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public Integer getFeatureCount() { return featureCount; }
    public void setFeatureCount(Integer featureCount) { this.featureCount = featureCount; }

    public List<String> getFeatureNames() { return featureNames; }
    public void setFeatureNames(List<String> featureNames) { this.featureNames = featureNames; }

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
        private String modelFormat;
        private String framework;
        private String taskType;
        private Long sizeBytes;
        private String sha256;
        private Integer featureCount;
        private List<String> featureNames;
        private String status;
        private Instant createdAt;
        private Boolean isDeleted = false;
        private Instant deletedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder filename(String filename) { this.filename = filename; return this; }
        public Builder modelFormat(String modelFormat) { this.modelFormat = modelFormat; return this; }
        public Builder framework(String framework) { this.framework = framework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder sizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder sha256(String sha256) { this.sha256 = sha256; return this; }
        public Builder featureCount(Integer featureCount) { this.featureCount = featureCount; return this; }
        public Builder featureNames(List<String> featureNames) { this.featureNames = featureNames; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        public ModelArtifactResponseDto build() {
            return new ModelArtifactResponseDto(id, filename, modelFormat, framework, taskType, sizeBytes, sha256,
                    featureCount, featureNames, status, createdAt, isDeleted, deletedAt);
        }
    }
}
