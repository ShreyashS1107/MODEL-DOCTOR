package com.modeldoctor.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FleetOverviewDto {

    private int totalLineagesCount;
    private int healthyLineagesCount;
    private int stableLineagesCount;
    private int degradedLineagesCount;
    private int atRiskLineagesCount;
    private int criticalLineagesCount;
    private int recoveringLineagesCount;
    private int averageReliabilityScore;

    private List<FleetRiskRankDto> rankedLineages = new ArrayList<>();
    private List<FleetRiskMatrixCellDto> riskMatrix = new ArrayList<>();
    private List<FleetPatternDto> recurringPatterns = new ArrayList<>();
    private Instant evaluatedAt = Instant.now();

    public FleetOverviewDto() {}

    public int getTotalLineagesCount() { return totalLineagesCount; }
    public void setTotalLineagesCount(int totalLineagesCount) { this.totalLineagesCount = totalLineagesCount; }

    public int getHealthyLineagesCount() { return healthyLineagesCount; }
    public void setHealthyLineagesCount(int healthyLineagesCount) { this.healthyLineagesCount = healthyLineagesCount; }

    public int getStableLineagesCount() { return stableLineagesCount; }
    public void setStableLineagesCount(int stableLineagesCount) { this.stableLineagesCount = stableLineagesCount; }

    public int getDegradedLineagesCount() { return degradedLineagesCount; }
    public void setDegradedLineagesCount(int degradedLineagesCount) { this.degradedLineagesCount = degradedLineagesCount; }

    public int getAtRiskLineagesCount() { return atRiskLineagesCount; }
    public void setAtRiskLineagesCount(int atRiskLineagesCount) { this.atRiskLineagesCount = atRiskLineagesCount; }

    public int getCriticalLineagesCount() { return criticalLineagesCount; }
    public void setCriticalLineagesCount(int criticalLineagesCount) { this.criticalLineagesCount = criticalLineagesCount; }

    public int getRecoveringLineagesCount() { return recoveringLineagesCount; }
    public void setRecoveringLineagesCount(int recoveringLineagesCount) { this.recoveringLineagesCount = recoveringLineagesCount; }

    public int getAverageReliabilityScore() { return averageReliabilityScore; }
    public void setAverageReliabilityScore(int averageReliabilityScore) { this.averageReliabilityScore = averageReliabilityScore; }

    public List<FleetRiskRankDto> getRankedLineages() { return rankedLineages; }
    public void setRankedLineages(List<FleetRiskRankDto> rankedLineages) { this.rankedLineages = rankedLineages; }

    public List<FleetRiskMatrixCellDto> getRiskMatrix() { return riskMatrix; }
    public void setRiskMatrix(List<FleetRiskMatrixCellDto> riskMatrix) { this.riskMatrix = riskMatrix; }

    public List<FleetPatternDto> getRecurringPatterns() { return recurringPatterns; }
    public void setRecurringPatterns(List<FleetPatternDto> recurringPatterns) { this.recurringPatterns = recurringPatterns; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }
}
