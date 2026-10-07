package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "model_artifacts")
public class ModelArtifactEntity {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "original_filename", length = 255, nullable = false)
    private String originalFilename;

    @Column(name = "storage_path", length = 512, nullable = false)
    private String storagePath;

    @Column(name = "model_format", length = 64, nullable = false)
    private String modelFormat; // json, xgb, joblib, pkl, bin

    @Column(name = "framework", length = 64, nullable = false)
    private String framework; // xgboost, sklearn, lightgbm, pytorch

    @Column(name = "task_type", length = 64, nullable = false)
    private String taskType; // binary_classification, regression, multiclass

    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    @Column(name = "sha256", length = 64, nullable = false)
    private String sha256;

    @Column(name = "feature_names_json", columnDefinition = "TEXT")
    private String featureNamesJson;

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

    public ModelArtifactEntity() {}

    public ModelArtifactEntity(String id, String originalFilename, String storagePath, String modelFormat,
                               String framework, String taskType, Long fileSize, String sha256,
                               String featureNamesJson, String status, String errorMessage, Instant createdAt,
                               Boolean isDeleted, Instant deletedAt) {
        this.id = id;
        this.originalFilename = originalFilename;
        this.storagePath = storagePath;
        this.modelFormat = modelFormat;
        this.framework = framework;
        this.taskType = taskType;
        this.fileSize = fileSize;
        this.sha256 = sha256;
        this.featureNamesJson = featureNamesJson;
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

    public String getModelFormat() { return modelFormat; }
    public void setModelFormat(String modelFormat) { this.modelFormat = modelFormat; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }

    public String getFeatureNamesJson() { return featureNamesJson; }
    public void setFeatureNamesJson(String featureNamesJson) { this.featureNamesJson = featureNamesJson; }

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
        private String modelFormat;
        private String framework;
        private String taskType;
        private Long fileSize;
        private String sha256;
        private String featureNamesJson;
        private String status = "READY";
        private String errorMessage;
        private Instant createdAt = Instant.now();
        private Boolean isDeleted = false;
        private Instant deletedAt;

        public Builder id(String id) { this.id = id; return this; }
        public Builder originalFilename(String originalFilename) { this.originalFilename = originalFilename; return this; }
        public Builder storagePath(String storagePath) { this.storagePath = storagePath; return this; }
        public Builder modelFormat(String modelFormat) { this.modelFormat = modelFormat; return this; }
        public Builder framework(String framework) { this.framework = framework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder fileSize(Long fileSize) { this.fileSize = fileSize; return this; }
        public Builder sha256(String sha256) { this.sha256 = sha256; return this; }
        public Builder featureNamesJson(String featureNamesJson) { this.featureNamesJson = featureNamesJson; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder isDeleted(Boolean isDeleted) { this.isDeleted = isDeleted; return this; }
        public Builder deletedAt(Instant deletedAt) { this.deletedAt = deletedAt; return this; }

        public ModelArtifactEntity build() {
            return new ModelArtifactEntity(id, originalFilename, storagePath, modelFormat, framework, taskType,
                    fileSize, sha256, featureNamesJson, status, errorMessage, createdAt, isDeleted, deletedAt);
        }
    }
}
