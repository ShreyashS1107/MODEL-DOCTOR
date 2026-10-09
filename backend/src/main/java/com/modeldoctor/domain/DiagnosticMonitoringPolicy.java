package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_monitoring_policies", indexes = {
        @Index(name = "idx_policy_lineage", columnList = "model_lineage_id", unique = true),
        @Index(name = "idx_policy_enabled", columnList = "enabled")
})
public class DiagnosticMonitoringPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false, unique = true)
    private String modelLineageId;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion = 1;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "required_dimensions_json", columnDefinition = "TEXT")
    private String requiredDimensionsJson;

    @Column(name = "observation_window", length = 64)
    private String observationWindow = "ALL_AVAILABLE";

    @Column(name = "min_baseline_runs_required", nullable = false)
    private int minBaselineRunsRequired = 3;

    @Column(name = "alert_persistence_threshold", nullable = false)
    private int alertPersistenceThreshold = 2;

    @Column(name = "recovery_consecutive_runs", nullable = false)
    private int recoveryConsecutiveRuns = 2;

    @Column(name = "alert_cooldown_runs", nullable = false)
    private int alertCooldownRuns = 1;

    @Column(name = "hysteresis_margin_pct", nullable = false)
    private double hysteresisMarginPct = 0.05;

    @Column(name = "experiment_overlay_enabled", nullable = false)
    private boolean experimentOverlayEnabled = true;

    @Column(name = "health_evaluation_mode", length = 64, nullable = false)
    private String healthEvaluationMode = "DETERMINISTIC_V1";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by", length = 128)
    private String updatedBy = "SYSTEM";

    public DiagnosticMonitoringPolicy() {}

    public DiagnosticMonitoringPolicy(String modelLineageId) {
        this.modelLineageId = modelLineageId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getRequiredDimensionsJson() { return requiredDimensionsJson; }
    public void setRequiredDimensionsJson(String requiredDimensionsJson) { this.requiredDimensionsJson = requiredDimensionsJson; }

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
