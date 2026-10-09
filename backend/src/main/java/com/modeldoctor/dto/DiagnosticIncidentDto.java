package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.IncidentCategory;
import com.modeldoctor.domain.IncidentDecisionState;
import com.modeldoctor.domain.IncidentLifecycleState;
import com.modeldoctor.domain.ModelHealthState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DiagnosticIncidentDto {

    private Long id;
    private String incidentCode;
    private String modelLineageId;
    private String incidentFingerprint;
    private String title;
    private IncidentCategory category;
    private String currentSeverity;
    private int priorityScore;
    private IncidentLifecycleState lifecycleState;
    private String primaryTarget;
    private String primaryMetric;
    private IncidentDecisionState decisionRecommendation;
    private DecisionConfidence decisionConfidence;
    private String decisionRationale;
    private String evidenceSummary;
    private int independentModuleCount;
    private int relatedAlertsCount;
    private String investigationTargetKey;
    private Long remediationId;
    private String experimentId;
    private ModelHealthState currentHealthState;
    private boolean hasContradictoryEvidence;
    private String contradictoryEvidenceSummary;
    private IncidentPriorityBreakdownDto priorityBreakdown;
    private List<IncidentAlertCorrelationDto> relatedAlerts = new ArrayList<>();
    private List<DiagnosticIncidentEventDto> recentEvents = new ArrayList<>();
    private List<String> allowedActions = new ArrayList<>();
    private Instant firstObservedAt;
    private Instant lastObservedAt;
    private Instant resolvedAt;
    private Instant createdAt;
    private Instant updatedAt;

    public DiagnosticIncidentDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getIncidentCode() { return incidentCode; }
    public void setIncidentCode(String incidentCode) { this.incidentCode = incidentCode; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getIncidentFingerprint() { return incidentFingerprint; }
    public void setIncidentFingerprint(String incidentFingerprint) { this.incidentFingerprint = incidentFingerprint; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public IncidentCategory getCategory() { return category; }
    public void setCategory(IncidentCategory category) { this.category = category; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public int getPriorityScore() { return priorityScore; }
    public void setPriorityScore(int priorityScore) { this.priorityScore = priorityScore; }

    public IncidentLifecycleState getLifecycleState() { return lifecycleState; }
    public void setLifecycleState(IncidentLifecycleState lifecycleState) { this.lifecycleState = lifecycleState; }

    public String getPrimaryTarget() { return primaryTarget; }
    public void setPrimaryTarget(String primaryTarget) { this.primaryTarget = primaryTarget; }

    public String getPrimaryMetric() { return primaryMetric; }
    public void setPrimaryMetric(String primaryMetric) { this.primaryMetric = primaryMetric; }

    public IncidentDecisionState getDecisionRecommendation() { return decisionRecommendation; }
    public void setDecisionRecommendation(IncidentDecisionState decisionRecommendation) { this.decisionRecommendation = decisionRecommendation; }

    public DecisionConfidence getDecisionConfidence() { return decisionConfidence; }
    public void setDecisionConfidence(DecisionConfidence decisionConfidence) { this.decisionConfidence = decisionConfidence; }

    public String getDecisionRationale() { return decisionRationale; }
    public void setDecisionRationale(String decisionRationale) { this.decisionRationale = decisionRationale; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public int getIndependentModuleCount() { return independentModuleCount; }
    public void setIndependentModuleCount(int independentModuleCount) { this.independentModuleCount = independentModuleCount; }

    public int getRelatedAlertsCount() { return relatedAlertsCount; }
    public void setRelatedAlertsCount(int relatedAlertsCount) { this.relatedAlertsCount = relatedAlertsCount; }

    public String getInvestigationTargetKey() { return investigationTargetKey; }
    public void setInvestigationTargetKey(String investigationTargetKey) { this.investigationTargetKey = investigationTargetKey; }

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public ModelHealthState getCurrentHealthState() { return currentHealthState; }
    public void setCurrentHealthState(ModelHealthState currentHealthState) { this.currentHealthState = currentHealthState; }

    public boolean isHasContradictoryEvidence() { return hasContradictoryEvidence; }
    public void setHasContradictoryEvidence(boolean hasContradictoryEvidence) { this.hasContradictoryEvidence = hasContradictoryEvidence; }

    public String getContradictoryEvidenceSummary() { return contradictoryEvidenceSummary; }
    public void setContradictoryEvidenceSummary(String contradictoryEvidenceSummary) { this.contradictoryEvidenceSummary = contradictoryEvidenceSummary; }

    public IncidentPriorityBreakdownDto getPriorityBreakdown() { return priorityBreakdown; }
    public void setPriorityBreakdown(IncidentPriorityBreakdownDto priorityBreakdown) { this.priorityBreakdown = priorityBreakdown; }

    public List<IncidentAlertCorrelationDto> getRelatedAlerts() { return relatedAlerts; }
    public void setRelatedAlerts(List<IncidentAlertCorrelationDto> relatedAlerts) { this.relatedAlerts = relatedAlerts; }

    public List<DiagnosticIncidentEventDto> getRecentEvents() { return recentEvents; }
    public void setRecentEvents(List<DiagnosticIncidentEventDto> recentEvents) { this.recentEvents = recentEvents; }

    public List<String> getAllowedActions() { return allowedActions; }
    public void setAllowedActions(List<String> allowedActions) { this.allowedActions = allowedActions; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
