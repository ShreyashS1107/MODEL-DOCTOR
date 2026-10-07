package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_run_events", indexes = {
    @Index(name = "idx_event_run_id", columnList = "run_id"),
    @Index(name = "idx_event_timestamp", columnList = "timestamp")
})
public class DiagnosticRunEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "module", length = 64)
    private String module;

    @Column(name = "event_type", length = 64, nullable = false)
    private String eventType;

    @Column(name = "message", columnDefinition = "TEXT")
    private String message;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    public DiagnosticRunEvent() {}

    public DiagnosticRunEvent(Long id, String runId, String module, String eventType, String message, Instant timestamp) {
        this.id = id;
        this.runId = runId;
        this.module = module;
        this.eventType = eventType;
        this.message = message;
        this.timestamp = timestamp;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private Long id;
        private String runId;
        private String module;
        private String eventType;
        private String message;
        private Instant timestamp;

        public Builder id(Long id) { this.id = id; return this; }
        public Builder runId(String runId) { this.runId = runId; return this; }
        public Builder module(String module) { this.module = module; return this; }
        public Builder eventType(String eventType) { this.eventType = eventType; return this; }
        public Builder message(String message) { this.message = message; return this; }
        public Builder timestamp(Instant timestamp) { this.timestamp = timestamp; return this; }

        public DiagnosticRunEvent build() {
            return new DiagnosticRunEvent(id, runId, module, eventType, message, timestamp != null ? timestamp : Instant.now());
        }
    }
}
