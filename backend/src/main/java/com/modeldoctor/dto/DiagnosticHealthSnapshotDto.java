package com.modeldoctor.dto;

import com.modeldoctor.domain.HealthDimension;
import com.modeldoctor.domain.ModelHealthState;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class DiagnosticHealthSnapshotDto {

    private Long id;
    private String modelLineageId;
    private String runId;
    private Instant timestamp;
    private ModelHealthState overallState;
    private Integer healthIndex;
    private HealthIndexBreakdownDto healthIndexBreakdown;
    private Map<HealthDimension, HealthDimensionEvaluationDto> dimensionStates = new HashMap<>();
    private int activeAlertsCount;
    private int criticalAlertsCount;
    private int highAlertsCount;
    private int degradedDimensionsCount;
    private int unknownDimensionsCount;
    private String evidenceSummary;
    private int policyVersion;
    private String decisionEngineVersion;

    public DiagnosticHealthSnapshotDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public ModelHealthState getOverallState() { return overallState; }
    public void setOverallState(ModelHealthState overallState) { this.overallState = overallState; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public HealthIndexBreakdownDto getHealthIndexBreakdown() { return healthIndexBreakdown; }
    public void setHealthIndexBreakdown(HealthIndexBreakdownDto healthIndexBreakdown) { this.healthIndexBreakdown = healthIndexBreakdown; }

    public Map<HealthDimension, HealthDimensionEvaluationDto> getDimensionStates() { return dimensionStates; }
    public void setDimensionStates(Map<HealthDimension, HealthDimensionEvaluationDto> dimensionStates) { this.dimensionStates = dimensionStates; }

    public int getActiveAlertsCount() { return activeAlertsCount; }
    public void setActiveAlertsCount(int activeAlertsCount) { this.activeAlertsCount = activeAlertsCount; }

    public int getCriticalAlertsCount() { return criticalAlertsCount; }
    public void setCriticalAlertsCount(int criticalAlertsCount) { this.criticalAlertsCount = criticalAlertsCount; }

    public int getHighAlertsCount() { return highAlertsCount; }
    public void setHighAlertsCount(int highAlertsCount) { this.highAlertsCount = highAlertsCount; }

    public int getDegradedDimensionsCount() { return degradedDimensionsCount; }
    public void setDegradedDimensionsCount(int degradedDimensionsCount) { this.degradedDimensionsCount = degradedDimensionsCount; }

    public int getUnknownDimensionsCount() { return unknownDimensionsCount; }
    public void setUnknownDimensionsCount(int unknownDimensionsCount) { this.unknownDimensionsCount = unknownDimensionsCount; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }

    public String getDecisionEngineVersion() { return decisionEngineVersion; }
    public void setDecisionEngineVersion(String decisionEngineVersion) { this.decisionEngineVersion = decisionEngineVersion; }
}
