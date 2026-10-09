package com.modeldoctor.dto;

import com.modeldoctor.domain.AlertLifecycleState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OperationalAlertDto {

    private Long id;
    private String modelLineageId;
    private String alertFingerprint;
    private String alertType;
    private String currentSeverity;
    private String previousSeverity;
    private String severityChange;
    private AlertLifecycleState lifecycleState;
    private String targetType;
    private String targetKey;
    private String metricName;
    private Double currentValue;
    private Double referenceValue;
    private String triggerDescription;
    private String evidenceJson;
    private String confidence;
    private String sourceModule;
    private String firstSeenRunId;
    private String lastSeenRunId;
    private Instant firstObservedAt;
    private Instant lastObservedAt;
    private int occurrenceCount;
    private int consecutiveCount;
    private int escalationCount;
    private int recoveryCount;
    private int reopenCount;
    private Instant suppressedUntil;
    private String suppressionReason;
    private String suppressedBy;
    private String acknowledgedBy;
    private Instant acknowledgedAt;
    private String investigatedBy;
    private Instant investigatedAt;
    private String resolvedBy;
    private Instant resolvedAt;
    private String resolutionReason;
    private String relatedInvestigationTargetKey;
    private Long relatedRemediationId;
    private String relatedExperimentId;
    private Long relatedIssueTrackId;
    private List<String> runIds = new ArrayList<>();
    private Integer cooldownUntilRunIndex;
    private boolean currentlySuppressed;
    private List<String> allowedActions = new ArrayList<>();

    public OperationalAlertDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getAlertFingerprint() { return alertFingerprint; }
    public void setAlertFingerprint(String alertFingerprint) { this.alertFingerprint = alertFingerprint; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public String getPreviousSeverity() { return previousSeverity; }
    public void setPreviousSeverity(String previousSeverity) { this.previousSeverity = previousSeverity; }

    public String getSeverityChange() { return severityChange; }
    public void setSeverityChange(String severityChange) { this.severityChange = severityChange; }

    public AlertLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(AlertLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }

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

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public String getFirstSeenRunId() { return firstSeenRunId; }
    public void setFirstSeenRunId(String firstSeenRunId) { this.firstSeenRunId = firstSeenRunId; }

    public String getLastSeenRunId() { return lastSeenRunId; }
    public void setLastSeenRunId(String lastSeenRunId) { this.lastSeenRunId = lastSeenRunId; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public int getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(int occurrenceCount) { this.occurrenceCount = occurrenceCount; }

    public int getConsecutiveCount() { return consecutiveCount; }
    public void setConsecutiveCount(int consecutiveCount) { this.consecutiveCount = consecutiveCount; }

    public int getEscalationCount() { return escalationCount; }
    public void setEscalationCount(int escalationCount) { this.escalationCount = escalationCount; }

    public int getRecoveryCount() { return recoveryCount; }
    public void setRecoveryCount(int recoveryCount) { this.recoveryCount = recoveryCount; }

    public int getReopenCount() { return reopenCount; }
    public void setReopenCount(int reopenCount) { this.reopenCount = reopenCount; }

    public Instant getSuppressedUntil() { return suppressedUntil; }
    public void setSuppressedUntil(Instant suppressedUntil) { this.suppressedUntil = suppressedUntil; }

    public String getSuppressionReason() { return suppressionReason; }
    public void setSuppressionReason(String suppressionReason) { this.suppressionReason = suppressionReason; }

    public String getSuppressedBy() { return suppressedBy; }
    public void setSuppressedBy(String suppressedBy) { this.suppressedBy = suppressedBy; }

    public String getAcknowledgedBy() { return acknowledgedBy; }
    public void setAcknowledgedBy(String acknowledgedBy) { this.acknowledgedBy = acknowledgedBy; }

    public Instant getAcknowledgedAt() { return acknowledgedAt; }
    public void setAcknowledgedAt(Instant acknowledgedAt) { this.acknowledgedAt = acknowledgedAt; }

    public String getInvestigatedBy() { return investigatedBy; }
    public void setInvestigatedBy(String investigatedBy) { this.investigatedBy = investigatedBy; }

    public Instant getInvestigatedAt() { return investigatedAt; }
    public void setInvestigatedAt(Instant investigatedAt) { this.investigatedAt = investigatedAt; }

    public String getResolvedBy() { return resolvedBy; }
    public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getResolutionReason() { return resolutionReason; }
    public void setResolutionReason(String resolutionReason) { this.resolutionReason = resolutionReason; }

    public String getRelatedInvestigationTargetKey() { return relatedInvestigationTargetKey; }
    public void setRelatedInvestigationTargetKey(String relatedInvestigationTargetKey) { this.relatedInvestigationTargetKey = relatedInvestigationTargetKey; }

    public Long getRelatedRemediationId() { return relatedRemediationId; }
    public void setRelatedRemediationId(Long relatedRemediationId) { this.relatedRemediationId = relatedRemediationId; }

    public String getRelatedExperimentId() { return relatedExperimentId; }
    public void setRelatedExperimentId(String relatedExperimentId) { this.relatedExperimentId = relatedExperimentId; }

    public Long getRelatedIssueTrackId() { return relatedIssueTrackId; }
    public void setRelatedIssueTrackId(Long relatedIssueTrackId) { this.relatedIssueTrackId = relatedIssueTrackId; }

    public List<String> getRunIds() { return runIds; }
    public void setRunIds(List<String> runIds) { this.runIds = runIds; }

    public Integer getCooldownUntilRunIndex() { return cooldownUntilRunIndex; }
    public void setCooldownUntilRunIndex(Integer cooldownUntilRunIndex) { this.cooldownUntilRunIndex = cooldownUntilRunIndex; }

    public boolean isCurrentlySuppressed() { return currentlySuppressed; }
    public void setCurrentlySuppressed(boolean currentlySuppressed) { this.currentlySuppressed = currentlySuppressed; }

    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }
}
