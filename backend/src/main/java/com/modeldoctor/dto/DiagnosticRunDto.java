package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticStatus;
import java.time.Instant;
import java.util.List;

public class DiagnosticRunDto {
    private String id;
    private String modelId;
    private String modelName;
    private String modelArchitecture;
    private DiagnosticStatus status;
    private Double healthScore;
    private Integer sampleCount;
    private Long durationMs;
    private Instant createdAt;
    private List<String> flaggedAnomalies;
    private Boolean isMockData;

    public DiagnosticRunDto() {}

    public DiagnosticRunDto(String id, String modelId, String modelName, String modelArchitecture, DiagnosticStatus status,
                            Double healthScore, Integer sampleCount, Long durationMs, Instant createdAt,
                            List<String> flaggedAnomalies, Boolean isMockData) {
        this.id = id;
        this.modelId = modelId;
        this.modelName = modelName;
        this.modelArchitecture = modelArchitecture;
        this.status = status;
        this.healthScore = healthScore;
        this.sampleCount = sampleCount;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
        this.flaggedAnomalies = flaggedAnomalies;
        this.isMockData = isMockData;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelArchitecture() { return modelArchitecture; }
    public void setModelArchitecture(String modelArchitecture) { this.modelArchitecture = modelArchitecture; }

    public DiagnosticStatus getStatus() { return status; }
    public void setStatus(DiagnosticStatus status) { this.status = status; }

    public Double getHealthScore() { return healthScore; }
    public void setHealthScore(Double healthScore) { this.healthScore = healthScore; }

    public Integer getSampleCount() { return sampleCount; }
    public void setSampleCount(Integer sampleCount) { this.sampleCount = sampleCount; }

    public Long getDurationMs() { return durationMs; }
    public void setDurationMs(Long durationMs) { this.durationMs = durationMs; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public List<String> getFlaggedAnomalies() { return flaggedAnomalies; }
    public void setFlaggedAnomalies(List<String> flaggedAnomalies) { this.flaggedAnomalies = flaggedAnomalies; }

    public Boolean getIsMockData() { return isMockData; }
    public void setIsMockData(Boolean isMockData) { this.isMockData = isMockData; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private String modelId;
        private String modelName;
        private String modelArchitecture;
        private DiagnosticStatus status;
        private Double healthScore;
        private Integer sampleCount;
        private Long durationMs;
        private Instant createdAt;
        private List<String> flaggedAnomalies;
        private Boolean isMockData;

        public Builder id(String id) { this.id = id; return this; }
        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder modelArchitecture(String modelArchitecture) { this.modelArchitecture = modelArchitecture; return this; }
        public Builder status(DiagnosticStatus status) { this.status = status; return this; }
        public Builder healthScore(Double healthScore) { this.healthScore = healthScore; return this; }
        public Builder sampleCount(Integer sampleCount) { this.sampleCount = sampleCount; return this; }
        public Builder durationMs(Long durationMs) { this.durationMs = durationMs; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder flaggedAnomalies(List<String> flaggedAnomalies) { this.flaggedAnomalies = flaggedAnomalies; return this; }
        public Builder isMockData(Boolean isMockData) { this.isMockData = isMockData; return this; }

        public DiagnosticRunDto build() {
            return new DiagnosticRunDto(id, modelId, modelName, modelArchitecture, status, healthScore,
                    sampleCount, durationMs, createdAt, flaggedAnomalies, isMockData);
        }
    }
}
