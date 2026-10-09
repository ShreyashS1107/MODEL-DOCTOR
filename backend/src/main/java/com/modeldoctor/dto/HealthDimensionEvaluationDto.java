package com.modeldoctor.dto;

import com.modeldoctor.domain.DimensionHealthState;
import com.modeldoctor.domain.HealthDimension;
import java.util.ArrayList;
import java.util.List;

public class HealthDimensionEvaluationDto {

    private HealthDimension dimension;
    private DimensionHealthState state = DimensionHealthState.UNKNOWN;
    private String summary;
    private List<MetricEvidenceItemDto> metricEvidence = new ArrayList<>();
    private int alertsCount = 0;
    private int criticalAlertsCount = 0;
    private boolean evaluable = false;
    private String reason;
    private Long sourceResultId;

    public HealthDimensionEvaluationDto() {}

    public HealthDimensionEvaluationDto(HealthDimension dimension) {
        this.dimension = dimension;
    }

    public static class MetricEvidenceItemDto {
        private String metricName;
        private String targetKey;
        private Double value;
        private Double threshold;
        private String severity;
        private String unit;
        private String details;
        private Long sourceResultId;

        public MetricEvidenceItemDto() {}

        public MetricEvidenceItemDto(String metricName, String targetKey, Double value, Double threshold,
                                     String severity, String unit, String details, Long sourceResultId) {
            this.metricName = metricName;
            this.targetKey = targetKey;
            this.value = value;
            this.threshold = threshold;
            this.severity = severity;
            this.unit = unit;
            this.details = details;
            this.sourceResultId = sourceResultId;
        }

        public String getMetricName() { return metricName; }
        public void setMetricName(String metricName) { this.metricName = metricName; }

        public String getTargetKey() { return targetKey; }
        public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

        public Double getValue() { return value; }
        public void setValue(Double value) { this.value = value; }

        public Double getThreshold() { return threshold; }
        public void setThreshold(Double threshold) { this.threshold = threshold; }

        public String getSeverity() { return severity; }
        public void setSeverity(String severity) { this.severity = severity; }

        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }

        public String getDetails() { return details; }
        public void setDetails(String details) { this.details = details; }

        public Long getSourceResultId() { return sourceResultId; }
        public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }
    }

    public HealthDimension getDimension() { return dimension; }
    public void setDimension(HealthDimension dimension) { this.dimension = dimension; }

    public DimensionHealthState getState() { return state; }
    public void setState(DimensionHealthState state) { this.state = state; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<MetricEvidenceItemDto> getMetricEvidence() { return metricEvidence; }
    public void setMetricEvidence(List<MetricEvidenceItemDto> metricEvidence) { this.metricEvidence = metricEvidence; }

    public int getAlertsCount() { return alertsCount; }
    public void setAlertsCount(int alertsCount) { this.alertsCount = alertsCount; }

    public int getCriticalAlertsCount() { return criticalAlertsCount; }
    public void setCriticalAlertsCount(int criticalAlertsCount) { this.criticalAlertsCount = criticalAlertsCount; }

    public boolean isEvaluable() { return evaluable; }
    public void setEvaluable(boolean evaluable) { this.evaluable = evaluable; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }
}
