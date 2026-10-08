package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_temporal_alerts", indexes = {
        @Index(name = "idx_temp_alert_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_temp_alert_type", columnList = "model_lineage_id, alert_type"),
        @Index(name = "idx_temp_alert_priority", columnList = "model_lineage_id, priority"),
        @Index(name = "idx_temp_alert_created", columnList = "created_at")
})
public class DiagnosticTemporalAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "alert_type", length = 64, nullable = false)
    private String alertType; // "NEW_DEGRADATION", "PERSISTENT_DEGRADATION", "ESCALATING_DEGRADATION", "RECOVERY", "REGRESSION_AFTER_RECOVERY", "RECURRING_ISSUE", "CHANGE_POINT_DETECTED", "REMEDIATION_NOT_SUSTAINED", "MULTI_MODULE_ESCALATION"

    @Column(name = "priority", length = 32, nullable = false)
    private String priority = "MEDIUM"; // "CRITICAL", "HIGH", "MEDIUM", "LOW"

    @Column(name = "target_type", length = 64)
    private String targetType; // "FEATURE", "SUBGROUP", "MODEL", "GLOBAL"

    @Column(name = "target_key", length = 128)
    private String targetKey;

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
    private String confidence = "HIGH"; // "VERY_HIGH", "HIGH", "MEDIUM", "LOW"

    @Column(name = "run_ids_json", columnDefinition = "TEXT")
    private String runIdsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "acknowledged", nullable = false)
    private boolean acknowledged = false;

    public DiagnosticTemporalAlert() {}

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

    public String getRunIdsJson() { return runIdsJson; }
    public void setRunIdsJson(String runIdsJson) { this.runIdsJson = runIdsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public boolean isAcknowledged() { return acknowledged; }
    public void setAcknowledged(boolean acknowledged) { this.acknowledged = acknowledged; }
}
