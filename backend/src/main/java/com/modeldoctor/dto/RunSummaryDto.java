package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class RunSummaryDto {
    private String runId;
    private String status;
    private int totalModules;
    private int completedModules;
    private int failedModules;
    private long criticalFindingsCount;
    private long highPriorityFindingsCount;
    private long mediumPriorityFindingsCount;
    private long lowPriorityFindingsCount;
    private long totalCorrelationsCount;
    private List<String> topFeatures;
    private List<String> topInvestigationAreas;
    private Map<String, Long> moduleContributions;
    private Map<String, Map<String, Object>> featureProfiles;

    // Phase 6 Investigation and Evidence Graph Summary Fields
    private int investigationTargetCount;
    private int criticalInvestigationCount;
    private int highInvestigationCount;
    private String topInvestigationTarget;
    private Double topInvestigationScore;
    private int evidenceGraphNodeCount;
    private int evidenceGraphEdgeCount;

    // Phase 7 Remediation Summary Fields
    private int remediationCount;
    private int criticalRemediationCount;
    private int highRemediationCount;
    private int selectedRemediationCount;
    private int validatedRemediationCount;
    private String topRemediationType;
    private String topRemediationTarget;
    private boolean remediationAvailable = true;

    // Phase 8 Experiment Summary Fields
    private int experimentCount;
    private int validatedExperimentCount;
    private int runningExperimentCount;

    // Model and Temporal Intelligence Summary Fields
    private String modelName;
    private String executionMode;
    private java.time.Instant createdAt;
    private int historicalRunCount;
    private int activeIssueCount;
    private int persistentIssueCount;
    private int emergingIssueCount;
    private int recurringIssueCount;
    private int activeAlertCount;
    private int criticalAlertCount;
    private int changePointCount;

    @JsonProperty("isAssociativeOnly")
    private boolean isAssociativeOnly = true;

    public RunSummaryDto() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalModules() { return totalModules; }
    public void setTotalModules(int totalModules) { this.totalModules = totalModules; }

    @JsonProperty("moduleCount")
    public int getModuleCount() { return totalModules; }

    public int getCompletedModules() { return completedModules; }
    public void setCompletedModules(int completedModules) { this.completedModules = completedModules; }

    public int getFailedModules() { return failedModules; }
    public void setFailedModules(int failedModules) { this.failedModules = failedModules; }

    public long getCriticalFindingsCount() { return criticalFindingsCount; }
    public void setCriticalFindingsCount(long criticalFindingsCount) { this.criticalFindingsCount = criticalFindingsCount; }

    @JsonProperty("criticalFindings")
    public long getCriticalFindings() { return criticalFindingsCount; }

    public long getHighPriorityFindingsCount() { return highPriorityFindingsCount; }
    public void setHighPriorityFindingsCount(long highPriorityFindingsCount) { this.highPriorityFindingsCount = highPriorityFindingsCount; }

    @JsonProperty("highPriorityFindings")
    public long getHighPriorityFindings() { return highPriorityFindingsCount; }

    public long getMediumPriorityFindingsCount() { return mediumPriorityFindingsCount; }
    public void setMediumPriorityFindingsCount(long mediumPriorityFindingsCount) { this.mediumPriorityFindingsCount = mediumPriorityFindingsCount; }

    @JsonProperty("mediumPriorityFindings")
    public long getMediumPriorityFindings() { return mediumPriorityFindingsCount; }

    public long getLowPriorityFindingsCount() { return lowPriorityFindingsCount; }
    public void setLowPriorityFindingsCount(long lowPriorityFindingsCount) { this.lowPriorityFindingsCount = lowPriorityFindingsCount; }

    @JsonProperty("lowPriorityFindings")
    public long getLowPriorityFindings() { return lowPriorityFindingsCount; }

    public long getTotalCorrelationsCount() { return totalCorrelationsCount; }
    public void setTotalCorrelationsCount(long totalCorrelationsCount) { this.totalCorrelationsCount = totalCorrelationsCount; }

    @JsonProperty("totalFindings")
    public long getTotalFindings() { return totalCorrelationsCount; }

    public List<String> getTopFeatures() { return topFeatures; }
    public void setTopFeatures(List<String> topFeatures) { this.topFeatures = topFeatures; }

    public List<String> getTopInvestigationAreas() { return topInvestigationAreas; }
    public void setTopInvestigationAreas(List<String> topInvestigationAreas) { this.topInvestigationAreas = topInvestigationAreas; }

    public Map<String, Long> getModuleContributions() { return moduleContributions; }
    public void setModuleContributions(Map<String, Long> moduleContributions) { this.moduleContributions = moduleContributions; }

    public Map<String, Map<String, Object>> getFeatureProfiles() { return featureProfiles; }
    public void setFeatureProfiles(Map<String, Map<String, Object>> featureProfiles) { this.featureProfiles = featureProfiles; }

    public int getInvestigationTargetCount() { return investigationTargetCount; }
    public void setInvestigationTargetCount(int investigationTargetCount) { this.investigationTargetCount = investigationTargetCount; }

    public int getCriticalInvestigationCount() { return criticalInvestigationCount; }
    public void setCriticalInvestigationCount(int criticalInvestigationCount) { this.criticalInvestigationCount = criticalInvestigationCount; }

    public int getHighInvestigationCount() { return highInvestigationCount; }
    public void setHighInvestigationCount(int highInvestigationCount) { this.highInvestigationCount = highInvestigationCount; }

    public String getTopInvestigationTarget() { return topInvestigationTarget; }
    public void setTopInvestigationTarget(String topInvestigationTarget) { this.topInvestigationTarget = topInvestigationTarget; }

    public Double getTopInvestigationScore() { return topInvestigationScore; }
    public void setTopInvestigationScore(Double topInvestigationScore) { this.topInvestigationScore = topInvestigationScore; }

    public int getEvidenceGraphNodeCount() { return evidenceGraphNodeCount; }
    public void setEvidenceGraphNodeCount(int evidenceGraphNodeCount) { this.evidenceGraphNodeCount = evidenceGraphNodeCount; }

    public int getEvidenceGraphEdgeCount() { return evidenceGraphEdgeCount; }
    public void setEvidenceGraphEdgeCount(int evidenceGraphEdgeCount) { this.evidenceGraphEdgeCount = evidenceGraphEdgeCount; }

    public int getRemediationCount() { return remediationCount; }
    public void setRemediationCount(int remediationCount) { this.remediationCount = remediationCount; }

    public int getCriticalRemediationCount() { return criticalRemediationCount; }
    public void setCriticalRemediationCount(int criticalRemediationCount) { this.criticalRemediationCount = criticalRemediationCount; }

    public int getHighRemediationCount() { return highRemediationCount; }
    public void setHighRemediationCount(int highRemediationCount) { this.highRemediationCount = highRemediationCount; }

    public int getSelectedRemediationCount() { return selectedRemediationCount; }
    public void setSelectedRemediationCount(int selectedRemediationCount) { this.selectedRemediationCount = selectedRemediationCount; }

    public int getValidatedRemediationCount() { return validatedRemediationCount; }
    public void setValidatedRemediationCount(int validatedRemediationCount) { this.validatedRemediationCount = validatedRemediationCount; }

    public String getTopRemediationType() { return topRemediationType; }
    public void setTopRemediationType(String topRemediationType) { this.topRemediationType = topRemediationType; }

    public String getTopRemediationTarget() { return topRemediationTarget; }
    public void setTopRemediationTarget(String topRemediationTarget) { this.topRemediationTarget = topRemediationTarget; }

    public boolean isRemediationAvailable() { return remediationAvailable; }
    public void setRemediationAvailable(boolean remediationAvailable) { this.remediationAvailable = remediationAvailable; }

    public int getExperimentCount() { return experimentCount; }
    public void setExperimentCount(int experimentCount) { this.experimentCount = experimentCount; }

    public int getValidatedExperimentCount() { return validatedExperimentCount; }
    public void setValidatedExperimentCount(int validatedExperimentCount) { this.validatedExperimentCount = validatedExperimentCount; }

    public int getRunningExperimentCount() { return runningExperimentCount; }
    public void setRunningExperimentCount(int runningExperimentCount) { this.runningExperimentCount = runningExperimentCount; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getExecutionMode() { return executionMode; }
    public void setExecutionMode(String executionMode) { this.executionMode = executionMode; }

    public java.time.Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(java.time.Instant createdAt) { this.createdAt = createdAt; }

    public int getHistoricalRunCount() { return historicalRunCount; }
    public void setHistoricalRunCount(int historicalRunCount) { this.historicalRunCount = historicalRunCount; }

    public int getActiveIssueCount() { return activeIssueCount; }
    public void setActiveIssueCount(int activeIssueCount) { this.activeIssueCount = activeIssueCount; }

    public int getPersistentIssueCount() { return persistentIssueCount; }
    public void setPersistentIssueCount(int persistentIssueCount) { this.persistentIssueCount = persistentIssueCount; }

    public int getEmergingIssueCount() { return emergingIssueCount; }
    public void setEmergingIssueCount(int emergingIssueCount) { this.emergingIssueCount = emergingIssueCount; }

    public int getRecurringIssueCount() { return recurringIssueCount; }
    public void setRecurringIssueCount(int recurringIssueCount) { this.recurringIssueCount = recurringIssueCount; }

    public int getActiveAlertCount() { return activeAlertCount; }
    public void setActiveAlertCount(int activeAlertCount) { this.activeAlertCount = activeAlertCount; }

    public int getCriticalAlertCount() { return criticalAlertCount; }
    public void setCriticalAlertCount(int criticalAlertCount) { this.criticalAlertCount = criticalAlertCount; }

    public int getChangePointCount() { return changePointCount; }
    public void setChangePointCount(int changePointCount) { this.changePointCount = changePointCount; }
}
