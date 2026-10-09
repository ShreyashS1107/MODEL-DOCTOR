package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_alert_events", indexes = {
        @Index(name = "idx_alert_event_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_alert_event_alert", columnList = "alert_id"),
        @Index(name = "idx_alert_event_fingerprint", columnList = "alert_fingerprint"),
        @Index(name = "idx_alert_event_timestamp", columnList = "timestamp")
})
public class DiagnosticAlertEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "alert_fingerprint", length = 255, nullable = false)
    private String alertFingerprint;

    @Column(name = "previous_state", length = 32)
    private String previousState;

    @Column(name = "new_state", length = 32, nullable = false)
    private String newState;

    @Column(name = "actor", length = 128, nullable = false)
    private String actor = "SYSTEM"; // "SYSTEM", "USER", etc.

    @Column(name = "action", length = 64, nullable = false)
    private String action; // "CREATE", "ACKNOWLEDGE", "INVESTIGATE", "SUPPRESS", "RESOLVE", "REOPEN", "ESCALATE", "DEESCALATE"

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public DiagnosticAlertEvent() {}

    public DiagnosticAlertEvent(String modelLineageId, Long alertId, String alertFingerprint,
                                String previousState, String newState, String actor,
                                String action, String reason) {
        this.modelLineageId = modelLineageId;
        this.alertId = alertId;
        this.alertFingerprint = alertFingerprint;
        this.previousState = previousState;
        this.newState = newState;
        this.actor = actor != null ? actor : "SYSTEM";
        this.action = action;
        this.reason = reason;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public String getAlertFingerprint() { return alertFingerprint; }
    public void setAlertFingerprint(String alertFingerprint) { this.alertFingerprint = alertFingerprint; }

    public String getPreviousState() { return previousState; }
    public void setPreviousState(String previousState) { this.previousState = previousState; }

    public String getNewState() { return newState; }
    public void setNewState(String newState) { this.newState = newState; }

    public String getActor() { return actor; }
    public void setActor(String actor) { this.actor = actor; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
