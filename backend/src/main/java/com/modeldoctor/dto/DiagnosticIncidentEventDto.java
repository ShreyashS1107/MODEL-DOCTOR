package com.modeldoctor.dto;

import java.time.Instant;

public class DiagnosticIncidentEventDto {

    private Long id;
    private Long incidentId;
    private String modelLineageId;
    private String previousState;
    private String newState;
    private String actor;
    private String action;
    private String reason;
    private String evidenceReference;
    private Instant timestamp;

    public DiagnosticIncidentEventDto() {}

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
