package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_correlations", indexes = {
    @Index(name = "idx_corr_run_id", columnList = "run_id"),
    @Index(name = "idx_corr_run_priority", columnList = "run_id, priority_score")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_correlation_run_rule_feature", columnNames = {"run_id", "rule_id", "correlation_key"})
})
public class DiagnosticCorrelation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "rule_id", length = 64, nullable = false)
    private String ruleId;

    @Column(name = "correlation_key", length = 128, nullable = false)
    private String correlationKey;

    @Column(name = "finding_type", length = 64, nullable = false)
    private String findingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 32, nullable = false)
    private SeverityLevel severity;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", length = 32, nullable = false)
    private InvestigationPriority priority;

    @Column(name = "priority_score", nullable = false)
    private Double priorityScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 32, nullable = false)
    private EvidenceConfidence confidence;

    @Column(name = "feature", length = 128)
    private String feature;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "summary", length = 1024, nullable = false)
    private String summary;

    @Column(name = "why_it_matters", columnDefinition = "TEXT")
    private String whyItMatters;

    @Column(name = "investigation_direction", columnDefinition = "TEXT")
    private String investigationDirection;

    @Column(name = "evidence_json", columnDefinition = "TEXT", nullable = false)
    private String evidenceJson;

    @Column(name = "source_modules_json", length = 255, nullable = false)
    private String sourceModulesJson;

    @Column(name = "source_result_ids_json", length = 255)
    private String sourceResultIdsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DiagnosticCorrelation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getCorrelationKey() { return correlationKey; }
    public void setCorrelationKey(String correlationKey) { this.correlationKey = correlationKey; }

    public String getFindingType() { return findingType; }
    public void setFindingType(String findingType) { this.findingType = findingType; }

    public SeverityLevel getSeverity() { return severity; }
    public void setSeverity(SeverityLevel severity) { this.severity = severity; }

    public InvestigationPriority getPriority() { return priority; }
    public void setPriority(InvestigationPriority priority) { this.priority = priority; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public EvidenceConfidence getConfidence() { return confidence; }
    public void setConfidence(EvidenceConfidence confidence) { this.confidence = confidence; }

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getWhyItMatters() { return whyItMatters; }
    public void setWhyItMatters(String whyItMatters) { this.whyItMatters = whyItMatters; }

    public String getInvestigationDirection() { return investigationDirection; }
    public void setInvestigationDirection(String investigationDirection) { this.investigationDirection = investigationDirection; }

    public String getEvidenceJson() { return evidenceJson; }
    public void setEvidenceJson(String evidenceJson) { this.evidenceJson = evidenceJson; }

    public String getSourceModulesJson() { return sourceModulesJson; }
    public void setSourceModulesJson(String sourceModulesJson) { this.sourceModulesJson = sourceModulesJson; }

    public String getSourceResultIdsJson() { return sourceResultIdsJson; }
    public void setSourceResultIdsJson(String sourceResultIdsJson) { this.sourceResultIdsJson = sourceResultIdsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
