package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_remediations",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_remediation_identity", columnNames = {"run_id", "remediation_type", "target_key"})
        },
        indexes = {
                @Index(name = "idx_diag_rem_run_id", columnList = "run_id"),
                @Index(name = "idx_diag_rem_priority", columnList = "run_id, priority_score")
        })
public class DiagnosticRemediation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "target_type", nullable = false, length = 64)
    private String targetType; // FEATURE, SUBGROUP, BEHAVIOR, ERROR_TYPE, GLOBAL, DATASET

    @Column(name = "target_key", nullable = false, length = 128)
    private String targetKey; // e.g. FEATURE::drifted_feature, GLOBAL::CALIBRATION_FAILURE

    @Enumerated(EnumType.STRING)
    @Column(name = "remediation_type", nullable = false, length = 64)
    private RemediationType remediationType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 32)
    private InvestigationPriority priority;

    @Column(name = "priority_score", nullable = false)
    private Double priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", nullable = false, length = 32)
    private EvidenceConfidence confidence;

    @Column(name = "evidence_strength", length = 32)
    private String evidenceStrength; // VERY_HIGH, HIGH, MEDIUM, LOW

    @Column(name = "hypothesis", columnDefinition = "TEXT", nullable = false)
    private String hypothesis;

    @Column(name = "expected_effect", columnDefinition = "TEXT")
    private String expectedEffect;

    @Column(name = "validation_strategy", columnDefinition = "TEXT")
    private String validationStrategy;

    @Column(name = "acceptance_criteria_json", columnDefinition = "TEXT")
    private String acceptanceCriteriaJson;

    @Column(name = "required_modules_json", columnDefinition = "TEXT")
    private String requiredModulesJson;

    @Column(name = "expected_impact_json", columnDefinition = "TEXT")
    private String expectedImpactJson;

    @Column(name = "regression_guards_json", columnDefinition = "TEXT")
    private String regressionGuardsJson;

    @Column(name = "source_correlation_ids_json", columnDefinition = "TEXT")
    private String sourceCorrelationIdsJson;

    @Column(name = "source_result_ids_json", columnDefinition = "TEXT")
    private String sourceResultIdsJson;

    @Column(name = "source_investigation_target", length = 128)
    private String sourceInvestigationTarget;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RemediationStatus status = RemediationStatus.PROPOSED;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "validation_run_id", length = 64)
    private String validationRunId;

    @Column(name = "is_associative_only", nullable = false)
    private boolean isAssociativeOnly = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DiagnosticRemediation() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = RemediationStatus.PROPOSED;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public RemediationType getRemediationType() { return remediationType; }
    public void setRemediationType(RemediationType remediationType) { this.remediationType = remediationType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public InvestigationPriority getPriority() { return priority; }
    public void setPriority(InvestigationPriority priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public EvidenceConfidence getConfidence() { return confidence; }
    public void setConfidence(EvidenceConfidence confidence) { this.confidence = confidence; }

    public String getEvidenceStrength() { return evidenceStrength; }
    public void setEvidenceStrength(String evidenceStrength) { this.evidenceStrength = evidenceStrength; }

    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }

    public String getExpectedEffect() { return expectedEffect; }
    public void setExpectedEffect(String expectedEffect) { this.expectedEffect = expectedEffect; }

    public String getValidationStrategy() { return validationStrategy; }
    public void setValidationStrategy(String validationStrategy) { this.validationStrategy = validationStrategy; }

    public String getAcceptanceCriteriaJson() { return acceptanceCriteriaJson; }
    public void setAcceptanceCriteriaJson(String acceptanceCriteriaJson) { this.acceptanceCriteriaJson = acceptanceCriteriaJson; }

    public String getRequiredModulesJson() { return requiredModulesJson; }
    public void setRequiredModulesJson(String requiredModulesJson) { this.requiredModulesJson = requiredModulesJson; }

    public String getExpectedImpactJson() { return expectedImpactJson; }
    public void setExpectedImpactJson(String expectedImpactJson) { this.expectedImpactJson = expectedImpactJson; }

    public String getRegressionGuardsJson() { return regressionGuardsJson; }
    public void setRegressionGuardsJson(String regressionGuardsJson) { this.regressionGuardsJson = regressionGuardsJson; }

    public String getSourceCorrelationIdsJson() { return sourceCorrelationIdsJson; }
    public void setSourceCorrelationIdsJson(String sourceCorrelationIdsJson) { this.sourceCorrelationIdsJson = sourceCorrelationIdsJson; }

    public String getSourceResultIdsJson() { return sourceResultIdsJson; }
    public void setSourceResultIdsJson(String sourceResultIdsJson) { this.sourceResultIdsJson = sourceResultIdsJson; }

    public String getSourceInvestigationTarget() { return sourceInvestigationTarget; }
    public void setSourceInvestigationTarget(String sourceInvestigationTarget) { this.sourceInvestigationTarget = sourceInvestigationTarget; }

    public RemediationStatus getStatus() { return status; }
    public void setStatus(RemediationStatus status) { this.status = status; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getValidationRunId() { return validationRunId; }
    public void setValidationRunId(String validationRunId) { this.validationRunId = validationRunId; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
