package com.modeldoctor.domain;

import java.time.Instant;
import java.util.Map;

public class ModelArtifact {
    private String id;
    private String name;
    private String version;
    private String framework; // xgboost, lightgbm, pytorch, sklearn
    private String taskType;  // binary_classification, multiclass, regression
    private String storageUri;
    private Long sizeBytes;
    private Instant uploadedAt;
    private Map<String, String> metadata;

    public ModelArtifact() {}

    public ModelArtifact(String id, String name, String version, String framework, String taskType, String storageUri, Long sizeBytes, Instant uploadedAt, Map<String, String> metadata) {
        this.id = id;
        this.name = name;
        this.version = version;
        this.framework = framework;
        this.taskType = taskType;
        this.storageUri = storageUri;
        this.sizeBytes = sizeBytes;
        this.uploadedAt = uploadedAt;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getStorageUri() { return storageUri; }
    public void setStorageUri(String storageUri) { this.storageUri = storageUri; }

    public Long getSizeBytes() { return sizeBytes; }
    public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }

    public Instant getUploadedAt() { return uploadedAt; }
    public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String name;
        private String version;
        private String framework;
        private String taskType;
        private String storageUri;
        private Long sizeBytes;
        private Instant uploadedAt;
        private Map<String, String> metadata;

        public Builder id(String id) { this.id = id; return this; }
        public Builder name(String name) { this.name = name; return this; }
        public Builder version(String version) { this.version = version; return this; }
        public Builder framework(String framework) { this.framework = framework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder storageUri(String storageUri) { this.storageUri = storageUri; return this; }
        public Builder sizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; return this; }
        public Builder uploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; return this; }
        public Builder metadata(Map<String, String> metadata) { this.metadata = metadata; return this; }

        public ModelArtifact build() {
            return new ModelArtifact(id, name, version, framework, taskType, storageUri, sizeBytes, uploadedAt, metadata);
        }
    }
}
