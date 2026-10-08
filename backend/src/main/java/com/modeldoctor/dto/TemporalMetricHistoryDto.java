package com.modeldoctor.dto;

import com.modeldoctor.domain.DiagnosticModule;
import java.util.ArrayList;
import java.util.List;

public class TemporalMetricHistoryDto {
    private String metricName;
    private DiagnosticModule module;
    private String targetKey; // "GLOBAL", "FEATURE::income", etc.
    private String targetType;
    private boolean higherIsBetter;
    private String unit;

    private List<TemporalMetricPointDto> baselinePoints = new ArrayList<>();
    private List<TemporalMetricPointDto> experimentPoints = new ArrayList<>();

    // Aggregated statistics
    private Double latestValue;
    private Double previousValue;
    private Double absoluteDelta;
    private Double relativeDelta;
    private Double mean;
    private Double median;
    private Double standardDeviation;
    private Double coefficientOfVariation;
    private Double minimum;
    private Double maximum;
    private Integer observationCount;

    // Trend analysis
    private String trendDirection; // "IMPROVING", "DEGRADING", "STABLE", "VOLATILE", "INSUFFICIENT_DATA"
    private Double slope;
    private Double rSquared;
    private Double mannKendallTau;
    private Double mannKendallPValue;
    private Boolean mannKendallSignificant;

    // Rolling baseline
    private Double baselineMean;
    private Double baselineStd;
    private Double standardizedDeviation; // (current - baselineMean) / baselineStd

    // Severity transitions
    private String currentSeverity;
    private String previousSeverity;
    private List<String> severityTransitions = new ArrayList<>();
    private Integer consecutiveRunsAtSeverity;

    // Change points on this metric
    private List<ChangePointDto> changePoints = new ArrayList<>();

    public TemporalMetricHistoryDto() {}

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public DiagnosticModule getModule() { return module; }
    public void setModule(DiagnosticModule module) { this.module = module; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getTargetType() { return targetType; }
    public void setTargetType(String targetType) { this.targetType = targetType; }

    public boolean isHigherIsBetter() { return higherIsBetter; }
    public void setHigherIsBetter(boolean higherIsBetter) { this.higherIsBetter = higherIsBetter; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public List<TemporalMetricPointDto> getBaselinePoints() { return baselinePoints; }
    public void setBaselinePoints(List<TemporalMetricPointDto> baselinePoints) { this.baselinePoints = baselinePoints; }

    public List<TemporalMetricPointDto> getExperimentPoints() { return experimentPoints; }
    public void setExperimentPoints(List<TemporalMetricPointDto> experimentPoints) { this.experimentPoints = experimentPoints; }

    public Double getLatestValue() { return latestValue; }
    public void setLatestValue(Double latestValue) { this.latestValue = latestValue; }

    public Double getPreviousValue() { return previousValue; }
    public void setPreviousValue(Double previousValue) { this.previousValue = previousValue; }

    public Double getAbsoluteDelta() { return absoluteDelta; }
    public void setAbsoluteDelta(Double absoluteDelta) { this.absoluteDelta = absoluteDelta; }

    public Double getRelativeDelta() { return relativeDelta; }
    public void setRelativeDelta(Double relativeDelta) { this.relativeDelta = relativeDelta; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public Double getMedian() { return median; }
    public void setMedian(Double median) { this.median = median; }

    public Double getStandardDeviation() { return standardDeviation; }
    public void setStandardDeviation(Double standardDeviation) { this.standardDeviation = standardDeviation; }

    public Double getCoefficientOfVariation() { return coefficientOfVariation; }
    public void setCoefficientOfVariation(Double coefficientOfVariation) { this.coefficientOfVariation = coefficientOfVariation; }

    public Double getMinimum() { return minimum; }
    public void setMinimum(Double minimum) { this.minimum = minimum; }

    public Double getMaximum() { return maximum; }
    public void setMaximum(Double maximum) { this.maximum = maximum; }

    public Integer getObservationCount() { return observationCount; }
    public void setObservationCount(Integer observationCount) { this.observationCount = observationCount; }

    public String getTrendDirection() { return trendDirection; }
    public void setTrendDirection(String trendDirection) { this.trendDirection = trendDirection; }

    public Double getSlope() { return slope; }
    public void setSlope(Double slope) { this.slope = slope; }

    public Double getrSquared() { return rSquared; }
    public void setrSquared(Double rSquared) { this.rSquared = rSquared; }

    public Double getMannKendallTau() { return mannKendallTau; }
    public void setMannKendallTau(Double mannKendallTau) { this.mannKendallTau = mannKendallTau; }

    public Double getMannKendallPValue() { return mannKendallPValue; }
    public void setMannKendallPValue(Double mannKendallPValue) { this.mannKendallPValue = mannKendallPValue; }

    public Boolean getMannKendallSignificant() { return mannKendallSignificant; }
    public void setMannKendallSignificant(Boolean mannKendallSignificant) { this.mannKendallSignificant = mannKendallSignificant; }

    public Double getBaselineMean() { return baselineMean; }
    public void setBaselineMean(Double baselineMean) { this.baselineMean = baselineMean; }

    public Double getBaselineStd() { return baselineStd; }
    public void setBaselineStd(Double baselineStd) { this.baselineStd = baselineStd; }

    public Double getStandardizedDeviation() { return standardizedDeviation; }
    public void setStandardizedDeviation(Double standardizedDeviation) { this.standardizedDeviation = standardizedDeviation; }

    public String getCurrentSeverity() { return currentSeverity; }
    public void setCurrentSeverity(String currentSeverity) { this.currentSeverity = currentSeverity; }

    public String getPreviousSeverity() { return previousSeverity; }
    public void setPreviousSeverity(String previousSeverity) { this.previousSeverity = previousSeverity; }

    public List<String> getSeverityTransitions() { return severityTransitions; }
    public void setSeverityTransitions(List<String> severityTransitions) { this.severityTransitions = severityTransitions; }

    public Integer getConsecutiveRunsAtSeverity() { return consecutiveRunsAtSeverity; }
    public void setConsecutiveRunsAtSeverity(Integer consecutiveRunsAtSeverity) { this.consecutiveRunsAtSeverity = consecutiveRunsAtSeverity; }

    public List<ChangePointDto> getChangePoints() { return changePoints; }
    public void setChangePoints(List<ChangePointDto> changePoints) { this.changePoints = changePoints; }
}
