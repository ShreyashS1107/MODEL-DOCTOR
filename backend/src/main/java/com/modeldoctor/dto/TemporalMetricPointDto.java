package com.modeldoctor.dto;

import java.time.Instant;

public class TemporalMetricPointDto {
    private String runId;
    private String runType; // "BASELINE", "EXPERIMENT"
    private Integer runIndex;
    private Instant timestamp;
    private Double value;
    private String severity; // "LOW", "MEDIUM", "HIGH", "CRITICAL", "NONE"
    private Double threshold;

    public TemporalMetricPointDto() {}

    public TemporalMetricPointDto(String runId, String runType, Integer runIndex, Instant timestamp, Double value, String severity, Double threshold) {
        this.runId = runId;
        this.runType = runType;
        this.runIndex = runIndex;
        this.timestamp = timestamp;
        this.value = value;
        this.severity = severity;
        this.threshold = threshold;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public Integer getRunIndex() { return runIndex; }
    public void setRunIndex(Integer runIndex) { this.runIndex = runIndex; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public Double getValue() { return value; }
    public void setValue(Double value) { this.value = value; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }
}
