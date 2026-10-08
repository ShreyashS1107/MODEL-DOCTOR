package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.ExperimentConclusion;
import com.modeldoctor.domain.ExperimentStatus;
import com.modeldoctor.domain.ExperimentType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class DiagnosticExperimentDto {

    private String id;
    private String baselineRunId;
    private String candidateRunId;
    private Long remediationId;
    private ExperimentType experimentType;
    private ExperimentStatus status;
    private String title;
    private String description;
    private String targetType;
    private String targetKey;

    private InterventionConfigDto interventionConfig;
    private Map<String, Object> datasetProvenance;
    private Map<String, Object> modelProvenance;

    private List<DiagnosticModule> requestedModules;
    private List<DiagnosticModule> executedModules;

    private Map<String, Object> baselineMetrics;
    private Map<String, Object> candidateMetrics;
    private List<MetricComparisonDto> metricDeltas;

    private List<String> acceptanceCriteria;
    private List<AcceptanceCriterionResultDto> acceptanceResults;

    private List<String> regressionGuards;
    private List<RegressionGuardResultDto> regressionResults;

    private StatisticalEvidenceDto statisticalEvidence;

    private ExperimentConclusion conclusion;
    private String conclusionReason;
    private Integer deterministicSeed;

    private String errorCode;
    private String errorMessage;

    private Instant createdAt;
    private Instant startedAt;
    private Instant completedAt;

    public DiagnosticExperimentDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getBaselineRunId() { return baselineRunId; }
    public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }

    public String getCandidateRunId() { return candidateRunId; }
    public void setCandidateRunId(String candidateRunId) { this.candidateRunId = candidateRunId; }

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public ExperimentType getExperimentType() { return experimentType; }
    public void setExperimentType(ExperimentType experimentType) { this.experimentType = experimentType; }

    public ExperimentStatus getStatus() { return status; }
    public void setStatus(ExperimentStatus status) { this.status = status; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public InterventionConfigDto getInterventionConfig() { return interventionConfig; }
    public void setInterventionConfig(InterventionConfigDto interventionConfig) { this.interventionConfig = interventionConfig; }

    public Map<String, Object> getDatasetProvenance() { return datasetProvenance; }
    public void setDatasetProvenance(Map<String, Object> datasetProvenance) { this.datasetProvenance = datasetProvenance; }

    public Map<String, Object> getModelProvenance() { return modelProvenance; }
    public void setModelProvenance(Map<String, Object> modelProvenance) { this.modelProvenance = modelProvenance; }

    public List<DiagnosticModule> getRequestedModules() { return requestedModules; }
    public void setRequestedModules(List<DiagnosticModule> requestedModules) { this.requestedModules = requestedModules; }

    public List<DiagnosticModule> getExecutedModules() { return executedModules; }
    public void setExecutedModules(List<DiagnosticModule> executedModules) { this.executedModules = executedModules; }

    public Map<String, Object> getBaselineMetrics() { return baselineMetrics; }
    public void setBaselineMetrics(Map<String, Object> baselineMetrics) { this.baselineMetrics = baselineMetrics; }

    public Map<String, Object> getCandidateMetrics() { return candidateMetrics; }
    public void setCandidateMetrics(Map<String, Object> candidateMetrics) { this.candidateMetrics = candidateMetrics; }

    public List<MetricComparisonDto> getMetricDeltas() { return metricDeltas; }
    public void setMetricDeltas(List<MetricComparisonDto> metricDeltas) { this.metricDeltas = metricDeltas; }

    public List<String> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<String> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }

    public List<AcceptanceCriterionResultDto> getAcceptanceResults() { return acceptanceResults; }
    public void setAcceptanceResults(List<AcceptanceCriterionResultDto> acceptanceResults) { this.acceptanceResults = acceptanceResults; }

    public List<String> getRegressionGuards() { return regressionGuards; }
    public void setRegressionGuards(List<String> regressionGuards) { this.regressionGuards = regressionGuards; }

    public List<RegressionGuardResultDto> getRegressionResults() { return regressionResults; }
    public void setRegressionResults(List<RegressionGuardResultDto> regressionResults) { this.regressionResults = regressionResults; }

    public StatisticalEvidenceDto getStatisticalEvidence() { return statisticalEvidence; }
    public void setStatisticalEvidence(StatisticalEvidenceDto statisticalEvidence) { this.statisticalEvidence = statisticalEvidence; }

    public ExperimentConclusion getConclusion() { return conclusion; }
    public void setConclusion(ExperimentConclusion conclusion) { this.conclusion = conclusion; }

    public String getConclusionReason() { return conclusionReason; }
    public void setConclusionReason(String conclusionReason) { this.conclusionReason = conclusionReason; }

    public Integer getDeterministicSeed() { return deterministicSeed; }
    public void setDeterministicSeed(Integer deterministicSeed) { this.deterministicSeed = deterministicSeed; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
}
