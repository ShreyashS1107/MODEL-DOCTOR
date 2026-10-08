package com.modeldoctor.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ModelLineageHistoryDto {
    private String modelLineageId;
    private String window = "ALL_AVAILABLE"; // "LAST_3", "LAST_5", "LAST_10", "ALL_AVAILABLE"

    // High-level telemetry HUD
    private int totalRunsCount = 0;
    private int baselineRunsCount = 0;
    private int experimentRunsCount = 0;
    private int activeIssuesCount = 0;
    private int persistentIssuesCount = 0;
    private int emergingIssuesCount = 0;
    private int recurringIssuesCount = 0;
    private int activeAlertsCount = 0;
    private int criticalAlertsCount = 0;
    private int changePointsCount = 0;

    private Instant firstObservedAt;
    private Instant lastObservedAt;

    // Ordered historical runs (operational baseline + experiment overlays)
    private List<RunSummaryDto> orderedRuns = new ArrayList<>();

    // Metric time series & trend models
    private List<TemporalMetricHistoryDto> metricHistories = new ArrayList<>();

    // Issue tracks
    private List<IssueTrackDto> issueTracks = new ArrayList<>();

    // Temporal alerts
    private List<TemporalAlertDto> alerts = new ArrayList<>();

    // Change points
    private List<ChangePointDto> changePoints = new ArrayList<>();

    // Remediation durability assessments
    private List<RemediationDurabilityDto> remediationDurability = new ArrayList<>();

    public ModelLineageHistoryDto() {}

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getWindow() { return window; }
    public void setWindow(String window) { this.window = window; }

    public int getTotalRunsCount() { return totalRunsCount; }
    public void setTotalRunsCount(int totalRunsCount) { this.totalRunsCount = totalRunsCount; }

    public int getBaselineRunsCount() { return baselineRunsCount; }
    public void setBaselineRunsCount(int baselineRunsCount) { this.baselineRunsCount = baselineRunsCount; }

    public int getExperimentRunsCount() { return experimentRunsCount; }
    public void setExperimentRunsCount(int experimentRunsCount) { this.experimentRunsCount = experimentRunsCount; }

    public int getActiveIssuesCount() { return activeIssuesCount; }
    public void setActiveIssuesCount(int activeIssuesCount) { this.activeIssuesCount = activeIssuesCount; }

    public int getPersistentIssuesCount() { return persistentIssuesCount; }
    public void setPersistentIssuesCount(int persistentIssuesCount) { this.persistentIssuesCount = persistentIssuesCount; }

    public int getEmergingIssuesCount() { return emergingIssuesCount; }
    public void setEmergingIssuesCount(int emergingIssuesCount) { this.emergingIssuesCount = emergingIssuesCount; }

    public int getRecurringIssuesCount() { return recurringIssuesCount; }
    public void setRecurringIssuesCount(int recurringIssuesCount) { this.recurringIssuesCount = recurringIssuesCount; }

    public int getActiveAlertsCount() { return activeAlertsCount; }
    public void setActiveAlertsCount(int activeAlertsCount) { this.activeAlertsCount = activeAlertsCount; }

    public int getCriticalAlertsCount() { return criticalAlertsCount; }
    public void setCriticalAlertsCount(int criticalAlertsCount) { this.criticalAlertsCount = criticalAlertsCount; }

    public int getChangePointsCount() { return changePointsCount; }
    public void setChangePointsCount(int changePointsCount) { this.changePointsCount = changePointsCount; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public List<RunSummaryDto> getOrderedRuns() { return orderedRuns; }
    public void setOrderedRuns(List<RunSummaryDto> orderedRuns) { this.orderedRuns = orderedRuns; }

    public List<TemporalMetricHistoryDto> getMetricHistories() { return metricHistories; }
    public void setMetricHistories(List<TemporalMetricHistoryDto> metricHistories) { this.metricHistories = metricHistories; }

    public List<IssueTrackDto> getIssueTracks() { return issueTracks; }
    public void setIssueTracks(List<IssueTrackDto> issueTracks) { this.issueTracks = issueTracks; }

    public List<TemporalAlertDto> getAlerts() { return alerts; }
    public void setAlerts(List<TemporalAlertDto> alerts) { this.alerts = alerts; }

    public List<ChangePointDto> getChangePoints() { return changePoints; }
    public void setChangePoints(List<ChangePointDto> changePoints) { this.changePoints = changePoints; }

    public List<RemediationDurabilityDto> getRemediationDurability() { return remediationDurability; }
    public void setRemediationDurability(List<RemediationDurabilityDto> remediationDurability) { this.remediationDurability = remediationDurability; }
}
