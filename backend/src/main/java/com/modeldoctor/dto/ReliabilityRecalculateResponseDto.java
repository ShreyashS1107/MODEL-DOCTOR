package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.GovernanceRecommendation;
import com.modeldoctor.domain.ModelReliabilityState;
import com.modeldoctor.domain.ReliabilityTrend;

public class ReliabilityRecalculateResponseDto {

    private String modelLineageId;
    private int operationalRunCount;
    private int reliabilityScore;
    private ModelReliabilityState reliabilityState;
    private DecisionConfidence confidence;
    private ReliabilityTrend trend;
    private GovernanceRecommendation governanceRecommendation;
    private boolean success;
    private String message;

    public ReliabilityRecalculateResponseDto() {}

    public ReliabilityRecalculateResponseDto(String modelLineageId, int operationalRunCount, int reliabilityScore,
                                            ModelReliabilityState reliabilityState, DecisionConfidence confidence,
                                            ReliabilityTrend trend, GovernanceRecommendation governanceRecommendation,
                                            boolean success, String message) {
        this.modelLineageId = modelLineageId;
        this.operationalRunCount = operationalRunCount;
        this.reliabilityScore = reliabilityScore;
        this.reliabilityState = reliabilityState;
        this.confidence = confidence;
        this.trend = trend;
        this.governanceRecommendation = governanceRecommendation;
        this.success = success;
        this.message = message;
    }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public int getOperationalRunCount() { return operationalRunCount; }
    public void setOperationalRunCount(int operationalRunCount) { this.operationalRunCount = operationalRunCount; }

    public int getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(int reliabilityScore) { this.reliabilityScore = reliabilityScore; }

    public ModelReliabilityState getReliabilityState() { return reliabilityState; }
    public void setReliabilityState(ModelReliabilityState reliabilityState) { this.reliabilityState = reliabilityState; }

    public DecisionConfidence getConfidence() { return confidence; }
    public void setConfidence(DecisionConfidence confidence) { this.confidence = confidence; }

    public ReliabilityTrend getTrend() { return trend; }
    public void setTrend(ReliabilityTrend trend) { this.trend = trend; }

    public GovernanceRecommendation getGovernanceRecommendation() { return governanceRecommendation; }
    public void setGovernanceRecommendation(GovernanceRecommendation governanceRecommendation) { this.governanceRecommendation = governanceRecommendation; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
