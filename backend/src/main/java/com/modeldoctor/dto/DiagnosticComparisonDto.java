package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DiagnosticComparisonDto {

    private String baselineRunId;
    private String candidateRunId;
    private String baselineModelName;
    private String candidateModelName;
    private String baselineDatasetName;
    private String candidateDatasetName;
    private String overallAssessment; // IMPROVED, REGRESSED, MIXED, NO_MATERIAL_CHANGE, INSUFFICIENT_EVIDENCE
    private String assessmentRationale;
    private int improvedMetricCount;
    private int regressedMetricCount;
    private int unchangedMetricCount;
    private int missingMetricCount;
    private List<MetricComparisonDto> metricComparisons;
    private List<String> improvedMetrics;
    private List<String> regressedMetrics;
    private List<String> unchangedMetrics;
    private List<String> missingMetrics;
    private Map<String, List<MetricComparisonDto>> moduleBreakdown;
    private String validationSummary;
    private boolean isAssociativeOnly = true;
    private String causalityDisclaimer = "RUN COMPARISON EVALUATES EMPIRICAL METRIC DIFFERENCES BETWEEN TWO DISTINCT EVALUATION RUNS. IT DOES NOT PROVE THAT AN INTERVENTION CAUSED THE OBSERVED DIFFERENCES.";
    private Instant comparedAt = Instant.now();

    public DiagnosticComparisonDto() {}

    public String getBaselineRunId() { return baselineRunId; }
    public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }

    public String getCandidateRunId() { return candidateRunId; }
    public void setCandidateRunId(String candidateRunId) { this.candidateRunId = candidateRunId; }

    public String getBaselineModelName() { return baselineModelName; }
    public void setBaselineModelName(String baselineModelName) { this.baselineModelName = baselineModelName; }

    public String getCandidateModelName() { return candidateModelName; }
    public void setCandidateModelName(String candidateModelName) { this.candidateModelName = candidateModelName; }

    public String getBaselineDatasetName() { return baselineDatasetName; }
    public void setBaselineDatasetName(String baselineDatasetName) { this.baselineDatasetName = baselineDatasetName; }

    public String getCandidateDatasetName() { return candidateDatasetName; }
    public void setCandidateDatasetName(String candidateDatasetName) { this.candidateDatasetName = candidateDatasetName; }

    public String getOverallAssessment() { return overallAssessment; }
    public void setOverallAssessment(String overallAssessment) { this.overallAssessment = overallAssessment; }

    public String getAssessmentRationale() { return assessmentRationale; }
    public void setAssessmentRationale(String assessmentRationale) { this.assessmentRationale = assessmentRationale; }

    public int getImprovedMetricCount() { return improvedMetricCount; }
    public void setImprovedMetricCount(int improvedMetricCount) { this.improvedMetricCount = improvedMetricCount; }

    public int getRegressedMetricCount() { return regressedMetricCount; }
    public void setRegressedMetricCount(int regressedMetricCount) { this.regressedMetricCount = regressedMetricCount; }

    public int getUnchangedMetricCount() { return unchangedMetricCount; }
    public void setUnchangedMetricCount(int unchangedMetricCount) { this.unchangedMetricCount = unchangedMetricCount; }

    public int getMissingMetricCount() { return missingMetricCount; }
    public void setMissingMetricCount(int missingMetricCount) { this.missingMetricCount = missingMetricCount; }

    public List<MetricComparisonDto> getMetricComparisons() { return metricComparisons; }
    public void setMetricComparisons(List<MetricComparisonDto> metricComparisons) { this.metricComparisons = metricComparisons; }

    public List<String> getImprovedMetrics() { return improvedMetrics; }
    public void setImprovedMetrics(List<String> improvedMetrics) { this.improvedMetrics = improvedMetrics; }

    public List<String> getRegressedMetrics() { return regressedMetrics; }
    public void setRegressedMetrics(List<String> regressedMetrics) { this.regressedMetrics = regressedMetrics; }

    public List<String> getUnchangedMetrics() { return unchangedMetrics; }
    public void setUnchangedMetrics(List<String> unchangedMetrics) { this.unchangedMetrics = unchangedMetrics; }

    public List<String> getMissingMetrics() { return missingMetrics; }
    public void setMissingMetrics(List<String> missingMetrics) { this.missingMetrics = missingMetrics; }

    public Map<String, List<MetricComparisonDto>> getModuleBreakdown() { return moduleBreakdown; }
    public void setModuleBreakdown(Map<String, List<MetricComparisonDto>> moduleBreakdown) { this.moduleBreakdown = moduleBreakdown; }

    public String getValidationSummary() { return validationSummary; }
    public void setValidationSummary(String validationSummary) { this.validationSummary = validationSummary; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public String getCausalityDisclaimer() { return causalityDisclaimer; }
    public void setCausalityDisclaimer(String causalityDisclaimer) { this.causalityDisclaimer = causalityDisclaimer; }

    public Instant getComparedAt() { return comparedAt; }
    public void setComparedAt(Instant comparedAt) { this.comparedAt = comparedAt; }
}
