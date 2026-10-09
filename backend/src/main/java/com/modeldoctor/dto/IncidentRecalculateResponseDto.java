package com.modeldoctor.dto;

public class IncidentRecalculateResponseDto {

    private String modelLineageId;
    private int activeAlertsCount;
    private int incidentsFormedCount;
    private int incidentsUpdatedCount;
    private String topIncidentCode;
    private boolean success;
    private String message;

    public IncidentRecalculateResponseDto() {}

    public IncidentRecalculateResponseDto(String modelLineageId, int activeAlertsCount, int incidentsFormedCount,
                                         int incidentsUpdatedCount, String topIncidentCode, boolean success, String message) {
        this.modelLineageId = modelLineageId;
        this.activeAlertsCount = activeAlertsCount;
        this.incidentsFormedCount = incidentsFormedCount;
        this.incidentsUpdatedCount = incidentsUpdatedCount;
        this.topIncidentCode = topIncidentCode;
        this.success = success;
        this.message = message;
    }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getActiveAlertsCount() { return activeAlertsCount; }
    public void setActiveAlertsCount(int activeAlertsCount) { this.activeAlertsCount = activeAlertsCount; }

    public int getIncidentsFormedCount() { return incidentsFormedCount; }
    public void setIncidentsFormedCount(int incidentsFormedCount) { this.incidentsFormedCount = incidentsFormedCount; }

    public int getIncidentsUpdatedCount() { return incidentsUpdatedCount; }
    public void setIncidentsUpdatedCount(int incidentsUpdatedCount) { this.incidentsUpdatedCount = incidentsUpdatedCount; }

    public String getTopIncidentCode() { return topIncidentCode; }
    public void setTopIncidentCode(String topIncidentCode) { this.topIncidentCode = topIncidentCode; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
