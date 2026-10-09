package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Persistent operational reliability profile snapshot for a model lineage.
 */
@Entity
@Table(name = "diagnostic_model_reliability", indexes = {
        @Index(name = "idx_rel_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_rel_state", columnList = "reliability_state"),
        @Index(name = "idx_rel_score", columnList = "reliability_score"),
        @Index(name = "idx_rel_updated", columnList = "updated_at")
})
public class DiagnosticModelReliability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false, unique = true)
    private String modelLineageId;

    @Column(name = "model_name", length = 255)
    private String modelName;

    @Column(name = "observation_window", length = 64, nullable = false)
    private String observationWindow = "ALL_AVAILABLE";

    @Column(name = "operational_run_count", nullable = false)
    private int operationalRunCount;

    @Column(name = "reliability_score", nullable = false)
    private int reliabilityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability_state", length = 32, nullable = false)
    private ModelReliabilityState reliabilityState;

    @Enumerated(EnumType.STRING)
    @Column(name = "reliability_confidence", length = 32, nullable = false)
    private DecisionConfidence reliabilityConfidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_health_state", length = 32)
    private ModelHealthState currentHealthState;

    @Column(name = "grade", length = 8, nullable = false)
    private String grade;

    @Enumerated(EnumType.STRING)
    @Column(name = "trend", length = 32, nullable = false)
    private ReliabilityTrend trend;

    @Column(name = "trend_slope")
    private Double trendSlope;

    @Column(name = "trend_r2")
    private Double trendR2;

    @Enumerated(EnumType.STRING)
    @Column(name = "governance_recommendation", length = 32, nullable = false)
    private GovernanceRecommendation governanceRecommendation;

    @Column(name = "recommendation_reason", columnDefinition = "TEXT")
    private String recommendationReason;

    @Column(name = "active_incident_count", nullable = false)
    private int activeIncidentCount;

    @Column(name = "critical_incident_count", nullable = false)
    private int criticalIncidentCount;

    @Column(name = "historical_incident_count", nullable = false)
    private int historicalIncidentCount;

    @Column(name = "recurring_incident_count", nullable = false)
    private int recurringIncidentCount;

    @Column(name = "reopened_incident_count", nullable = false)
    private int reopenedIncidentCount;

    @Column(name = "unresolved_incident_count", nullable = false)
    private int unresolvedIncidentCount;

    @Column(name = "remediation_count", nullable = false)
    private int remediationCount;

    @Column(name = "validated_remediation_count", nullable = false)
    private int validatedRemediationCount;

    @Column(name = "failed_remediation_count", nullable = false)
    private int failedRemediationCount;

    @Column(name = "remediation_durability_rate")
    private Double remediationDurabilityRate;

    @Column(name = "recovery_rate")
    private Double recoveryRate;

    @Column(name = "regression_rate")
    private Double regressionRate;

    @Column(name = "last_healthy_run_id", length = 64)
    private String lastHealthyRunId;

    @Column(name = "last_degraded_run_id", length = 64)
    private String lastDegradedRunId;

    @Column(name = "last_critical_run_id", length = 64)
    private String lastCriticalRunId;

    @Column(name = "last_incident_code", length = 64)
    private String lastIncidentCode;

    @Column(name = "score_breakdown_json", columnDefinition = "TEXT")
    private String scoreBreakdownJson;

    @Column(name = "risk_factors_json", columnDefinition = "TEXT")
    private String riskFactorsJson;

    @Column(name = "strengths_json", columnDefinition = "TEXT")
    private String strengthsJson;

    @Column(name = "trajectory_json", columnDefinition = "TEXT")
    private String trajectoryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DiagnosticModelReliability() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public String getObservationWindow() { return observationWindow; }
    public void setObservationWindow(String observationWindow) { this.observationWindow = observationWindow; }

    public int getOperationalRunCount() { return operationalRunCount; }
    public void setOperationalRunCount(int operationalRunCount) { this.operationalRunCount = operationalRunCount; }

    public int getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(int reliabilityScore) { this.reliabilityScore = reliabilityScore; }

    public ModelReliabilityState getReliabilityState() { return reliabilityState; }
    public void setReliabilityState(ModelReliabilityState reliabilityState) { this.reliabilityState = reliabilityState; }

    public DecisionConfidence getReliabilityConfidence() { return reliabilityConfidence; }
    public void setReliabilityConfidence(DecisionConfidence reliabilityConfidence) { this.reliabilityConfidence = reliabilityConfidence; }

    public ModelHealthState getCurrentHealthState() { return currentHealthState; }
    public void setCurrentHealthState(ModelHealthState currentHealthState) { this.currentHealthState = currentHealthState; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }

    public ReliabilityTrend getTrend() { return trend; }
    public void setTrend(ReliabilityTrend trend) { this.trend = trend; }

    public Double getTrendSlope() { return trendSlope; }
    public void setTrendSlope(Double trendSlope) { this.trendSlope = trendSlope; }

    public Double getTrendR2() { return trendR2; }
    public void setTrendR2(Double trendR2) { this.trendR2 = trendR2; }

    public GovernanceRecommendation getGovernanceRecommendation() { return governanceRecommendation; }
    public void setGovernanceRecommendation(GovernanceRecommendation governanceRecommendation) { this.governanceRecommendation = governanceRecommendation; }

    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String recommendationReason) { this.recommendationReason = recommendationReason; }

    public int getActiveIncidentCount() { return activeIncidentCount; }
    public void setActiveIncidentCount(int activeIncidentCount) { this.activeIncidentCount = activeIncidentCount; }

    public int getCriticalIncidentCount() { return criticalIncidentCount; }
    public void setCriticalIncidentCount(int criticalIncidentCount) { this.criticalIncidentCount = criticalIncidentCount; }

    public int getHistoricalIncidentCount() { return historicalIncidentCount; }
    public void setHistoricalIncidentCount(int historicalIncidentCount) { this.historicalIncidentCount = historicalIncidentCount; }

    public int getRecurringIncidentCount() { return recurringIncidentCount; }
    public void setRecurringIncidentCount(int recurringIncidentCount) { this.recurringIncidentCount = recurringIncidentCount; }

    public int getReopenedIncidentCount() { return reopenedIncidentCount; }
    public void setReopenedIncidentCount(int reopenedIncidentCount) { this.reopenedIncidentCount = reopenedIncidentCount; }

    public int getUnresolvedIncidentCount() { return unresolvedIncidentCount; }
    public void setUnresolvedIncidentCount(int unresolvedIncidentCount) { this.unresolvedIncidentCount = unresolvedIncidentCount; }

    public int getRemediationCount() { return remediationCount; }
    public void setRemediationCount(int remediationCount) { this.remediationCount = remediationCount; }

    public int getValidatedRemediationCount() { return validatedRemediationCount; }
    public void setValidatedRemediationCount(int validatedRemediationCount) { this.validatedRemediationCount = validatedRemediationCount; }

    public int getFailedRemediationCount() { return failedRemediationCount; }
    public void setFailedRemediationCount(int failedRemediationCount) { this.failedRemediationCount = failedRemediationCount; }

    public Double getRemediationDurabilityRate() { return remediationDurabilityRate; }
    public void setRemediationDurabilityRate(Double remediationDurabilityRate) { this.remediationDurabilityRate = remediationDurabilityRate; }

    public Double getRecoveryRate() { return recoveryRate; }
    public void setRecoveryRate(Double recoveryRate) { this.recoveryRate = recoveryRate; }

    public Double getRegressionRate() { return regressionRate; }
    public void setRegressionRate(Double regressionRate) { this.regressionRate = regressionRate; }

    public String getLastHealthyRunId() { return lastHealthyRunId; }
    public void setLastHealthyRunId(String lastHealthyRunId) { this.lastHealthyRunId = lastHealthyRunId; }

    public String getLastDegradedRunId() { return lastDegradedRunId; }
    public void setLastDegradedRunId(String lastDegradedRunId) { this.lastDegradedRunId = lastDegradedRunId; }

    public String getLastCriticalRunId() { return lastCriticalRunId; }
    public void setLastCriticalRunId(String lastCriticalRunId) { this.lastCriticalRunId = lastCriticalRunId; }

    public String getLastIncidentCode() { return lastIncidentCode; }
    public void setLastIncidentCode(String lastIncidentCode) { this.lastIncidentCode = lastIncidentCode; }

    public String getScoreBreakdownJson() { return scoreBreakdownJson; }
    public void setScoreBreakdownJson(String scoreBreakdownJson) { this.scoreBreakdownJson = scoreBreakdownJson; }

    public String getRiskFactorsJson() { return riskFactorsJson; }
    public void setRiskFactorsJson(String riskFactorsJson) { this.riskFactorsJson = riskFactorsJson; }

    public String getStrengthsJson() { return strengthsJson; }
    public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }

    public String getTrajectoryJson() { return trajectoryJson; }
    public void setTrajectoryJson(String trajectoryJson) { this.trajectoryJson = trajectoryJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
