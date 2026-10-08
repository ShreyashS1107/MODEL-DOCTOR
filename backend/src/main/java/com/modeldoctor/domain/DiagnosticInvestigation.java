package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_investigations", indexes = {
    @Index(name = "idx_inv_run_id", columnList = "run_id"),
    @Index(name = "idx_inv_run_priority", columnList = "run_id, priority_score")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_investigation_run_type_key", columnNames = {"run_id", "target_type", "target_key"})
})
public class DiagnosticInvestigation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "target_type", length = 32, nullable = false)
    private String targetType; // FEATURE, SUBGROUP, BEHAVIOR, ERROR_TYPE

    @Column(name = "target_key", length = 128, nullable = false)
    private String targetKey; // e.g. FEATURE::income, SUBGROUP::region=north, BEHAVIOR::HIGH_CONFIDENCE_ERRORS

    @Column(name = "display_name", length = 255, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 32, nullable = false)
    private InvestigationPriority priority;

    @Column(name = "priority_score", nullable = false)
    private Double priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 32, nullable = false)
    private EvidenceConfidence confidence;

    @Column(name = "supporting_module_count", nullable = false)
    private int supportingModuleCount;

    @Column(name = "supporting_finding_count", nullable = false)
    private int supportingFindingCount;

    @Column(name = "supporting_evidence_count", nullable = false)
    private int supportingEvidenceCount;

    @Column(name = "graph_degree", nullable = false)
    private int graphDegree;

    @Column(name = "hypothesis", columnDefinition = "TEXT", nullable = false)
    private String hypothesis;

    @Column(name = "next_actions_json", columnDefinition = "TEXT")
    private String nextActionsJson;

    @Column(name = "supporting_modules_json", length = 512, nullable = false)
    private String supportingModulesJson;

    @Column(name = "supporting_rule_ids_json", length = 1024)
    private String supportingRuleIdsJson;

    @Column(name = "evidence_summary_json", columnDefinition = "TEXT")
    private String evidenceSummaryJson;

    @Column(name = "investigation_path_json", columnDefinition = "TEXT")
    private String investigationPathJson;

    @Column(name = "provenance_json", columnDefinition = "TEXT")
    private String provenanceJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public DiagnosticInvestigation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public InvestigationPriority getPriority() { return priority; }
    public void setPriority(InvestigationPriority priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public EvidenceConfidence getConfidence() { return confidence; }
    public void setConfidence(EvidenceConfidence confidence) { this.confidence = confidence; }

    public int getSupportingModuleCount() { return supportingModuleCount; }
    public void setSupportingModuleCount(int supportingModuleCount) { this.supportingModuleCount = supportingModuleCount; }

    public int getSupportingFindingCount() { return supportingFindingCount; }
    public void setSupportingFindingCount(int supportingFindingCount) { this.supportingFindingCount = supportingFindingCount; }

    public int getSupportingEvidenceCount() { return supportingEvidenceCount; }
    public void setSupportingEvidenceCount(int supportingEvidenceCount) { this.supportingEvidenceCount = supportingEvidenceCount; }

    public int getGraphDegree() { return graphDegree; }
    public void setGraphDegree(int graphDegree) { this.graphDegree = graphDegree; }

    public String getHypothesis() { return hypothesis; }
    public void setHypothesis(String hypothesis) { this.hypothesis = hypothesis; }

    public String getNextActionsJson() { return nextActionsJson; }
    public void setNextActionsJson(String nextActionsJson) { this.nextActionsJson = nextActionsJson; }

    public String getSupportingModulesJson() { return supportingModulesJson; }
    public void setSupportingModulesJson(String supportingModulesJson) { this.supportingModulesJson = supportingModulesJson; }

    public String getSupportingRuleIdsJson() { return supportingRuleIdsJson; }
    public void setSupportingRuleIdsJson(String supportingRuleIdsJson) { this.supportingRuleIdsJson = supportingRuleIdsJson; }

    public String getEvidenceSummaryJson() { return evidenceSummaryJson; }
    public void setEvidenceSummaryJson(String evidenceSummaryJson) { this.evidenceSummaryJson = evidenceSummaryJson; }

    public String getInvestigationPathJson() { return investigationPathJson; }
    public void setInvestigationPathJson(String investigationPathJson) { this.investigationPathJson = investigationPathJson; }

    public String getProvenanceJson() { return provenanceJson; }
    public void setProvenanceJson(String provenanceJson) { this.provenanceJson = provenanceJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
