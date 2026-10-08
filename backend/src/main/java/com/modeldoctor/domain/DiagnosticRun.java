package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "diagnostic_runs")
public class DiagnosticRun {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Version
    @Column(name = "version")
    private Long version;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private DiagnosticStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "model_artifact_id", length = 64)
    private String modelArtifactId;

    @Column(name = "baseline_dataset_artifact_id", length = 64)
    private String baselineDatasetArtifactId;

    @Column(name = "evaluation_dataset_artifact_id", length = 64)
    private String evaluationDatasetArtifactId;

    @Column(name = "execution_mode", length = 32)
    private String executionMode; // REAL, BENCHMARK, TEST

    @Column(name = "model_name", length = 255, nullable = false)
    private String modelName;

    @Column(name = "model_framework", length = 64, nullable = false)
    private String modelFramework;

    @Column(name = "task_type", length = 64, nullable = false)
    private String taskType;

    @Column(name = "model_storage_uri", length = 1024)
    private String modelStorageUri;

    @Column(name = "evaluation_dataset", length = 1024, nullable = false)
    private String evaluationDataset;

    @Column(name = "baseline_dataset", length = 1024)
    private String baselineDataset;

    @Column(name = "target_column", length = 255, nullable = false)
    private String targetColumn;

    @Column(name = "prediction_column", length = 255)
    private String predictionColumn;

    @Column(name = "protected_attribute", length = 255)
    private String protectedAttribute;

    @Column(name = "run_type", length = 32)
    private String runType = "BASELINE"; // "BASELINE", "EXPERIMENT"

    @Column(name = "parent_run_id", length = 64)
    private String parentRunId;

    @Column(name = "experiment_id", length = 64)
    private String experimentId;

    @Column(name = "remediation_id")
    private Long remediationId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "queued_at")
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "execution_duration_ms")
    private Long executionDurationMs;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<DiagnosticRunModule> modules = new ArrayList<>();

    public DiagnosticRun() {}

    public DiagnosticRun(String id, DiagnosticStatus status, int retryCount, String modelArtifactId, String baselineDatasetArtifactId,
                         String evaluationDatasetArtifactId, String executionMode, String modelName, String modelFramework,
                         String taskType, String modelStorageUri, String evaluationDataset, String baselineDataset,
                         String targetColumn, String predictionColumn, String protectedAttribute, Instant createdAt,
                         Instant queuedAt, Instant startedAt, Instant completedAt, Long executionDurationMs,
                         String errorMessage, List<DiagnosticRunModule> modules) {
        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.modelArtifactId = modelArtifactId;
        this.baselineDatasetArtifactId = baselineDatasetArtifactId;
        this.evaluationDatasetArtifactId = evaluationDatasetArtifactId;
        this.executionMode = executionMode;
        this.modelName = modelName;
        this.modelFramework = modelFramework;
        this.taskType = taskType;
        this.modelStorageUri = modelStorageUri;
        this.evaluationDataset = evaluationDataset;
        this.baselineDataset = baselineDataset;
        this.targetColumn = targetColumn;
        this.predictionColumn = predictionColumn;
        this.protectedAttribute = protectedAttribute;
        this.createdAt = createdAt;
        this.queuedAt = queuedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executionDurationMs = executionDurationMs;
        this.errorMessage = errorMessage;
        this.modules = modules != null ? modules : new ArrayList<>();
        for (DiagnosticRunModule m : this.modules) {
            m.setRun(this);
        }
    }

    public void addModule(DiagnosticRunModule module) {
        if (modules == null) {
            modules = new ArrayList<>();
        }
        modules.add(module);
        module.setRun(this);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public DiagnosticStatus getStatus() { return status; }
    public void setStatus(DiagnosticStatus status) { this.status = status; }

    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }

    public String getModelArtifactId() { return modelArtifactId; }
    public void setModelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; }

    public String getBaselineDatasetArtifactId() { return baselineDatasetArtifactId; }
    public void setBaselineDatasetArtifactId(String baselineDatasetArtifactId) { this.baselineDatasetArtifactId = baselineDatasetArtifactId; }

    public String getEvaluationDatasetArtifactId() { return evaluationDatasetArtifactId; }
    public void setEvaluationDatasetArtifactId(String evaluationDatasetArtifactId) { this.evaluationDatasetArtifactId = evaluationDatasetArtifactId; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelFramework() { return modelFramework; }
    public void setModelFramework(String modelFramework) { this.modelFramework = modelFramework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getModelStorageUri() { return modelStorageUri; }
    public void setModelStorageUri(String modelStorageUri) { this.modelStorageUri = modelStorageUri; }

    public String getEvaluationDataset() { return evaluationDataset; }
    public void setEvaluationDataset(String evaluationDataset) { this.evaluationDataset = evaluationDataset; }

    public String getBaselineDataset() { return baselineDataset; }
    public void setBaselineDataset(String baselineDataset) { this.baselineDataset = baselineDataset; }

    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }

    public String getPredictionColumn() { return predictionColumn; }
    public void setPredictionColumn(String predictionColumn) { this.predictionColumn = predictionColumn; }

    public String getProtectedAttribute() { return protectedAttribute; }
    public void setProtectedAttribute(String protectedAttribute) { this.protectedAttribute = protectedAttribute; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getQueuedAt() { return queuedAt; }
    public void setQueuedAt(Instant queuedAt) { this.queuedAt = queuedAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public Long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public List<DiagnosticRunModule> getModules() { return modules; }
    public void setModules(List<DiagnosticRunModule> modules) { this.modules = modules; }

    public static Builder builder() {
        return new Builder();
    }
    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public String getParentRunId() { return parentRunId; }
    public void setParentRunId(String parentRunId) { this.parentRunId = parentRunId; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public static class Builder {
        private String id;
        private DiagnosticStatus status;
        private int retryCount = 0;
        private String modelArtifactId;
        private String baselineDatasetArtifactId;
        private String evaluationDatasetArtifactId;
        private String executionMode;
        private String modelName;
        private String modelFramework;
        private String taskType;
        private String modelStorageUri;
        private String evaluationDataset;
        private String baselineDataset;
        private String targetColumn;
        private String predictionColumn;
        private String protectedAttribute;
        private Instant createdAt;
        private Instant queuedAt;
        private Instant startedAt;
        private Instant completedAt;
        private Long executionDurationMs;
        private String errorMessage;
        private List<DiagnosticRunModule> modules = new ArrayList<>();

        public Builder id(String id) { this.id = id; return this; }
        public Builder status(DiagnosticStatus status) { this.status = status; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder modelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; return this; }
        public Builder baselineDatasetArtifactId(String baselineDatasetArtifactId) { this.baselineDatasetArtifactId = baselineDatasetArtifactId; return this; }
        public Builder evaluationDatasetArtifactId(String evaluationDatasetArtifactId) { this.evaluationDatasetArtifactId = evaluationDatasetArtifactId; return this; }
        public Builder executionMode(String executionMode) { this.executionMode = executionMode; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder modelFramework(String modelFramework) { this.modelFramework = modelFramework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder modelStorageUri(String modelStorageUri) { this.modelStorageUri = modelStorageUri; return this; }
        public Builder evaluationDataset(String evaluationDataset) { this.evaluationDataset = evaluationDataset; return this; }
        public Builder baselineDataset(String baselineDataset) { this.baselineDataset = baselineDataset; return this; }
        public Builder targetColumn(String targetColumn) { this.targetColumn = targetColumn; return this; }
        public Builder predictionColumn(String predictionColumn) { this.predictionColumn = predictionColumn; return this; }
        public Builder protectedAttribute(String protectedAttribute) { this.protectedAttribute = protectedAttribute; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder queuedAt(Instant queuedAt) { this.queuedAt = queuedAt; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder executionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder modules(List<DiagnosticRunModule> modules) { this.modules = modules; return this; }

        public DiagnosticRun build() {
            return new DiagnosticRun(id, status, retryCount, modelArtifactId, baselineDatasetArtifactId, evaluationDatasetArtifactId,
                    executionMode, modelName, modelFramework, taskType, modelStorageUri, evaluationDataset,
                    baselineDataset, targetColumn, predictionColumn, protectedAttribute, createdAt, queuedAt, startedAt, completedAt,
                    executionDurationMs, errorMessage, modules);
        }
    }
}
