package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_change_points", indexes = {
        @Index(name = "idx_change_pt_lineage", columnList = "model_lineage_id"),
        @Index(name = "idx_change_pt_metric", columnList = "model_lineage_id, metric_name, target_key"),
        @Index(name = "idx_change_pt_time", columnList = "change_timestamp")
})
public class DiagnosticChangePoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_lineage_id", length = 255, nullable = false)
    private String modelLineageId;

    @Column(name = "metric_name", length = 128, nullable = false)
    private String metricName;

    @Column(name = "target_key", length = 128, nullable = false)
    private String targetKey; // "GLOBAL", "FEATURE::income", etc.

    @Column(name = "change_run_id", length = 64)
    private String changeRunId;

    @Column(name = "change_timestamp")
    private Instant changeTimestamp;

    @Column(name = "before_mean", nullable = false)
    private Double beforeMean;

    @Column(name = "after_mean", nullable = false)
    private Double afterMean;

    @Column(name = "absolute_shift", nullable = false)
    private Double absoluteShift;

    @Column(name = "relative_shift", nullable = false)
    private Double relativeShift;

    @Column(name = "confidence_level", length = 32, nullable = false)
    private String confidenceLevel = "MEDIUM"; // "HIGH", "MEDIUM", "LOW"

    @Column(name = "run_ids_before_json", columnDefinition = "TEXT")
    private String runIdsBeforeJson;

    @Column(name = "run_ids_after_json", columnDefinition = "TEXT")
    private String runIdsAfterJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DiagnosticChangePoint() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getChangeRunId() { return changeRunId; }
    public void setChangeRunId(String changeRunId) { this.changeRunId = changeRunId; }

    public Instant getChangeTimestamp() { return changeTimestamp; }
    public void setChangeTimestamp(Instant changeTimestamp) { this.changeTimestamp = changeTimestamp; }

    public Double getBeforeMean() { return beforeMean; }
    public void setBeforeMean(Double beforeMean) { this.beforeMean = beforeMean; }

    public Double getAfterMean() { return afterMean; }
    public void setAfterMean(Double afterMean) { this.afterMean = afterMean; }

    public Double getAbsoluteShift() { return absoluteShift; }
    public void setAbsoluteShift(Double absoluteShift) { this.absoluteShift = absoluteShift; }

    public Double getRelativeShift() { return relativeShift; }
    public void setRelativeShift(Double relativeShift) { this.relativeShift = relativeShift; }

    public String getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }

    public String getRunIdsBeforeJson() { return runIdsBeforeJson; }
    public void setRunIdsBeforeJson(String runIdsBeforeJson) { this.runIdsBeforeJson = runIdsBeforeJson; }

    public String getRunIdsAfterJson() { return runIdsAfterJson; }
    public void setRunIdsAfterJson(String runIdsAfterJson) { this.runIdsAfterJson = runIdsAfterJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
