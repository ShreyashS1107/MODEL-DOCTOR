package com.modeldoctor.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public class DiagnosticRemediationDto {

    private Long id;
    private String runId;
    private String targetType;
    private String targetKey;
    private String remediationType;
    private String title;
    private String description;
    private String priority;
    private Double priorityScore;
    private String confidence;
    private String evidenceStrength;
    private String hypothesis;
    private String expectedEffect;
    private String validationStrategy;
    private List<String> acceptanceCriteria;
    private List<String> requiredModules;
    private List<ExpectedImpactDto> expectedImpact;
    private List<String> regressionGuards;
    private List<String> sourceCorrelationIds;
    private Map<String, Long> sourceResultIds;
    private String sourceInvestigationTarget;
    private String status;
    private String rejectionReason;
    private String validationRunId;
    private boolean isAssociativeOnly = true;
    private String causalityDisclaimer = "REMEDIATION RECOMMENDATIONS ARE EVIDENCE-DRIVEN HYPOTHESES. THE SYSTEM DOES NOT ESTABLISH CAUSALITY OR GUARANTEE THAT A RECOMMENDED ACTION WILL IMPROVE THE MODEL.";
    private Instant createdAt;
    private Instant updatedAt;

    public DiagnosticRemediationDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getRemediationType() { return remediationType; }
    public void setRemediationType(String remediationType) { this.remediationType = remediationType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getEvidenceStrength() { return evidenceStrength; }
    public void setEvidenceStrength(String evidenceStrength) { this.evidenceStrength = evidenceStrength; }

    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }

    public String getExpectedEffect() { return expectedEffect; }
    public void setExpectedEffect(String expectedEffect) { this.expectedEffect = expectedEffect; }

    public String getValidationStrategy() { return validationStrategy; }
    public void setValidationStrategy(String validationStrategy) { this.validationStrategy = validationStrategy; }

    public List<String> getAcceptanceCriteria() { return acceptanceCriteria; }
    public void setAcceptanceCriteria(List<String> acceptanceCriteria) { this.acceptanceCriteria = acceptanceCriteria; }

    public List<String> getRequiredModules() { return requiredModules; }
    public void setRequiredModules(List<String> requiredModules) { this.requiredModules = requiredModules; }

    public List<ExpectedImpactDto> getExpectedImpact() { return expectedImpact; }
    public void setExpectedImpact(List<ExpectedImpactDto> expectedImpact) { this.expectedImpact = expectedImpact; }

    public List<String> getRegressionGuards() { return regressionGuards; }
    public void setRegressionGuards(List<String> regressionGuards) { this.regressionGuards = regressionGuards; }

    public List<String> getSourceCorrelationIds() { return sourceCorrelationIds; }
    public void setSourceCorrelationIds(List<String> sourceCorrelationIds) { this.sourceCorrelationIds = sourceCorrelationIds; }

    public Map<String, Long> getSourceResultIds() { return sourceResultIds; }
    public void setSourceResultIds(Map<String, Long> sourceResultIds) { this.sourceResultIds = sourceResultIds; }

    public String getSourceInvestigationTarget() { return sourceInvestigationTarget; }
    public void setSourceInvestigationTarget(String sourceInvestigationTarget) { this.sourceInvestigationTarget = sourceInvestigationTarget; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getValidationRunId() { return validationRunId; }
    public void setValidationRunId(String validationRunId) { this.validationRunId = validationRunId; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public String getCausalityDisclaimer() { return causalityDisclaimer; }
    public void setCausalityDisclaimer(String causalityDisclaimer) { this.causalityDisclaimer = causalityDisclaimer; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
