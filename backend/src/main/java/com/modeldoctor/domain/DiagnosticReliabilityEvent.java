package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Historical governance event in a model lineage's longitudinal operational timeline.
 */
@Entity
@Table(name = "diagnostic_reliability_events", indexes = {
        @Index(name = "idx_rel_evt_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_rel_evt_type", columnList = "event_type"),
        @Index(name = "idx_rel_evt_timestamp", columnList = "timestamp"),
        @Index(name = "idx_rel_evt_source", columnList = "source_type, source_id")
})
public class DiagnosticReliabilityEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 64, nullable = false)
    private ReliabilityEventType eventType;

    @Column(name = "run_id", length = 64)
    private String runId;

    @Column(name = "source_type", length = 64, nullable = false)
    private String sourceType;

    @Column(name = "source_id", length = 128)
    private String sourceId;

    @Column(name = "severity", length = 32, nullable = false)
    private String severity = "INFO";

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    private String summary;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public DiagnosticReliabilityEvent() {}

    public DiagnosticReliabilityEvent(String modelLineageId, ReliabilityEventType eventType, String runId,
                                      String sourceType, String sourceId, String severity, String summary, Instant timestamp) {
        this.modelLineageId = modelLineageId;
        this.eventType = eventType;
        this.runId = runId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.severity = severity != null ? severity : "INFO";
        this.summary = summary;
        this.timestamp = timestamp != null ? timestamp : Instant.now();
    }

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
