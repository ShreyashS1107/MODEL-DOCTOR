package com.modeldoctor.dto;

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

    public RunSummaryDto() {}

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public int getTotalModules() { return totalModules; }
    public void setTotalModules(int totalModules) { this.totalModules = totalModules; }

    public int getCompletedModules() { return completedModules; }
    public void setCompletedModules(int completedModules) { this.completedModules = completedModules; }

    public int getFailedModules() { return failedModules; }
    public void setFailedModules(int failedModules) { this.failedModules = failedModules; }

    public long getCriticalFindingsCount() { return criticalFindingsCount; }
    public void setCriticalFindingsCount(long criticalFindingsCount) { this.criticalFindingsCount = criticalFindingsCount; }

    public long getHighPriorityFindingsCount() { return highPriorityFindingsCount; }
    public void setHighPriorityFindingsCount(long highPriorityFindingsCount) { this.highPriorityFindingsCount = highPriorityFindingsCount; }

    public long getMediumPriorityFindingsCount() { return mediumPriorityFindingsCount; }
    public void setMediumPriorityFindingsCount(long mediumPriorityFindingsCount) { this.mediumPriorityFindingsCount = mediumPriorityFindingsCount; }

    public long getLowPriorityFindingsCount() { return lowPriorityFindingsCount; }
    public void setLowPriorityFindingsCount(long lowPriorityFindingsCount) { this.lowPriorityFindingsCount = lowPriorityFindingsCount; }

    public long getTotalCorrelationsCount() { return totalCorrelationsCount; }
    public void setTotalCorrelationsCount(long totalCorrelationsCount) { this.totalCorrelationsCount = totalCorrelationsCount; }

    public List<String> getTopFeatures() { return topFeatures; }
    public void setTopFeatures(List<String> topFeatures) { this.topFeatures = topFeatures; }

    public List<String> getTopInvestigationAreas() { return topInvestigationAreas; }
    public void setTopInvestigationAreas(List<String> topInvestigationAreas) { this.topInvestigationAreas = topInvestigationAreas; }

    public Map<String, Long> getModuleContributions() { return moduleContributions; }
    public void setModuleContributions(Map<String, Long> moduleContributions) { this.moduleContributions = moduleContributions; }

    public Map<String, Map<String, Object>> getFeatureProfiles() { return featureProfiles; }
    public void setFeatureProfiles(Map<String, Map<String, Object>> featureProfiles) { this.featureProfiles = featureProfiles; }
}
