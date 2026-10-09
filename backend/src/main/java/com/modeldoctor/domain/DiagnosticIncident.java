package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_incidents", indexes = {
        @Index(name = "idx_incident_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_incident_fingerprint", columnList = "model_lineage_id, incident_fingerprint"),
        @Index(name = "idx_incident_state", columnList = "model_lineage_id, lifecycle_state"),
        @Index(name = "idx_incident_priority", columnList = "model_lineage_id, priority_score"),
        @Index(name = "idx_incident_severity", columnList = "model_lineage_id, current_severity"),
        @Index(name = "idx_incident_target", columnList = "model_lineage_id, primary_target"),
        @Index(name = "idx_incident_last_seen", columnList = "last_observed_at")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_incident_fingerprint", columnNames = {"model_lineage_id", "incident_fingerprint"})
})
public class DiagnosticIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_code", length = 32, nullable = false)
    private String incidentCode;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "incident_fingerprint", length = 255, nullable = false)
    private String incidentFingerprint;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 64, nullable = false)
    private IncidentCategory category = IncidentCategory.MULTI_MODULE_INCIDENT;

    @Column(name = "current_severity", length = 32, nullable = false)
    private String currentSeverity = "MEDIUM";

    @Column(name = "priority_score", nullable = false)
    private int priorityScore = 50;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_state", length = 32, nullable = false)
    private IncidentLifecycleState lifecycleState = IncidentLifecycleState.OPEN;

    @Column(name = "primary_target", length = 128, nullable = false)
    private String primaryTarget = "GLOBAL";

    @Column(name = "primary_metric", length = 128)
    private String primaryMetric;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_recommendation", length = 64, nullable = false)
    private IncidentDecisionState decisionRecommendation = IncidentDecisionState.INVESTIGATE;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision_confidence", length = 32, nullable = false)
    private DecisionConfidence decisionConfidence = DecisionConfidence.MEDIUM;

    @Column(name = "decision_rationale", columnDefinition = "TEXT")
    private String decisionRationale;

    @Column(name = "evidence_summary", columnDefinition = "TEXT")
    private String evidenceSummary;

    @Column(name = "independent_module_count", nullable = false)
    private int independentModuleCount = 1;

    @Column(name = "related_alerts_count", nullable = false)
    private int relatedAlertsCount = 1;

    @Column(name = "investigation_target_key", length = 128)
    private String investigationTargetKey;

    @Column(name = "remediation_id")
    private Long remediationId;

    @Column(name = "experiment_id", length = 64)
    private String experimentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_health_state", length = 32)
    private ModelHealthState currentHealthState = ModelHealthState.UNKNOWN;

    @Column(name = "has_contradictory_evidence", nullable = false)
    private boolean hasContradictoryEvidence = false;

    @Column(name = "contradictory_evidence_summary", columnDefinition = "TEXT")
    private String contradictoryEvidenceSummary;

    @Column(name = "priority_breakdown_json", columnDefinition = "TEXT")
    private String priorityBreakdownJson;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt = Instant.now();

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "reopened_count", nullable = false)
    private int reopenedCount = 0;

    @Column(name = "recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "last_seen_run_id", length = 64)
    private String lastSeenRunId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DiagnosticIncident() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIncidentCode() { return incidentCode; }
    public void setIncidentCode(String incidentCode) { this.incidentCode = incidentCode; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getIncidentFingerprint() { return incidentFingerprint; }
    public void setIncidentFingerprint(String incidentFingerprint) { this.incidentFingerprint = incidentFingerprint; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public IncidentCategory getCategory() { return category; }
    public void setCategory(IncidentCategory category) { this.category = category; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }

    public IncidentLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(IncidentLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }

    public String getPrimaryTarget() { return primaryTarget; }
    public void setPrimaryTarget(String primaryTarget) { this.primaryTarget = primaryTarget; }

    public String getPrimaryMetric() { return primaryMetric; }
    public void setPrimaryMetric(String primaryMetric) { this.primaryMetric = primaryMetric; }

    public IncidentDecisionState getDecisionRecommendation() { return decisionRecommendation; }
    public void setDecisionRecommendation(IncidentDecisionState decisionRecommendation) { this.decisionRecommendation = decisionRecommendation; }

    public DecisionConfidence getDecisionConfidence() { return decisionConfidence; }
    public void setDecisionConfidence(DecisionConfidence decisionConfidence) { this.decisionConfidence = decisionConfidence; }

    public String getDecisionRationale() { return decisionRationale; }
    public void setDecisionRationale(String decisionRationale) { this.decisionRationale = decisionRationale; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public int getIndependentModuleCount() { return independentModuleCount; }
    public void setIndependentModuleCount(int independentModuleCount) { this.independentModuleCount = independentModuleCount; }

    public int getRelatedAlertsCount() { return relatedAlertsCount; }
    public void setRelatedAlertsCount(int relatedAlertsCount) { this.relatedAlertsCount = relatedAlertsCount; }

    public String getInvestigationTargetKey() { return investigationTargetKey; }
    public void setInvestigationTargetKey(String investigationTargetKey) { this.investigationTargetKey = investigationTargetKey; }

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public ModelHealthState getCurrentHealthState() { return currentHealthState; }
    public void setCurrentHealthState(ModelHealthState currentHealthState) { this.currentHealthState = currentHealthState; }

    public boolean isHasContradictoryEvidence() { return hasContradictoryEvidence; }
    public void setHasContradictoryEvidence(boolean hasContradictoryEvidence) { this.hasContradictoryEvidence = hasContradictoryEvidence; }

    public String getContradictoryEvidenceSummary() { return contradictoryEvidenceSummary; }
    public void setContradictoryEvidenceSummary(String contradictoryEvidenceSummary) { this.contradictoryEvidenceSummary = contradictoryEvidenceSummary; }

    public String getPriorityBreakdownJson() { return priorityBreakdownJson; }
    public void setPriorityBreakdownJson(String priorityBreakdownJson) { this.priorityBreakdownJson = priorityBreakdownJson; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public int getReopenedCount() { return reopenedCount; }
    public void setReopenedCount(int reopenedCount) { this.reopenedCount = reopenedCount; }

    public boolean isRecurring() { return recurring; }
    public void setRecurring(boolean recurring) { this.recurring = recurring; }

    public String getLastSeenRunId() { return lastSeenRunId; }
    public void setLastSeenRunId(String lastSeenRunId) { this.lastSeenRunId = lastSeenRunId; }

    public SeverityLevel getSeverity() {
        if (currentSeverity == null) return SeverityLevel.MEDIUM;
        try {
            return SeverityLevel.valueOf(currentSeverity.toUpperCase());
        } catch (Exception e) {
            return SeverityLevel.MEDIUM;
        }
    }

    public void setSeverity(SeverityLevel severity) {
        this.currentSeverity = severity != null ? severity.name() : "MEDIUM";
    }
}
