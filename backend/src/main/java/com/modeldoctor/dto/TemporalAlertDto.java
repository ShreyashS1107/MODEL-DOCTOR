package com.modeldoctor.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class TemporalAlertDto {
    private Long id;
    private String modelLineageId;
    private String runId;
    private String alertType; // "NEW_DEGRADATION", "PERSISTENT_DEGRADATION", "ESCALATING_DEGRADATION", "RECOVERY", "REGRESSION_AFTER_RECOVERY", "RECURRING_ISSUE", "CHANGE_POINT_DETECTED", "REMEDIATION_NOT_SUSTAINED", "MULTI_MODULE_ESCALATION"
    private String priority; // "CRITICAL", "HIGH", "MEDIUM", "LOW"
    private String targetType;
    private String targetKey;
    private String metricName;
    private Double currentValue;
    private Double referenceValue;
    private String triggerDescription;
    private String evidenceJson;
    private String confidence; // "VERY_HIGH", "HIGH", "MEDIUM", "LOW"
    private List<String> runIds = new ArrayList<>();
    private Instant createdAt;
    private boolean acknowledged;

    public TemporalAlertDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public Double getCurrentValue() { return currentValue; }
    public void setCurrentValue(Double currentValue) { this.currentValue = currentValue; }

    public Double getReferenceValue() { return referenceValue; }
    public void setReferenceValue(Double referenceValue) { this.referenceValue = referenceValue; }

    public String getTriggerDescription() { return triggerDescription; }
    public void setTriggerDescription(String triggerDescription) { this.triggerDescription = triggerDescription; }

    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public List<String> getRunIds() { return runIds; }
    public void setRunIds(List<String> runIds) { this.runIds = runIds; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
