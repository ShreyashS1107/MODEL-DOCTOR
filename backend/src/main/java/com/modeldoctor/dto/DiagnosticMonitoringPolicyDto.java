package com.modeldoctor.dto;

import com.modeldoctor.domain.HealthDimension;
import java.time.Instant;
import java.util.List;

public class DiagnosticMonitoringPolicyDto {

    private String modelLineageId;
    private int policyVersion;
    private boolean enabled;
    private List<HealthDimension> requiredDimensions;
    private String observationWindow;
    private int minBaselineRunsRequired;
    private int alertPersistenceThreshold;
    private int recoveryConsecutiveRuns;
    private int alertCooldownRuns;
    private double hysteresisMarginPct;
    private boolean experimentOverlayEnabled;
    private String healthEvaluationMode;
    private Instant createdAt;
    private Instant updatedAt;
    private String updatedBy;

    public DiagnosticMonitoringPolicyDto() {}

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<HealthDimension> getRequiredDimensions() { return requiredDimensions; }
    public void setRequiredDimensions(List<HealthDimension> requiredDimensions) { this.requiredDimensions = requiredDimensions; }

    public String getObservationWindow() { return observationWindow; }
    public void setObservationWindow(String observationWindow) { this.observationWindow = observationWindow; }

    public int getMinBaselineRunsRequired() { return minBaselineRunsRequired; }
    public void setMinBaselineRunsRequired(int minBaselineRunsRequired) { this.minBaselineRunsRequired = minBaselineRunsRequired; }

    public int getAlertPersistenceThreshold() { return alertPersistenceThreshold; }
    public void setAlertPersistenceThreshold(int alertPersistenceThreshold) { this.alertPersistenceThreshold = alertPersistenceThreshold; }

    public int getRecoveryConsecutiveRuns() { return recoveryConsecutiveRuns; }
    public void setRecoveryConsecutiveRuns(int recoveryConsecutiveRuns) { this.recoveryConsecutiveRuns = recoveryConsecutiveRuns; }

    public int getAlertCooldownRuns() { return alertCooldownRuns; }
    public void setAlertCooldownRuns(int alertCooldownRuns) { this.alertCooldownRuns = alertCooldownRuns; }

    public double getHysteresisMarginPct() { return hysteresisMarginPct; }
    public void setHysteresisMarginPct(double hysteresisMarginPct) { this.hysteresisMarginPct = hysteresisMarginPct; }

    public boolean isExperimentOverlayEnabled() { return experimentOverlayEnabled; }
    public void setExperimentOverlayEnabled(boolean experimentOverlayEnabled) { this.experimentOverlayEnabled = experimentOverlayEnabled; }

    public String getHealthEvaluationMode() { return healthEvaluationMode; }
    public void setHealthEvaluationMode(String healthEvaluationMode) { this.healthEvaluationMode = healthEvaluationMode; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
}
