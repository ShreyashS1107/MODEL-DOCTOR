package com.modeldoctor.dto;

import com.modeldoctor.domain.ReliabilityEventType;
import java.time.Instant;

public class DiagnosticReliabilityEventDto {

    private Long id;
    private String modelLineageId;
    private ReliabilityEventType eventType;
    private String runId;
    private String sourceType;
    private String sourceId;
    private String severity;
    private String summary;
    private Instant timestamp;

    public DiagnosticReliabilityEventDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public ReliabilityEventType getEventType() { return eventType; }
    public void setEventType(ReliabilityEventType eventType) { this.eventType = eventType; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
