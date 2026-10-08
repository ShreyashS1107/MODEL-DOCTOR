package com.modeldoctor.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IssueTrackDto {
    private Long id;
    private String modelLineageId;
    private String trackFingerprint;
    private String targetType;
    private String targetKey;
    private Instant firstSeenAt;
    private Instant lastSeenAt;
    private String firstSeenRunId;
    private String lastSeenRunId;
    private Integer observationCount;
    private Integer consecutiveCount;
    private String currentSeverity; // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    private String peakSeverity;
    private String status; // "EMERGING", "PERSISTENT", "RECURRING", "TRANSIENT", "RECOVERED", "ESCALATING", "DEESCALATING"
    private List<String> modulesInvolved = new ArrayList<>();
    private List<String> metricNames = new ArrayList<>();
    private List<String> runIds = new ArrayList<>();
    private String historyJson;
    private String remediationHistoryJson;
    private String durabilityStatus; // "SUSTAINED", "TEMPORARY", "FAILED_TO_SUSTAIN", "INSUFFICIENT_FOLLOWUP", "NOT_APPLICABLE"

    public IssueTrackDto() {}

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

    public List<String> getModulesInvolved() { return modulesInvolved; }
    public void setModulesInvolved(List<String> modulesInvolved) { this.modulesInvolved = modulesInvolved; }

    public List<String> getMetricNames() { return metricNames; }
    public void setMetricNames(List<String> metricNames) { this.metricNames = metricNames; }

    public List<String> getRunIds() { return runIds; }
    public void setRunIds(List<String> runIds) { this.runIds = runIds; }

    public String getHistoryJson() { return historyJson; }
    public void setHistoryJson(String historyJson) { this.historyJson = historyJson; }

    public String getRemediationHistoryJson() { return remediationHistoryJson; }
    public void setRemediationHistoryJson(String remediationHistoryJson) { this.remediationHistoryJson = remediationHistoryJson; }

    public String getDurabilityStatus() { return durabilityStatus; }
    public void setDurabilityStatus(String durabilityStatus) { this.durabilityStatus = durabilityStatus; }
}
