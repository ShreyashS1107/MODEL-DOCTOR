package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CreateDiagnosticRunRequestDto {

    private String modelArtifactId;

    private String baselineDatasetArtifactId;

    private String evaluationDatasetArtifactId;

    private String executionMode; // REAL, BENCHMARK, TEST

    private String runType; // BASELINE, EXPERIMENT

    @Valid
    private ModelInfoDto model;

    private String evaluationDataset;

    private String baselineDataset;

    @NotBlank(message = "Target column name is required")
    private String targetColumn;

    private String predictionColumn;

    private String protectedAttribute;

    @NotEmpty(message = "At least one diagnostic module must be selected")
    private List<DiagnosticModule> modules;

    public CreateDiagnosticRunRequestDto() {}

    public CreateDiagnosticRunRequestDto(String modelArtifactId, String baselineDatasetArtifactId,
                                         String evaluationDatasetArtifactId, String executionMode,
                                         ModelInfoDto model, String evaluationDataset, String baselineDataset,
                                         String targetColumn, String predictionColumn, String protectedAttribute,
                                         List<DiagnosticModule> modules) {
        this.modelArtifactId = modelArtifactId;
        this.baselineDatasetArtifactId = baselineDatasetArtifactId;
        this.evaluationDatasetArtifactId = evaluationDatasetArtifactId;
        this.executionMode = executionMode;
        this.model = model;
        this.evaluationDataset = evaluationDataset;
        this.baselineDataset = baselineDataset;
        this.targetColumn = targetColumn;
        this.predictionColumn = predictionColumn;
        this.protectedAttribute = protectedAttribute;
        this.modules = modules;
    }

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

    public List<DiagnosticModule> getModules() { return modules; }
    public void setModules(List<DiagnosticModule> modules) { this.modules = modules; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelArtifactId;
        private String baselineDatasetArtifactId;
        private String evaluationDatasetArtifactId;
        private String executionMode;
        private String runType;
        private ModelInfoDto model;
        private String evaluationDataset;
        private String baselineDataset;
        private String targetColumn;
        private String predictionColumn;
        private String protectedAttribute;
        private List<DiagnosticModule> modules;

        public Builder modelArtifactId(String modelArtifactId) { this.modelArtifactId = modelArtifactId; return this; }
        public Builder baselineDatasetArtifactId(String baselineDatasetArtifactId) { this.baselineDatasetArtifactId = baselineDatasetArtifactId; return this; }
        public Builder evaluationDatasetArtifactId(String evaluationDatasetArtifactId) { this.evaluationDatasetArtifactId = evaluationDatasetArtifactId; return this; }
        public Builder executionMode(String executionMode) { this.executionMode = executionMode; return this; }
        public Builder runType(String runType) { this.runType = runType; return this; }
        public Builder model(ModelInfoDto model) { this.model = model; return this; }
        public Builder evaluationDataset(String evaluationDataset) { this.evaluationDataset = evaluationDataset; return this; }
        public Builder baselineDataset(String baselineDataset) { this.baselineDataset = baselineDataset; return this; }
        public Builder targetColumn(String targetColumn) { this.targetColumn = targetColumn; return this; }
        public Builder predictionColumn(String predictionColumn) { this.predictionColumn = predictionColumn; return this; }
        public Builder protectedAttribute(String protectedAttribute) { this.protectedAttribute = protectedAttribute; return this; }
        public Builder modules(List<DiagnosticModule> modules) { this.modules = modules; return this; }

        public CreateDiagnosticRunRequestDto build() {
            CreateDiagnosticRunRequestDto dto = new CreateDiagnosticRunRequestDto(modelArtifactId, baselineDatasetArtifactId, evaluationDatasetArtifactId,
                    executionMode, model, evaluationDataset, baselineDataset, targetColumn,
                    predictionColumn, protectedAttribute, modules);
            dto.setRunType(runType);
            return dto;
        }
    }
}
