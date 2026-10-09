package com.modeldoctor.dto;

import com.modeldoctor.domain.ModelHealthState;
import com.modeldoctor.domain.ReliabilityTrend;
import java.util.ArrayList;
import java.util.List;

public class FleetRiskMatrixCellDto {

    private ModelHealthState currentHealth;
    private ReliabilityTrend trend;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private List<String> modelLineageIds = new ArrayList<>();

    public FleetRiskMatrixCellDto() {}

    public FleetRiskMatrixCellDto(ModelHealthState currentHealth, ReliabilityTrend trend, String riskLevel, List<String> modelLineageIds) {
        this.currentHealth = currentHealth;
        this.trend = trend;
        this.riskLevel = riskLevel;
        this.modelLineageIds = modelLineageIds != null ? modelLineageIds : new ArrayList<>();
    }

    public ModelHealthState getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(ModelHealthState currentHealth) { this.currentHealth = currentHealth; }

    public ReliabilityTrend getTrend() { return trend; }
    public void setTrend(ReliabilityTrend trend) { this.trend = trend; }

    public String getRiskLevel() { return riskLevel; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }

    public List<String> getModelLineageIds() { return modelLineageIds; }
    public void setModelLineageIds(List<String> modelLineageIds) { this.modelLineageIds = modelLineageIds; }
}
