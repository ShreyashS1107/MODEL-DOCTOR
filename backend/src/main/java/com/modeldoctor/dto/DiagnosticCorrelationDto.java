package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DiagnosticCorrelationDto {
    private Long id;
    private String runId;
    private String ruleId;
    private String correlationKey;
    private String findingType;
    private String severity;
    private String priority;
    private Double priorityScore;
    private String confidence;
    private String feature;
    private String title;
    private String summary;
    private String whyItMatters;
    private String investigationDirection;
    private Map<String, Object> evidence;
    private List<String> sourceModules;
    private List<String> sourceResultIds;
    private Instant createdAt;
    private boolean isAssociativeOnly = true;

    public DiagnosticCorrelationDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }

    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getWhyItMatters() { return whyItMatters; }
    public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }

    public String getInvestigationDirection() { return investigationDirection; }
    public void setInvestigationDirection(String investigationDirection) { this.investigationDirection = investigationDirection; }

    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }

    public List<String> getSourceModules() { return sourceModules; }
    public void setSourceModules(List<String> sourceModules) { this.sourceModules = sourceModules; }

    public List<String> getSourceResultIds() { return sourceResultIds; }
    public void setSourceResultIds(List<String> sourceResultIds) { this.sourceResultIds = sourceResultIds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }
}
