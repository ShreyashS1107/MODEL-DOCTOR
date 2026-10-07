package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticStatus;
import java.time.Instant;
import java.util.List;

public class DiagnosticRunResponseDto {

    private String id;
    private DiagnosticStatus status;
    private int retryCount;
    private String modelArtifactId;
    private String baselineDatasetArtifactId;
    private String evaluationDatasetArtifactId;
    private String executionMode;
    private ModelInfoDto model;
    private ModelArtifactResponseDto modelArtifact;
    private DatasetArtifactResponseDto baselineDatasetArtifact;
    private DatasetArtifactResponseDto evaluationDatasetArtifact;
    private String evaluationDataset;
    private String baselineDataset;
    private String targetColumn;
    private String predictionColumn;
    private String protectedAttribute;
    private List<ModuleStatusDto> selectedModules;
    private DiagnosticProgressDto progress;
    private Instant createdAt;
    private Instant queuedAt;
    private Instant startedAt;
    private Instant completedAt;
    private Long executionDurationMs;
    private String errorMessage;

    public DiagnosticRunResponseDto() {}

    public DiagnosticRunResponseDto(String id, DiagnosticStatus status, int retryCount, String modelArtifactId,
                                    String baselineDatasetArtifactId, String evaluationDatasetArtifactId,
                                    String executionMode, ModelInfoDto model, ModelArtifactResponseDto modelArtifact,
                                    DatasetArtifactResponseDto baselineDatasetArtifact,
                                    DatasetArtifactResponseDto evaluationDatasetArtifact, String evaluationDataset,
                                    String baselineDataset, String targetColumn, String predictionColumn,
                                    String protectedAttribute, List<ModuleStatusDto> selectedModules,
                                    DiagnosticProgressDto progress, Instant createdAt, Instant queuedAt,
                                    Instant startedAt, Instant completedAt, Long executionDurationMs, String errorMessage) {
        this.id = id;
        this.status = status;
        this.retryCount = retryCount;
        this.modelArtifactId = modelArtifactId;
        this.baselineDatasetArtifactId = baselineDatasetArtifactId;
        this.evaluationDatasetArtifactId = evaluationDatasetArtifactId;
        this.executionMode = executionMode;
        this.model = model;
        this.modelArtifact = modelArtifact;
        this.baselineDatasetArtifact = baselineDatasetArtifact;
        this.evaluationDatasetArtifact = evaluationDatasetArtifact;
        this.evaluationDataset = evaluationDataset;
        this.baselineDataset = baselineDataset;
        this.targetColumn = targetColumn;
        this.predictionColumn = predictionColumn;
        this.protectedAttribute = protectedAttribute;
        this.selectedModules = selectedModules;
        this.progress = progress;
        this.createdAt = createdAt;
        this.queuedAt = queuedAt;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.executionDurationMs = executionDurationMs;
        this.errorMessage = errorMessage;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

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

    public ModelInfoDto getModel() { return model; }
    public void setModel(ModelInfoDto model) { this.model = model; }

    public ModelArtifactResponseDto getModelArtifact() { return modelArtifact; }
    public void setModelArtifact(ModelArtifactResponseDto modelArtifact) { this.modelArtifact = modelArtifact; }

    public DatasetArtifactResponseDto getBaselineDatasetArtifact() { return baselineDatasetArtifact; }
    public void setBaselineDatasetArtifact(DatasetArtifactResponseDto baselineDatasetArtifact) { this.baselineDatasetArtifact = baselineDatasetArtifact; }

    public DatasetArtifactResponseDto getEvaluationDatasetArtifact() { return evaluationDatasetArtifact; }
    public void setEvaluationDatasetArtifact(DatasetArtifactResponseDto evaluationDatasetArtifact) { this.evaluationDatasetArtifact = evaluationDatasetArtifact; }

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

    public List<ModuleStatusDto> getSelectedModules() { return selectedModules; }
    public void setSelectedModules(List<ModuleStatusDto> selectedModules) { this.selectedModules = selectedModules; }

    public DiagnosticProgressDto getProgress() { return progress; }
    public void setProgress(DiagnosticProgressDto progress) { this.progress = progress; }

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

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String id;
        private DiagnosticStatus status;
        private int retryCount = 0;
        private String modelArtifactId;
        private String baselineDatasetArtifactId;
        private String evaluationDatasetArtifactId;
        private String executionMode;
        private ModelInfoDto model;
        private ModelArtifactResponseDto modelArtifact;
        private DatasetArtifactResponseDto baselineDatasetArtifact;
        private DatasetArtifactResponseDto evaluationDatasetArtifact;
        private String evaluationDataset;
        private String baselineDataset;
        private String targetColumn;
        private String predictionColumn;
        private String protectedAttribute;
        private List<ModuleStatusDto> selectedModules;
        private DiagnosticProgressDto progress;
        private Instant createdAt;
        private Instant queuedAt;
        private Instant startedAt;
        private Instant completedAt;
        private Long executionDurationMs;
        private String errorMessage;

        public Builder id(String id) { this.id = id; return this; }
        public Builder status(DiagnosticStatus status) { this.status = status; return this; }
        public Builder retryCount(int retryCount) { this.retryCount = retryCount; return this; }
        public Builder modelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; return this; }
        public Builder baselineDatasetArtifactId(String baselineDatasetArtifactId) { this.baselineDatasetArtifactId = baselineDatasetArtifactId; return this; }
        public Builder evaluationDatasetArtifactId(String evaluationDatasetArtifactId) { this.evaluationDatasetArtifactId = evaluationDatasetArtifactId; return this; }
        public Builder executionMode(String executionMode) { this.executionMode = executionMode; return this; }
        public Builder model(ModelInfoDto model) { this.model = model; return this; }
        public Builder modelArtifact(ModelArtifactResponseDto modelArtifact) { this.modelArtifact = modelArtifact; return this; }
        public Builder baselineDatasetArtifact(DatasetArtifactResponseDto baselineDatasetArtifact) { this.baselineDatasetArtifact = baselineDatasetArtifact; return this; }
        public Builder evaluationDatasetArtifact(DatasetArtifactResponseDto evaluationDatasetArtifact) { this.evaluationDatasetArtifact = evaluationDatasetArtifact; return this; }
        public Builder evaluationDataset(String evaluationDataset) { this.evaluationDataset = evaluationDataset; return this; }
        public Builder baselineDataset(String baselineDataset) { this.baselineDataset = baselineDataset; return this; }
        public Builder targetColumn(String targetColumn) { this.targetColumn = targetColumn; return this; }
        public Builder predictionColumn(String predictionColumn) { this.predictionColumn = predictionColumn; return this; }
        public Builder protectedAttribute(String protectedAttribute) { this.protectedAttribute = protectedAttribute; return this; }
        public Builder selectedModules(List<ModuleStatusDto> selectedModules) { this.selectedModules = selectedModules; return this; }
        public Builder progress(DiagnosticProgressDto progress) { this.progress = progress; return this; }
        public Builder createdAt(Instant createdAt) { this.createdAt = createdAt; return this; }
        public Builder queuedAt(Instant queuedAt) { this.queuedAt = queuedAt; return this; }
        public Builder startedAt(Instant startedAt) { this.startedAt = startedAt; return this; }
        public Builder completedAt(Instant completedAt) { this.completedAt = completedAt; return this; }
        public Builder executionDurationMs(Long executionDurationMs) { this.executionDurationMs = executionDurationMs; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }

        public DiagnosticRunResponseDto build() {
            return new DiagnosticRunResponseDto(id, status, retryCount, modelArtifactId, baselineDatasetArtifactId,
                    evaluationDatasetArtifactId, executionMode, model, modelArtifact, baselineDatasetArtifact,
                    evaluationDatasetArtifact, evaluationDataset, baselineDataset, targetColumn, predictionColumn,
                    protectedAttribute, selectedModules, progress, createdAt, queuedAt, startedAt, completedAt,
                    executionDurationMs, errorMessage);
        }
    }
}
