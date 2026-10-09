package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.GovernanceRecommendation;
import com.modeldoctor.domain.ModelHealthState;
import com.modeldoctor.domain.ModelReliabilityState;
import com.modeldoctor.domain.ReliabilityTrend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ModelReliabilityProfileDto {

    private String modelLineageId;
    private String modelName;
    private String observationWindow = "ALL_AVAILABLE";
    private int operationalRunCount;
    private int reliabilityScore;
    private ModelReliabilityState reliabilityState;
    private DecisionConfidence reliabilityConfidence;
    private ModelHealthState currentHealthState;
    private String grade;
    private ReliabilityTrend trend;
    private Double trendSlope;
    private Double trendR2;
    private GovernanceRecommendation governanceRecommendation;
    private String recommendationReason;

    private ReliabilityScoreBreakdownDto scoreBreakdown;
    private List<ReliabilityTrajectoryPointDto> trajectory = new ArrayList<>();
    private RecoveryProfileDto recoveryProfile;
    private RemediationDurabilitySummaryDto remediationDurability;
    private List<ReliabilityRiskFactorDto> riskFactors = new ArrayList<>();
    private List<ReliabilityStrengthDto> strengths = new ArrayList<>();

    private int activeIncidentCount;
    private int criticalIncidentCount;
    private int historicalIncidentCount;
    private int recurringIncidentCount;
    private int reopenedIncidentCount;
    private int unresolvedIncidentCount;

    private int remediationCount;
    private int validatedRemediationCount;
    private int failedRemediationCount;
    private int degradedDimensionsCount;
    private Double recoveryRate;
    private Double remediationDurabilityRate;

    private String lastHealthyRunId;
    private String lastDegradedRunId;
    private String lastCriticalRunId;
    private String lastIncidentCode;

    private List<DiagnosticReliabilityEventDto> recentEvents = new ArrayList<>();
    private Instant updatedAt;

    public ModelReliabilityProfileDto() {}

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

    public ReliabilityScoreBreakdownDto getScoreBreakdown() { return scoreBreakdown; }
    public void setScoreBreakdown(ReliabilityScoreBreakdownDto scoreBreakdown) { this.scoreBreakdown = scoreBreakdown; }

    public List<ReliabilityTrajectoryPointDto> getTrajectory() { return trajectory; }
    public void setTrajectory(List<ReliabilityTrajectoryPointDto> trajectory) { this.trajectory = trajectory; }

    public RecoveryProfileDto getRecoveryProfile() { return recoveryProfile; }
    public void setRecoveryProfile(RecoveryProfileDto recoveryProfile) { this.recoveryProfile = recoveryProfile; }

    public RemediationDurabilitySummaryDto getRemediationDurability() { return remediationDurability; }
    public void setRemediationDurability(RemediationDurabilitySummaryDto remediationDurability) { this.remediationDurability = remediationDurability; }

    public List<ReliabilityRiskFactorDto> getRiskFactors() { return riskFactors; }
    public void setRiskFactors(List<ReliabilityRiskFactorDto> riskFactors) { this.riskFactors = riskFactors; }

    public List<ReliabilityStrengthDto> getStrengths() { return strengths; }
    public void setStrengths(List<ReliabilityStrengthDto> strengths) { this.strengths = strengths; }

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

    public int getDegradedDimensionsCount() { return degradedDimensionsCount; }
    public void setDegradedDimensionsCount(int degradedDimensionsCount) { this.degradedDimensionsCount = degradedDimensionsCount; }

    public Double getRecoveryRate() { return recoveryRate; }
    public void setRecoveryRate(Double recoveryRate) { this.recoveryRate = recoveryRate; }

    public Double getRemediationDurabilityRate() { return remediationDurabilityRate; }
    public void setRemediationDurabilityRate(Double remediationDurabilityRate) { this.remediationDurabilityRate = remediationDurabilityRate; }

    public String getLastHealthyRunId() { return lastHealthyRunId; }
    public void setLastHealthyRunId(String lastHealthyRunId) { this.lastHealthyRunId = lastHealthyRunId; }

    public String getLastDegradedRunId() { return lastDegradedRunId; }
    public void setLastDegradedRunId(String lastDegradedRunId) { this.lastDegradedRunId = lastDegradedRunId; }

    public String getLastCriticalRunId() { return lastCriticalRunId; }
    public void setLastCriticalRunId(String lastCriticalRunId) { this.lastCriticalRunId = lastCriticalRunId; }

    public String getLastIncidentCode() { return lastIncidentCode; }
    public void setLastIncidentCode(String lastIncidentCode) { this.lastIncidentCode = lastIncidentCode; }

    public List<DiagnosticReliabilityEventDto> getRecentEvents() { return recentEvents; }
    public void setRecentEvents(List<DiagnosticReliabilityEventDto> recentEvents) { this.recentEvents = recentEvents; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
