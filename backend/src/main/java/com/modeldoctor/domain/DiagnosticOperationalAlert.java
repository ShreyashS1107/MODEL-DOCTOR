package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_operational_alerts", indexes = {
        @Index(name = "idx_op_alert_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_op_alert_fingerprint", columnList = "model_lineage_id, alert_fingerprint"),
        @Index(name = "idx_op_alert_state", columnList = "model_lineage_id, lifecycle_state"),
        @Index(name = "idx_op_alert_severity", columnList = "model_lineage_id, current_severity"),
        @Index(name = "idx_op_alert_target", columnList = "model_lineage_id, target_key"),
        @Index(name = "idx_op_alert_last_seen", columnList = "last_observed_at")
})
public class DiagnosticOperationalAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "alert_fingerprint", length = 255, nullable = false)
    private String alertFingerprint;

    @Column(name = "alert_type", length = 64, nullable = false)
    private String alertType;

    @Column(name = "current_severity", length = 32, nullable = false)
    private String currentSeverity = "MEDIUM";

    @Column(name = "previous_severity", length = 32)
    private String previousSeverity;

    @Column(name = "severity_change", length = 32)
    private String severityChange = "NEW"; // "NEW", "ESCALATED", "DEESCALATED", "UNCHANGED"

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", length = 32, nullable = false)
    private AlertLifecycleState lifecycleState = AlertLifecycleState.OPEN;

    @Column(name = "target_type", length = 64)
    private String targetType = "GLOBAL";

    @Column(name = "target_key", length = 128)
    private String targetKey = "GLOBAL";

    @Column(name = "metric_name", length = 128)
    private String metricName;

    @Column(name = "current_value")
    private Double currentValue;

    @Column(name = "reference_value")
    private Double referenceValue;

    @Column(name = "trigger_description", columnDefinition = "TEXT", nullable = false)
    private String triggerDescription;

    @Column(name = "evidence_json", columnDefinition = "TEXT")
    private String evidenceJson;

    @Column(name = "confidence", length = 32)
    private String confidence = "HIGH";

    @Column(name = "source_module", length = 64)
    private String sourceModule;

    @Column(name = "first_seen_run_id", length = 64)
    private String firstSeenRunId;

    @Column(name = "last_seen_run_id", length = 64)
    private String lastSeenRunId;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt = Instant.now();

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt = Instant.now();

    @Column(name = "occurrence_count", nullable = false)
    private int occurrenceCount = 1;

    @Column(name = "consecutive_count", nullable = false)
    private int consecutiveCount = 1;

    @Column(name = "escalation_count", nullable = false)
    private int escalationCount = 0;

    @Column(name = "recovery_count", nullable = false)
    private int recoveryCount = 0;

    @Column(name = "reopen_count", nullable = false)
    private int reopenCount = 0;

    @Column(name = "suppressed_until")
    private Instant suppressedUntil;

    @Column(name = "suppression_reason", columnDefinition = "TEXT")
    private String suppressionReason;

    @Column(name = "suppressed_by", length = 128)
    private String suppressedBy;

    @Column(name = "acknowledged_by", length = 128)
    private String acknowledgedBy;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "investigated_by", length = 128)
    private String investigatedBy;

    @Column(name = "investigated_at")
    private Instant investigatedAt;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    @Column(name = "related_investigation_target_key", length = 128)
    private String relatedInvestigationTargetKey;

    @Column(name = "related_remediation_id")
    private Long relatedRemediationId;

    @Column(name = "related_experiment_id", length = 64)
    private String relatedExperimentId;

    @Column(name = "related_issue_track_id")
    private Long relatedIssueTrackId;

    @Column(name = "run_ids_json", columnDefinition = "TEXT")
    private String runIdsJson;

    @Column(name = "cooldown_until_run_index")
    private Integer cooldownUntilRunIndex;

    public DiagnosticOperationalAlert() {}

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

    public String getRunIdsJson() { return runIdsJson; }
    public void setRunIdsJson(String runIdsJson) { this.runIdsJson = runIdsJson; }

    public Integer getCooldownUntilRunIndex() { return cooldownUntilRunIndex; }
    public void setCooldownUntilRunIndex(Integer cooldownUntilRunIndex) { this.cooldownUntilRunIndex = cooldownUntilRunIndex; }

    public boolean isCurrentlySuppressed() {
        if (lifecycleState != AlertLifecycleState.SUPPRESSED) return false;
        if (suppressedUntil == null) return true;
        return Instant.now().isBefore(suppressedUntil);
    }
}
