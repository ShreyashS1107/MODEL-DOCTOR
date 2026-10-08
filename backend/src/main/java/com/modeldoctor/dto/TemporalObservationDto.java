package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import java.time.Instant;

public class TemporalObservationDto {
    private Long id;
    private String modelLineageId;
    private String runId;
    private String runType;
    private Integer runIndex;
    private Instant timestamp;
    private DiagnosticModule module;
    private String metricName;
    private String targetType;
    private String targetKey;
    private Double metricValue;
    private String unit;
    private String severity;
    private Double threshold;
    private Long sampleSize;
    private Long sourceResultId;
    private Long sourceFindingId;
    private Long sourceInvestigationId;
    private Long sourceRemediationId;
    private String sourceExperimentId;

    public TemporalObservationDto() {}

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
