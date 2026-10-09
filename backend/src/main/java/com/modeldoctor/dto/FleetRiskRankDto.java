package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.GovernanceRecommendation;
import com.modeldoctor.domain.ModelHealthState;
import com.modeldoctor.domain.ModelReliabilityState;
import com.modeldoctor.domain.ReliabilityTrend;
import java.util.ArrayList;
import java.util.List;

public class FleetRiskRankDto {

    private int rank;
    private String modelLineageId;
    private String modelName;
    private int reliabilityScore;
    private ModelReliabilityState reliabilityState;
    private DecisionConfidence reliabilityConfidence;
    private ModelHealthState currentHealthState;
    private String grade;
    private ReliabilityTrend trend;
    private int activeIncidentsCount;
    private int criticalIncidentsCount;
    private GovernanceRecommendation governanceRecommendation;
    private List<String> rankingReasons = new ArrayList<>();
    private String riskTier; // CRITICAL, HIGH, MEDIUM, LOW

    public FleetRiskRankDto() {}

    public int getRank() { return rank; }
    public void setRank(int rank) { this.rank = rank; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

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

    public int getActiveIncidentsCount() { return activeIncidentsCount; }
    public void setActiveIncidentsCount(int activeIncidentsCount) { this.activeIncidentsCount = activeIncidentsCount; }

    public int getCriticalIncidentsCount() { return criticalIncidentsCount; }
    public void setCriticalIncidentsCount(int criticalIncidentsCount) { this.criticalIncidentsCount = criticalIncidentsCount; }

    public GovernanceRecommendation getGovernanceRecommendation() { return governanceRecommendation; }
    public void setGovernanceRecommendation(GovernanceRecommendation governanceRecommendation) { this.governanceRecommendation = governanceRecommendation; }

    public List<String> getRankingReasons() { return rankingReasons; }
    public void setRankingReasons(List<String> rankingReasons) { this.rankingReasons = rankingReasons; }

    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }
}
