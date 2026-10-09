package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_incident_events", indexes = {
        @Index(name = "idx_inc_event_incident", columnList = "incident_id"),
        @Index(name = "idx_inc_event_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_inc_event_timestamp", columnList = "timestamp")
})
public class DiagnosticIncidentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "previous_state", length = 32)
    private String previousState;

    @Column(name = "new_state", length = 32, nullable = false)
    private String newState;

    @Column(name = "actor", length = 64, nullable = false)
    private String actor = "USER";

    @Column(name = "action", length = 64, nullable = false)
    private String action;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "evidence_reference", length = 255)
    private String evidenceReference;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    public DiagnosticIncidentEvent() {}

    public DiagnosticIncidentEvent(Long incidentId, String modelLineageId, String previousState, String newState,
                                   String actor, String action, String reason, String evidenceReference) {
        this.incidentId = incidentId;
        this.modelLineageId = modelLineageId;
        this.previousState = previousState;
        this.newState = newState;
        this.actor = actor;
        this.action = action;
        this.reason = reason;
        this.evidenceReference = evidenceReference;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

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

    public String getEvidenceReference() { return evidenceReference; }
    public void setEvidenceReference(String evidenceReference) { this.evidenceReference = evidenceReference; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
