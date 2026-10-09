package com.modeldoctor.dto;

import com.modeldoctor.domain.IncidentCategory;
import com.modeldoctor.domain.IncidentLifecycleState;
import com.modeldoctor.domain.ModelHealthState;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;


public class IncidentEvidenceDossierDto {

    private Long incidentId;
    private String incidentCode;
    private String modelLineageId;
    private String title;
    private IncidentCategory category;
    private String currentSeverity;
    private int priorityScore;
    private IncidentLifecycleState lifecycleState;
    private String primaryTarget;
    private String primaryMetric;
    private IncidentDecisionDto decision;
    private IncidentPriorityBreakdownDto priorityBreakdown;
    private ContradictoryEvidenceDto contradictoryEvidence;
    private String evidenceSummary;
    private int independentModuleCount;
    private int relatedAlertsCount;
    private ModelHealthState currentHealthState;
    private List<EvidenceMatrixRowDto> evidenceMatrix = new ArrayList<>();
    private List<IncidentAlertCorrelationDto> relatedAlerts = new ArrayList<>();
    private InvestigationTargetDto investigationTarget;
    private DiagnosticRemediationDto remediation;
    private DiagnosticExperimentDto experiment;
    private List<IssueTrackDto> temporalIssueTracks = new ArrayList<>();
    private List<DiagnosticIncidentEventDto> auditEvents = new ArrayList<>();
    private Instant firstObservedAt;
    private Instant lastObservedAt;
    private Instant resolvedAt;
    private String nonCausalityDisclaimer = "INCIDENT SYNTHESIS AND DECISION RECOMMENDATIONS ARE EVIDENCE-DRIVEN AND ASSOCIATIVE. THEY DO NOT ESTABLISH CAUSALITY OR AUTHORIZE AUTONOMOUS MODEL MODIFICATION.";

    public IncidentEvidenceDossierDto() {}

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public String getIncidentCode() { return incidentCode; }
    public void setIncidentCode(String incidentCode) { this.incidentCode = incidentCode; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

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

    public IncidentDecisionDto getDecision() { return decision; }
    public void setDecision(IncidentDecisionDto decision) { this.decision = decision; }

    public IncidentPriorityBreakdownDto getPriorityBreakdown() { return priorityBreakdown; }
    public void setPriorityBreakdown(IncidentPriorityBreakdownDto priorityBreakdown) { this.priorityBreakdown = priorityBreakdown; }

    public ContradictoryEvidenceDto getContradictoryEvidence() { return contradictoryEvidence; }
    public void setContradictoryEvidence(ContradictoryEvidenceDto contradictoryEvidence) { this.contradictoryEvidence = contradictoryEvidence; }

    public String getEvidenceSummary() { return evidenceSummary; }
    public void setEvidenceSummary(String evidenceSummary) { this.evidenceSummary = evidenceSummary; }

    public int getIndependentModuleCount() { return independentModuleCount; }
    public void setIndependentModuleCount(int independentModuleCount) { this.independentModuleCount = independentModuleCount; }

    public int getRelatedAlertsCount() { return relatedAlertsCount; }
    public void setRelatedAlertsCount(int relatedAlertsCount) { this.relatedAlertsCount = relatedAlertsCount; }

    public ModelHealthState getCurrentHealthState() { return currentHealthState; }
    public void setCurrentHealthState(ModelHealthState currentHealthState) { this.currentHealthState = currentHealthState; }

    public List<EvidenceMatrixRowDto> getEvidenceMatrix() { return evidenceMatrix; }
    public void setEvidenceMatrix(List<EvidenceMatrixRowDto> evidenceMatrix) { this.evidenceMatrix = evidenceMatrix; }

    public List<IncidentAlertCorrelationDto> getRelatedAlerts() { return relatedAlerts; }
    public void setRelatedAlerts(List<IncidentAlertCorrelationDto> relatedAlerts) { this.relatedAlerts = relatedAlerts; }

    public InvestigationTargetDto getInvestigationTarget() { return investigationTarget; }
    public void setInvestigationTarget(InvestigationTargetDto investigationTarget) { this.investigationTarget = investigationTarget; }

    public DiagnosticRemediationDto getRemediation() { return remediation; }
    public void setRemediation(DiagnosticRemediationDto remediation) { this.remediation = remediation; }

    public DiagnosticExperimentDto getExperiment() { return experiment; }
    public void setExperiment(DiagnosticExperimentDto experiment) { this.experiment = experiment; }

    public List<IssueTrackDto> getTemporalIssueTracks() { return temporalIssueTracks; }
    public void setTemporalIssueTracks(List<IssueTrackDto> temporalIssueTracks) { this.temporalIssueTracks = temporalIssueTracks; }

    public List<DiagnosticIncidentEventDto> getAuditEvents() { return auditEvents; }
    public void setAuditEvents(List<DiagnosticIncidentEventDto> auditEvents) { this.auditEvents = auditEvents; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public String getNonCausalityDisclaimer() { return nonCausalityDisclaimer; }
    public void setNonCausalityDisclaimer(String nonCausalityDisclaimer) { this.nonCausalityDisclaimer = nonCausalityDisclaimer; }
}
