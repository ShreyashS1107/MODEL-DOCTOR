package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_issue_tracks", indexes = {
        @Index(name = "idx_issue_track_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_issue_track_target", columnList = "model_lineage_id, target_key"),
        @Index(name = "idx_issue_track_status", columnList = "model_lineage_id, status")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_issue_track_identity", columnNames = {"model_lineage_id", "track_fingerprint"})
})
public class DiagnosticIssueTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "track_fingerprint", length = 255, nullable = false)
    private String trackFingerprint; // e.g. "FEATURE::income" or "SUBGROUP::gender=female"

    @Column(name = "target_type", length = 64, nullable = false)
    private String targetType;

    @Column(name = "target_key", length = 128, nullable = false)
    private String targetKey;

    @Column(name = "first_seen_at", nullable = false)
    private Instant firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "first_seen_run_id", length = 64)
    private String firstSeenRunId;

    @Column(name = "last_seen_run_id", length = 64)
    private String lastSeenRunId;

    @Column(name = "observation_count", nullable = false)
    private Integer observationCount = 0;

    @Column(name = "consecutive_count", nullable = false)
    private Integer consecutiveCount = 0;

    @Column(name = "current_severity", length = 32, nullable = false)
    private String currentSeverity = "LOW";

    @Column(name = "peak_severity", length = 32, nullable = false)
    private String peakSeverity = "LOW";

    @Column(name = "status", length = 32, nullable = false)
    private String status = "EMERGING"; // "EMERGING", "PERSISTENT", "RECURRING", "TRANSIENT", "RECOVERED", "ESCALATING", "DEESCALATING"

    @Column(name = "modules_involved_json", columnDefinition = "TEXT")
    private String modulesInvolvedJson; // JSON array of module names

    @Column(name = "metric_names_json", columnDefinition = "TEXT")
    private String metricNamesJson; // JSON array of metric names

    @Column(name = "run_ids_json", columnDefinition = "TEXT")
    private String runIdsJson; // JSON array of run IDs

    @Column(name = "history_json", columnDefinition = "TEXT")
    private String historyJson; // JSON array of state sequence [{runId, timestamp, severity, metricValue, status}]

    @Column(name = "remediation_history_json", columnDefinition = "TEXT")
    private String remediationHistoryJson; // JSON array of remediation linkages

    @Column(name = "durability_status", length = 32)
    private String durabilityStatus = "NOT_APPLICABLE"; // "SUSTAINED", "TEMPORARY", "FAILED_TO_SUSTAIN", "INSUFFICIENT_FOLLOWUP", "NOT_APPLICABLE"

    public DiagnosticIssueTrack() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getTrackFingerprint() { return trackFingerprint; }
    public void setTrackFingerprint(String trackFingerprint) { this.trackFingerprint = trackFingerprint; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public Instant getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(Instant firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public Instant getLastSeenAt() { return lastSeenAt; }
    public void setLastSeenAt(Instant lastSeenAt) { this.lastSeenAt = lastSeenAt; }

    public String getFirstSeenRunId() { return firstSeenRunId; }
    public void setFirstSeenRunId(String firstSeenRunId) { this.firstSeenRunId = firstSeenRunId; }

    public String getLastSeenRunId() { return lastSeenRunId; }
    public void setLastSeenRunId(String lastSeenRunId) { this.lastSeenRunId = lastSeenRunId; }

    public Integer getObservationCount() { return observationCount; }
    public void setObservationCount(Integer observationCount) { this.observationCount = observationCount; }

    public Integer getConsecutiveCount() { return consecutiveCount; }
    public void setConsecutiveCount(Integer consecutiveCount) { this.consecutiveCount = consecutiveCount; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public String getPeakSeverity() { return peakSeverity; }
    public void setPeakSeverity(String peakSeverity) { this.peakSeverity = peakSeverity; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getModulesInvolvedJson() { return modulesInvolvedJson; }
    public void setModulesInvolvedJson(String modulesInvolvedJson) { this.modulesInvolvedJson = modulesInvolvedJson; }

    public String getMetricNamesJson() { return metricNamesJson; }
    public void setMetricNamesJson(String metricNamesJson) { this.metricNamesJson = metricNamesJson; }

    public String getRunIdsJson() { return runIdsJson; }
    public void setRunIdsJson(String runIdsJson) { this.runIdsJson = runIdsJson; }

    public String getHistoryJson() { return historyJson; }
    public void setHistoryJson(String historyJson) { this.historyJson = historyJson; }

    public String getRemediationHistoryJson() { return remediationHistoryJson; }
    public void setRemediationHistoryJson(String remediationHistoryJson) { this.remediationHistoryJson = remediationHistoryJson; }

    public String getDurabilityStatus() { return durabilityStatus; }
    public void setDurabilityStatus(String durabilityStatus) { this.durabilityStatus = durabilityStatus; }
}
