package com.modeldoctor.dto;

public class TemporalRecalculateResponseDto {
    private String modelLineageId;
    private int runsProcessed;
    private int observationsExtracted;
    private int issueTracksBuilt;
    private int alertsGenerated;
    private int changePointsDetected;
    private boolean success;
    private String message;

    public TemporalRecalculateResponseDto() {}

    public TemporalRecalculateResponseDto(String modelLineageId, int runsProcessed, int observationsExtracted,
                                          int issueTracksBuilt, int alertsGenerated, int changePointsDetected,
                                          boolean success, String message) {
        this.modelLineageId = modelLineageId;
        this.runsProcessed = runsProcessed;
        this.observationsExtracted = observationsExtracted;
        this.issueTracksBuilt = issueTracksBuilt;
        this.alertsGenerated = alertsGenerated;
        this.changePointsDetected = changePointsDetected;
        this.success = success;
        this.message = message;
    }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getRunsProcessed() { return runsProcessed; }
    public void setRunsProcessed(int runsProcessed) { this.runsProcessed = runsProcessed; }

    public int getObservationsExtracted() { return observationsExtracted; }
    public void setObservationsExtracted(int observationsExtracted) { this.observationsExtracted = observationsExtracted; }

    public int getIssueTracksBuilt() { return issueTracksBuilt; }
    public void setIssueTracksBuilt(int issueTracksBuilt) { this.issueTracksBuilt = issueTracksBuilt; }

    public int getAlertsGenerated() { return alertsGenerated; }
    public void setAlertsGenerated(int alertsGenerated) { this.alertsGenerated = alertsGenerated; }

    public int getChangePointsDetected() { return changePointsDetected; }
    public void setChangePointsDetected(int changePointsDetected) { this.changePointsDetected = changePointsDetected; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
