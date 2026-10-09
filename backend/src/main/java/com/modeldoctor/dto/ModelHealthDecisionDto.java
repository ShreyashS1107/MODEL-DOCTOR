package com.modeldoctor.dto;

import com.modeldoctor.domain.HealthDimension;
import com.modeldoctor.domain.ModelHealthState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModelHealthDecisionDto {

    private String modelLineageId;
    private String operationalRunId;
    private Instant evaluationTimestamp = Instant.now();
    private ModelHealthState overallState = ModelHealthState.UNKNOWN;
    private Integer healthIndex;
    private HealthIndexBreakdownDto healthIndexBreakdown;
    private Map<HealthDimension, HealthDimensionEvaluationDto> healthVector = new HashMap<>();
    private List<OperationalAlertDto> activeAlerts = new ArrayList<>();
    private List<OperationalAlertDto> allAlerts = new ArrayList<>();
    private List<DiagnosticAlertEventDto> recentEvents = new ArrayList<>();
    private List<DiagnosticHealthSnapshotDto> history = new ArrayList<>();
    private DiagnosticMonitoringPolicyDto policy;
    private EvidenceDossierDto evidenceDossier;
    private DataSufficiencyDto dataSufficiency;
    private String decisionReason;

    public ModelHealthDecisionDto() {}

    public static class DataSufficiencyDto {
        private int baselineRunsCount;
        private int requiredBaselineRuns;
        private boolean isSufficient;
        private int evaluatedDimensionsCount;
        private int requiredDimensionsCount;
        private String explanation;

        public DataSufficiencyDto() {}

        public DataSufficiencyDto(int baselineRunsCount, int requiredBaselineRuns, boolean isSufficient,
                                  int evaluatedDimensionsCount, int requiredDimensionsCount, String explanation) {
            this.baselineRunsCount = baselineRunsCount;
            this.requiredBaselineRuns = requiredBaselineRuns;
            this.isSufficient = isSufficient;
            this.evaluatedDimensionsCount = evaluatedDimensionsCount;
            this.requiredDimensionsCount = requiredDimensionsCount;
            this.explanation = explanation;
        }

        public int getBaselineRunsCount() { return baselineRunsCount; }
        public void setBaselineRunsCount(int baselineRunsCount) { this.baselineRunsCount = baselineRunsCount; }

        public int getRequiredBaselineRuns() { return requiredBaselineRuns; }
        public void setRequiredBaselineRuns(int requiredBaselineRuns) { this.requiredBaselineRuns = requiredBaselineRuns; }

        public boolean isSufficient() { return isSufficient; }
        public void setSufficient(boolean sufficient) { isSufficient = sufficient; }

        public int getEvaluatedDimensionsCount() { return evaluatedDimensionsCount; }
        public void setEvaluatedDimensionsCount(int evaluatedDimensionsCount) { this.evaluatedDimensionsCount = evaluatedDimensionsCount; }

        public int getRequiredDimensionsCount() { return requiredDimensionsCount; }
        public void setRequiredDimensionsCount(int requiredDimensionsCount) { this.requiredDimensionsCount = requiredDimensionsCount; }

        public String getExplanation() { return explanation; }
        public void setExplanation(String explanation) { this.explanation = explanation; }
    }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getOperationalRunId() { return operationalRunId; }
    public void setOperationalRunId(String operationalRunId) { this.operationalRunId = operationalRunId; }

    public Instant getEvaluationTimestamp() { return evaluationTimestamp; }
    public void setEvaluationTimestamp(Instant evaluationTimestamp) { this.evaluationTimestamp = evaluationTimestamp; }

    public ModelHealthState getOverallState() { return overallState; }
    public void setOverallState(ModelHealthState overallState) { this.overallState = overallState; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public HealthIndexBreakdownDto getHealthIndexBreakdown() { return healthIndexBreakdown; }
    public void setHealthIndexBreakdown(HealthIndexBreakdownDto healthIndexBreakdown) { this.healthIndexBreakdown = healthIndexBreakdown; }

    public Map<HealthDimension, HealthDimensionEvaluationDto> getHealthVector() { return healthVector; }
    public void setHealthVector(Map<HealthDimension, HealthDimensionEvaluationDto> healthVector) { this.healthVector = healthVector; }

    public List<OperationalAlertDto> getActiveAlerts() { return activeAlerts; }
    public void setActiveAlerts(List<OperationalAlertDto> activeAlerts) { this.activeAlerts = activeAlerts; }

    public List<OperationalAlertDto> getAllAlerts() { return allAlerts; }
    public void setAllAlerts(List<OperationalAlertDto> allAlerts) { this.allAlerts = allAlerts; }

    public List<DiagnosticAlertEventDto> getRecentEvents() { return recentEvents; }
    public void setRecentEvents(List<DiagnosticAlertEventDto> recentEvents) { this.recentEvents = recentEvents; }

    public List<DiagnosticHealthSnapshotDto> getHistory() { return history; }
    public void setHistory(List<DiagnosticHealthSnapshotDto> history) { this.history = history; }

    public DiagnosticMonitoringPolicyDto getPolicy() { return policy; }
    public void setPolicy(DiagnosticMonitoringPolicyDto policy) { this.policy = policy; }

    public EvidenceDossierDto getEvidenceDossier() { return evidenceDossier; }
    public void setEvidenceDossier(EvidenceDossierDto evidenceDossier) { this.evidenceDossier = evidenceDossier; }

    public DataSufficiencyDto getDataSufficiency() { return dataSufficiency; }
    public void setDataSufficiency(DataSufficiencyDto dataSufficiency) { this.dataSufficiency = dataSufficiency; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }
}
