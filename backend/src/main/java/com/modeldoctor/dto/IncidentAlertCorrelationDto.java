package com.modeldoctor.dto;

import com.modeldoctor.domain.AlertLifecycleState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class IncidentAlertCorrelationDto {

    private Long alertId;
    private String alertFingerprint;
    private String alertType;
    private String targetKey;
    private String currentSeverity;
    private AlertLifecycleState lifecycleState;
    private int correlationScore;
    private List<String> correlationReasons = new ArrayList<>();
    private String sourceModule;
    private String metricName;
    private String triggerDescription;
    private Instant firstObservedAt;
    private Instant lastObservedAt;

    public IncidentAlertCorrelationDto() {}

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public String getAlertFingerprint() { return alertFingerprint; }
    public void setAlertFingerprint(String alertFingerprint) { this.alertFingerprint = alertFingerprint; }

    public String getAlertType() { return alertType; }
    public void setAlertType(String alertType) { this.alertType = alertType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public AlertLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(AlertLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }

    public int getCorrelationScore() { return correlationScore; }
    public void setCorrelationScore(int correlationScore) { this.correlationScore = correlationScore; }

    public List<String> getCorrelationReasons() { return correlationReasons; }
    public void setCorrelationReasons(List<String> correlationReasons) { this.correlationReasons = correlationReasons; }

    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getTriggerDescription() { return triggerDescription; }
    public void setTriggerDescription(String triggerDescription) { this.triggerDescription = triggerDescription; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }
}
