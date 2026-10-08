package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_experiments", indexes = {
        @Index(name = "idx_diag_exp_baseline_run_id", columnList = "baseline_run_id"),
        @Index(name = "idx_diag_exp_candidate_run_id", columnList = "candidate_run_id"),
        @Index(name = "idx_diag_exp_remediation_id", columnList = "remediation_id"),
        @Index(name = "idx_diag_exp_status", columnList = "status"),
        @Index(name = "idx_diag_exp_created_at", columnList = "created_at DESC")
})
public class DiagnosticExperiment {

    @Id
    @Column(name = "id", length = 64, nullable = false)
    private String id;

    @Column(name = "baseline_run_id", length = 64, nullable = false)
    private String baselineRunId;

    @Column(name = "candidate_run_id", length = 64)
    private String candidateRunId;

    @Column(name = "remediation_id")
    private Long remediationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "experiment_type", length = 64, nullable = false)
    private ExperimentType experimentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 32, nullable = false)
    private ExperimentStatus status;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_key", length = 128)
    private String targetKey;

    @Column(name = "intervention_config_json", columnDefinition = "TEXT")
    private String interventionConfigJson;

    @Column(name = "dataset_provenance_json", columnDefinition = "TEXT")
    private String datasetProvenanceJson;

    @Column(name = "model_provenance_json", columnDefinition = "TEXT")
    private String modelProvenanceJson;

    @Column(name = "requested_modules_json", columnDefinition = "TEXT")
    private String requestedModulesJson;

    @Column(name = "executed_modules_json", columnDefinition = "TEXT")
    private String executedModulesJson;

    @Column(name = "baseline_metrics_json", columnDefinition = "TEXT")
    private String baselineMetricsJson;

    @Column(name = "candidate_metrics_json", columnDefinition = "TEXT")
    private String candidateMetricsJson;

    @Column(name = "metric_deltas_json", columnDefinition = "TEXT")
    private String metricDeltasJson;

    @Column(name = "acceptance_criteria_json", columnDefinition = "TEXT")
    private String acceptanceCriteriaJson;

    @Column(name = "acceptance_results_json", columnDefinition = "TEXT")
    private String acceptanceResultsJson;

    @Column(name = "regression_guards_json", columnDefinition = "TEXT")
    private String regressionGuardsJson;

    @Column(name = "regression_results_json", columnDefinition = "TEXT")
    private String regressionResultsJson;

    @Column(name = "statistical_evidence_json", columnDefinition = "TEXT")
    private String statisticalEvidenceJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "conclusion", length = 32)
    private ExperimentConclusion conclusion;

    @Column(name = "conclusion_reason", columnDefinition = "TEXT")
    private String conclusionReason;

    @Column(name = "deterministic_seed")
    private Integer deterministicSeed;

    @Column(name = "error_code", length = 64)
    private String errorCode;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public DiagnosticExperiment() {}

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

    public String getInterventionConfigJson() { return interventionConfigJson; }
    public void setInterventionConfigJson(String interventionConfigJson) { this.interventionConfigJson = interventionConfigJson; }

    public String getDatasetProvenanceJson() { return datasetProvenanceJson; }
    public void setDatasetProvenanceJson(String datasetProvenanceJson) { this.datasetProvenanceJson = datasetProvenanceJson; }

    public String getModelProvenanceJson() { return modelProvenanceJson; }
    public void setModelProvenanceJson(String modelProvenanceJson) { this.modelProvenanceJson = modelProvenanceJson; }

    public String getRequestedModulesJson() { return requestedModulesJson; }
    public void setRequestedModulesJson(String requestedModulesJson) { this.requestedModulesJson = requestedModulesJson; }

    public String getExecutedModulesJson() { return executedModulesJson; }
    public void setExecutedModulesJson(String executedModulesJson) { this.executedModulesJson = executedModulesJson; }

    public String getBaselineMetricsJson() { return baselineMetricsJson; }
    public void setBaselineMetricsJson(String baselineMetricsJson) { this.baselineMetricsJson = baselineMetricsJson; }

    public String getCandidateMetricsJson() { return candidateMetricsJson; }
    public void setCandidateMetricsJson(String candidateMetricsJson) { this.candidateMetricsJson = candidateMetricsJson; }

    public String getMetricDeltasJson() { return metricDeltasJson; }
    public void setMetricDeltasJson(String metricDeltasJson) { this.metricDeltasJson = metricDeltasJson; }

    public String getAcceptanceCriteriaJson() { return acceptanceCriteriaJson; }
    public void setAcceptanceCriteriaJson(String acceptanceCriteriaJson) { this.acceptanceCriteriaJson = acceptanceCriteriaJson; }

    public String getAcceptanceResultsJson() { return acceptanceResultsJson; }
    public void setAcceptanceResultsJson(String acceptanceResultsJson) { this.acceptanceResultsJson = acceptanceResultsJson; }

    public String getRegressionGuardsJson() { return regressionGuardsJson; }
    public void setRegressionGuardsJson(String regressionGuardsJson) { this.regressionGuardsJson = regressionGuardsJson; }

    public String getRegressionResultsJson() { return regressionResultsJson; }
    public void setRegressionResultsJson(String regressionResultsJson) { this.regressionResultsJson = regressionResultsJson; }

    public String getStatisticalEvidenceJson() { return statisticalEvidenceJson; }
    public void setStatisticalEvidenceJson(String statisticalEvidenceJson) { this.statisticalEvidenceJson = statisticalEvidenceJson; }

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
