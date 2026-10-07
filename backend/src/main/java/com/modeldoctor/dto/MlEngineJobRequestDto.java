package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class MlEngineJobRequestDto {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("modelName")
    private String modelName;

    @JsonProperty("modelFramework")
    private String modelFramework;

    @JsonProperty("taskType")
    private String taskType;

    @JsonProperty("modelStorageUri")
    private String modelStorageUri;

    @JsonProperty("modelArtifactId")
    private String modelArtifactId;

    @JsonProperty("executionMode")
    private String executionMode;

    @JsonProperty("evaluationDataset")
    private String evaluationDataset;

    @JsonProperty("baselineDataset")
    private String baselineDataset;

    @JsonProperty("targetColumn")
    private String targetColumn;

    @JsonProperty("predictionColumn")
    private String predictionColumn;

    @JsonProperty("protectedAttribute")
    private String protectedAttribute;

    @JsonProperty("modules")
    private List<String> modules;

    public MlEngineJobRequestDto() {}

    public MlEngineJobRequestDto(String runId, String modelName, String modelFramework, String taskType,
                                 String modelStorageUri, String modelArtifactId, String executionMode,
                                 String evaluationDataset, String baselineDataset, String targetColumn,
                                 String predictionColumn, String protectedAttribute, List<String> modules) {
        this.runId = runId;
        this.modelName = modelName;
        this.modelFramework = modelFramework;
        this.taskType = taskType;
        this.modelStorageUri = modelStorageUri;
        this.modelArtifactId = modelArtifactId;
        this.executionMode = executionMode;
        this.evaluationDataset = evaluationDataset;
        this.baselineDataset = baselineDataset;
        this.targetColumn = targetColumn;
        this.predictionColumn = predictionColumn;
        this.protectedAttribute = protectedAttribute;
        this.modules = modules;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getModelFramework() { return modelFramework; }
    public void setModelFramework(String modelFramework) { this.modelFramework = modelFramework; }

    public String getTaskType() { return taskType; }
    public void setTaskType(String taskType) { this.taskType = taskType; }

    public String getModelStorageUri() { return modelStorageUri; }
    public void setModelStorageUri(String modelStorageUri) { this.modelStorageUri = modelStorageUri; }

    public String getModelArtifactId() { return modelArtifactId; }
    public void setModelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

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

    public List<String> getModules() { return modules; }
    public void setModules(List<String> modules) { this.modules = modules; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String runId;
        private String modelName;
        private String modelFramework;
        private String taskType;
        private String modelStorageUri;
        private String modelArtifactId;
        private String executionMode;
        private String evaluationDataset;
        private String baselineDataset;
        private String targetColumn;
        private String predictionColumn;
        private String protectedAttribute;
        private List<String> modules;

        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder modelFramework(String modelFramework) { this.modelFramework = modelFramework; return this; }
        public Builder taskType(String taskType) { this.taskType = taskType; return this; }
        public Builder modelStorageUri(String modelStorageUri) { this.modelStorageUri = modelStorageUri; return this; }
        public Builder modelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; return this; }
        public Builder executionMode(String executionMode) { this.executionMode = executionMode; return this; }
        public Builder evaluationDataset(String evaluationDataset) { this.evaluationDataset = evaluationDataset; return this; }
        public Builder baselineDataset(String baselineDataset) { this.baselineDataset = baselineDataset; return this; }
        public Builder targetColumn(String targetColumn) { this.targetColumn = targetColumn; return this; }
        public Builder predictionColumn(String predictionColumn) { this.predictionColumn = predictionColumn; return this; }
        public Builder protectedAttribute(String protectedAttribute) { this.protectedAttribute = protectedAttribute; return this; }
        public Builder modules(List<String> modules) { this.modules = modules; return this; }

        public MlEngineJobRequestDto build() {
            return new MlEngineJobRequestDto(runId, modelName, modelFramework, taskType, modelStorageUri,
                    modelArtifactId, executionMode, evaluationDataset, baselineDataset, targetColumn,
                    predictionColumn, protectedAttribute, modules);
        }
    }
}
