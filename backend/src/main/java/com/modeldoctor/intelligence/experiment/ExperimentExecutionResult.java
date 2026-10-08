package com.modeldoctor.intelligence.experiment;

import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.dto.MetricComparisonDto;
import com.modeldoctor.dto.StatisticalEvidenceDto;

import java.util.*;

public class ExperimentExecutionResult {

    private boolean success;
    private String candidateRunId;
    private Map<String, Object> baselineMetrics = new HashMap<>();
    private Map<String, Object> candidateMetrics = new HashMap<>();
    private List<MetricComparisonDto> metricDeltas = new ArrayList<>();
    private Map<String, Object> datasetProvenance = new HashMap<>();
    private Map<String, Object> modelProvenance = new HashMap<>();
    private StatisticalEvidenceDto statisticalEvidence;
    private List<DiagnosticModule> executedModules = new ArrayList<>();
    private String errorCode;
    private String errorMessage;

    public ExperimentExecutionResult() {}

    public static ExperimentExecutionResult failure(String errorCode, String errorMessage) {
        ExperimentExecutionResult res = new ExperimentExecutionResult();
        res.setSuccess(false);
        res.setErrorCode(errorCode);
        res.setErrorMessage(errorMessage);
        return res;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getCandidateRunId() { return candidateRunId; }
    public void setCandidateRunId(String candidateRunId) { this.candidateRunId = candidateRunId; }

    public Map<String, Object> getBaselineMetrics() { return baselineMetrics; }
    public void setBaselineMetrics(Map<String, Object> baselineMetrics) { this.baselineMetrics = baselineMetrics; }

    public Map<String, Object> getCandidateMetrics() { return candidateMetrics; }
    public void setCandidateMetrics(Map<String, Object> candidateMetrics) { this.candidateMetrics = candidateMetrics; }

    public List<MetricComparisonDto> getMetricDeltas() { return metricDeltas; }
    public void setMetricDeltas(List<MetricComparisonDto> metricDeltas) { this.metricDeltas = metricDeltas; }

    public Map<String, Object> getDatasetProvenance() { return datasetProvenance; }
    public void setDatasetProvenance(Map<String, Object> datasetProvenance) { this.datasetProvenance = datasetProvenance; }

    public Map<String, Object> getModelProvenance() { return modelProvenance; }
    public void setModelProvenance(Map<String, Object> modelProvenance) { this.modelProvenance = modelProvenance; }

    public StatisticalEvidenceDto getStatisticalEvidence() { return statisticalEvidence; }
    public void setStatisticalEvidence(StatisticalEvidenceDto statisticalEvidence) { this.statisticalEvidence = statisticalEvidence; }

    public List<DiagnosticModule> getExecutedModules() { return executedModules; }
    public void setExecutedModules(List<DiagnosticModule> executedModules) { this.executedModules = executedModules; }

    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
