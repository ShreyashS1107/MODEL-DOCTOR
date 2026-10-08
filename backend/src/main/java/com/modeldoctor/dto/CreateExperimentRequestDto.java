package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.ExperimentType;

import java.util.List;

public class CreateExperimentRequestDto {

    private Long remediationId;
    private ExperimentType experimentType;
    private String title;
    private String description;
    private String targetType;
    private String targetKey;
    private InterventionConfigDto intervention;
    private List<DiagnosticModule> requestedModules;
    private List<String> acceptanceCriteria;
    private List<String> regressionGuards;
    private Integer deterministicSeed = 42;

    public CreateExperimentRequestDto() {}

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public ExperimentType getExperimentType() { return experimentType; }
    public void setExperimentType(ExperimentType experimentType) { this.experimentType = experimentType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public InterventionConfigDto getIntervention() { return intervention; }
    public void setIntervention(InterventionConfigDto intervention) { this.intervention = intervention; }

    public List<DiagnosticModule> getRequestedModules() { return requestedModules; }
    public void setRequestedModules(List<DiagnosticModule> requestedModules) { this.requestedModules = requestedModules; }

    public List<String> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<String> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }

    public List<String> getRegressionGuards() { return regressionGuards; }
    public void setRegressionGuards(List<String> regressionGuards) { this.regressionGuards = regressionGuards; }

    public Integer getDeterministicSeed() { return deterministicSeed; }
    public void setDeterministicSeed(Integer deterministicSeed) { this.deterministicSeed = deterministicSeed; }
}
