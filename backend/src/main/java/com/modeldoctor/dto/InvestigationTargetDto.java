package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class InvestigationTargetDto {

    private Long id;
    private String runId;
    private String targetType; // FEATURE, SUBGROUP, BEHAVIOR, ERROR_TYPE
    private String targetKey;  // e.g. FEATURE::income, SUBGROUP::region=north
    private String displayName;
    private String priority;   // CRITICAL, HIGH, MEDIUM, LOW, INFO
    private Double priorityScore;
    private String confidence; // HIGH, MEDIUM, LOW
    private int supportingModuleCount;
    private int supportingFindingCount;
    private int supportingEvidenceCount;
    private int graphDegree;
    private String hypothesis;
    private List<String> nextActions;
    private List<String> supportingModules;
    private List<String> supportingRuleIds;
    private Map<String, Object> evidenceSummary;
    private boolean isAssociativeOnly = true;
    private Instant createdAt;

    public InvestigationTargetDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getEvidenceConfidence() { return confidence; }
    public void setEvidenceConfidence(String evidenceConfidence) { this.confidence = evidenceConfidence; }


    public int getSupportingModuleCount() { return supportingModuleCount; }
    public void setSupportingModuleCount(int supportingModuleCount) { this.supportingModuleCount = supportingModuleCount; }

    public int getSupportingFindingCount() { return supportingFindingCount; }
    public void setSupportingFindingCount(int supportingFindingCount) { this.supportingFindingCount = supportingFindingCount; }

    public int getSupportingEvidenceCount() { return supportingEvidenceCount; }
    public void setSupportingEvidenceCount(int supportingEvidenceCount) { this.supportingEvidenceCount = supportingEvidenceCount; }

    public int getGraphDegree() { return graphDegree; }
    public void setGraphDegree(int graphDegree) { this.graphDegree = graphDegree; }

    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }

    public List<String> getNextActions() { return nextActions; }
    public void setNextActions(List<String> nextActions) { this.nextActions = nextActions; }

    public List<String> getSupportingModules() { return supportingModules; }
    public void setSupportingModules(List<String> supportingModules) { this.supportingModules = supportingModules; }

    public List<String> getSupportingRuleIds() { return supportingRuleIds; }
    public void setSupportingRuleIds(List<String> supportingRuleIds) { this.supportingRuleIds = supportingRuleIds; }

    public Map<String, Object> getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(Map<String, Object> evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
