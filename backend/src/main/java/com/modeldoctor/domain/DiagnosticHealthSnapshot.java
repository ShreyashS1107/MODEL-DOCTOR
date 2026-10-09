package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_health_snapshots", indexes = {
        @Index(name = "idx_snapshot_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_snapshot_run", columnList = "run_id"),
        @Index(name = "idx_snapshot_timestamp", columnList = "timestamp"),
        @Index(name = "idx_snapshot_state", columnList = "overall_state")
})
public class DiagnosticHealthSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_state", length = 32, nullable = false)
    private ModelHealthState overallState;

    @Column(name = "health_index")
    private Integer healthIndex;

    @Column(name = "dimension_states_json", columnDefinition = "TEXT", nullable = false)
    private String dimensionStatesJson;

    @Column(name = "active_alerts_count", nullable = false)
    private int activeAlertsCount;

    @Column(name = "critical_alerts_count", nullable = false)
    private int criticalAlertsCount;

    @Column(name = "high_alerts_count", nullable = false)
    private int highAlertsCount;

    @Column(name = "degraded_dimensions_count", nullable = false)
    private int degradedDimensionsCount;

    @Column(name = "unknown_dimensions_count", nullable = false)
    private int unknownDimensionsCount;

    @Column(name = "evidence_summary_json", columnDefinition = "TEXT")
    private String evidenceSummaryJson;

    @Column(name = "policy_version", nullable = false)
    private int policyVersion = 1;

    @Column(name = "decision_engine_version", length = 64, nullable = false)
    private String decisionEngineVersion = "v1.0";

    public DiagnosticHealthSnapshot() {}

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

    public String getDimensionStatesJson() { return dimensionStatesJson; }
    public void setDimensionStatesJson(String dimensionStatesJson) { this.dimensionStatesJson = dimensionStatesJson; }

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

    public String getEvidenceSummaryJson() { return evidenceSummaryJson; }
    public void setEvidenceSummaryJson(String evidenceSummaryJson) { this.evidenceSummaryJson = evidenceSummaryJson; }

    public int getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(int policyVersion) { this.policyVersion = policyVersion; }

    public String getDecisionEngineVersion() { return decisionEngineVersion; }
    public void setDecisionEngineVersion(String decisionEngineVersion) { this.decisionEngineVersion = decisionEngineVersion; }
}
