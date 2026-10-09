package com.modeldoctor.dto;

import com.modeldoctor.domain.ModelHealthState;

public class MonitoringRecalculateResponseDto {

    private String modelLineageId;
    private int baselineRunsEvaluated;
    private int activeAlertsCount;
    private int alertsUpdated;
    private boolean snapshotCreated;
    private ModelHealthState overallState;
    private Integer healthIndex;
    private boolean success;
    private String message;

    public MonitoringRecalculateResponseDto() {}

    public MonitoringRecalculateResponseDto(String modelLineageId, int baselineRunsEvaluated,
                                            int activeAlertsCount, int alertsUpdated,
                                            boolean snapshotCreated, ModelHealthState overallState,
                                            Integer healthIndex, boolean success, String message) {
        this.modelLineageId = modelLineageId;
        this.baselineRunsEvaluated = baselineRunsEvaluated;
        this.activeAlertsCount = activeAlertsCount;
        this.alertsUpdated = alertsUpdated;
        this.snapshotCreated = snapshotCreated;
        this.overallState = overallState;
        this.healthIndex = healthIndex;
        this.success = success;
        this.message = message;
    }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getBaselineRunsEvaluated() { return baselineRunsEvaluated; }
    public void setBaselineRunsEvaluated(int baselineRunsEvaluated) { this.baselineRunsEvaluated = baselineRunsEvaluated; }

    public int getActiveAlertsCount() { return activeAlertsCount; }
    public void setActiveAlertsCount(int activeAlertsCount) { this.activeAlertsCount = activeAlertsCount; }

    public int getAlertsUpdated() { return alertsUpdated; }
    public void setAlertsUpdated(int alertsUpdated) { this.alertsUpdated = alertsUpdated; }

    public boolean isSnapshotCreated() { return snapshotCreated; }
    public void setSnapshotCreated(boolean snapshotCreated) { this.snapshotCreated = snapshotCreated; }

    public ModelHealthState getOverallState() { return overallState; }
    public void setOverallState(ModelHealthState overallState) { this.overallState = overallState; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
