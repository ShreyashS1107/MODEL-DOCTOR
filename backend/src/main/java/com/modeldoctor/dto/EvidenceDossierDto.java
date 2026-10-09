package com.modeldoctor.dto;

import com.modeldoctor.domain.HealthDimension;
import com.modeldoctor.domain.ModelHealthState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvidenceDossierDto {

    private String modelLineageId;
    private ModelHealthState overallState;
    private Integer healthIndex;
    private String decisionReason;
    private String evaluatedRunId;
    private Instant evaluatedAt;
    private List<OperationalAlertDto> activeAlerts = new ArrayList<>();
    private Map<HealthDimension, HealthDimensionEvaluationDto> dimensionEvaluations = new HashMap<>();
    private List<InvestigationTargetDto> relatedInvestigationTargets = new ArrayList<>();
    private List<DiagnosticRemediationDto> relatedRemediations = new ArrayList<>();
    private List<DiagnosticExperimentDto> relatedExperiments = new ArrayList<>();
    private List<IssueTrackDto> temporalIssueTracks = new ArrayList<>();

    public EvidenceDossierDto() {}

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public ModelHealthState getOverallState() { return overallState; }
    public void setOverallState(ModelHealthState overallState) { this.overallState = overallState; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public String getDecisionReason() { return decisionReason; }
    public void setDecisionReason(String decisionReason) { this.decisionReason = decisionReason; }

    public String getEvaluatedRunId() { return evaluatedRunId; }
    public void setEvaluatedRunId(String evaluatedRunId) { this.evaluatedRunId = evaluatedRunId; }

    public Instant getEvaluatedAt() { return evaluatedAt; }
    public void setEvaluatedAt(Instant evaluatedAt) { this.evaluatedAt = evaluatedAt; }

    public List<OperationalAlertDto> getActiveAlerts() { return activeAlerts; }
    public void setActiveAlerts(List<OperationalAlertDto> activeAlerts) { this.activeAlerts = activeAlerts; }

    public Map<HealthDimension, HealthDimensionEvaluationDto> getDimensionEvaluations() { return dimensionEvaluations; }
    public void setDimensionEvaluations(Map<HealthDimension, HealthDimensionEvaluationDto> dimensionEvaluations) { this.dimensionEvaluations = dimensionEvaluations; }

    public List<InvestigationTargetDto> getRelatedInvestigationTargets() { return relatedInvestigationTargets; }
    public void setRelatedInvestigationTargets(List<InvestigationTargetDto> relatedInvestigationTargets) { this.relatedInvestigationTargets = relatedInvestigationTargets; }

    public List<DiagnosticRemediationDto> getRelatedRemediations() { return relatedRemediations; }
    public void setRelatedRemediations(List<DiagnosticRemediationDto> relatedRemediations) { this.relatedRemediations = relatedRemediations; }

    public List<DiagnosticExperimentDto> getRelatedExperiments() { return relatedExperiments; }
    public void setRelatedExperiments(List<DiagnosticExperimentDto> relatedExperiments) { this.relatedExperiments = relatedExperiments; }

    public List<IssueTrackDto> getTemporalIssueTracks() { return temporalIssueTracks; }
    public void setTemporalIssueTracks(List<IssueTrackDto> temporalIssueTracks) { this.temporalIssueTracks = temporalIssueTracks; }
}
