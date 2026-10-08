package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_temporal_observations", indexes = {
        @Index(name = "idx_temp_obs_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_temp_obs_run", columnList = "run_id"),
        @Index(name = "idx_temp_obs_metric", columnList = "model_lineage_id, metric_name, target_key"),
        @Index(name = "idx_temp_obs_timestamp", columnList = "timestamp")
})
public class DiagnosticTemporalObservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "run_id", length = 64, nullable = false)
    private String runId;

    @Column(name = "run_type", length = 32, nullable = false)
    private String runType = "BASELINE"; // "BASELINE", "EXPERIMENT"

    @Column(name = "run_index")
    private Integer runIndex;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Enumerated(EnumType.STRING)
    @Column(name = "module", length = 64, nullable = false)
    private DiagnosticModule module;

    @Column(name = "metric_name", length = 128, nullable = false)
    private String metricName;

    @Column(name = "target_type", length = 64)
    private String targetType; // "GLOBAL", "FEATURE", "SUBGROUP", "MODEL"

    @Column(name = "target_key", length = 128)
    private String targetKey; // "GLOBAL", "FEATURE::income", etc.

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "severity", length = 32)
    private String severity = "NONE"; // "LOW", "MEDIUM", "HIGH", "CRITICAL", "NONE"

    @Column(name = "threshold")
    private Double threshold;

    @Column(name = "sample_size")
    private Long sampleSize;

    @Column(name = "source_result_id")
    private Long sourceResultId;

    @Column(name = "source_finding_id")
    private Long sourceFindingId;

    @Column(name = "source_investigation_id")
    private Long sourceInvestigationId;

    @Column(name = "source_remediation_id")
    private Long sourceRemediationId;

    @Column(name = "source_experiment_id", length = 64)
    private String sourceExperimentId;

    public DiagnosticTemporalObservation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public Integer getRunIndex() { return runIndex; }
    public void setRunIndex(Integer runIndex) { this.runIndex = runIndex; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public DiagnosticModule getModule() { return module; }
    public void setModule(DiagnosticModule module) { this.module = module; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public Long getSampleSize() { return sampleSize; }
    public void setSampleSize(Long sampleSize) { this.sampleSize = sampleSize; }

    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }

    public Long getSourceFindingId() { return sourceFindingId; }
    public void setSourceFindingId(Long sourceFindingId) { this.sourceFindingId = sourceFindingId; }

    public Long getSourceInvestigationId() { return sourceInvestigationId; }
    public void setSourceInvestigationId(Long sourceInvestigationId) { this.sourceInvestigationId = sourceInvestigationId; }

    public Long getSourceRemediationId() { return sourceRemediationId; }
    public void setSourceRemediationId(Long sourceRemediationId) { this.sourceRemediationId = sourceRemediationId; }

    public String getSourceExperimentId() { return sourceExperimentId; }
    public void setSourceExperimentId(String sourceExperimentId) { this.sourceExperimentId = sourceExperimentId; }
}
