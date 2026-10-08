package com.modeldoctor.dto;

import java.util.List;
import java.util.Map;

public class InvestigationDossierDto {

    private InvestigationTargetDto target;
    private String hypothesis;
    private Double priorityScore;
    private String priorityLevel;
    private String confidence;
    private List<String> supportingModules;
    private List<DiagnosticCorrelationDto> supportingFindings;
    private List<InvestigationPathStepDto> investigationPath;
    private List<String> nextActions;
    private Map<String, Map<String, Object>> evidenceMetrics;
    private InvestigationProvenanceDto provenance;
    private List<String> connectedNodeIds;
    private boolean isAssociativeOnly = true;
    private String causalityDisclaimer = "INVESTIGATION RELATIONSHIPS ARE ASSOCIATIVE. THE SYSTEM DOES NOT ESTABLISH CAUSALITY.";

    public InvestigationDossierDto() {}

    public InvestigationTargetDto getTarget() { return target; }
    public void setTarget(InvestigationTargetDto target) { this.target = target; }

    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public String getPriorityLevel() { return priorityLevel; }
    public void setPriorityLevel(String priorityLevel) { this.priorityLevel = priorityLevel; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public List<String> getSupportingModules() { return supportingModules; }
    public void setSupportingModules(List<String> supportingModules) { this.supportingModules = supportingModules; }

    public List<DiagnosticCorrelationDto> getSupportingFindings() { return supportingFindings; }
    public void setSupportingFindings(List<DiagnosticCorrelationDto> supportingFindings) { this.supportingFindings = supportingFindings; }

    public List<InvestigationPathStepDto> getInvestigationPath() { return investigationPath; }
    public void setInvestigationPath(List<InvestigationPathStepDto> investigationPath) { this.investigationPath = investigationPath; }

    public List<String> getNextActions() { return nextActions; }
    public void setNextActions(List<String> nextActions) { this.nextActions = nextActions; }

    public Map<String, Map<String, Object>> getEvidenceMetrics() { return evidenceMetrics; }
    public void setEvidenceMetrics(Map<String, Map<String, Object>> evidenceMetrics) { this.evidenceMetrics = evidenceMetrics; }

    public InvestigationProvenanceDto getProvenance() { return provenance; }
    public void setProvenance(InvestigationProvenanceDto provenance) { this.provenance = provenance; }

    public List<String> getConnectedNodeIds() { return connectedNodeIds; }
    public void setConnectedNodeIds(List<String> connectedNodeIds) { this.connectedNodeIds = connectedNodeIds; }

    public String getTargetKey() { return target != null ? target.getTargetKey() : null; }
    public String getTargetType() { return target != null ? target.getTargetType() : null; }
    public String getDisplayName() { return target != null ? target.getDisplayName() : null; }
    public String getPriority() { return priorityLevel != null ? priorityLevel : (target != null ? target.getPriority() : null); }
    public String getEvidenceConfidence() { return confidence != null ? confidence : (target != null ? target.getConfidence() : null); }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public String getCausalityDisclaimer() { return causalityDisclaimer; }
    public void setCausalityDisclaimer(String causalityDisclaimer) { this.causalityDisclaimer = causalityDisclaimer; }
}
