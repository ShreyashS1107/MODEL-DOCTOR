package com.modeldoctor.dto;

public class MetricComparisonDto {

    private String metricName;
    private String module;
    private Double baselineValue;
    private Double candidateValue;
    private Double delta;
    private Double percentChange;
    private boolean isHigherBetter;
    private String direction; // IMPROVED, REGRESSED, NO_MATERIAL_CHANGE, INSUFFICIENT_EVIDENCE
    private String assessment; // IMPROVED, REGRESSED, UNCHANGED, MISSING
    private String rationale;

    public MetricComparisonDto() {}

    public MetricComparisonDto(
            String metricName,
            Double baselineValue,
            Double candidateValue,
            Double delta,
            Double percentChange,
            String direction,
            String assessment) {
        this.metricName = metricName;
        this.baselineValue = baselineValue;
        this.candidateValue = candidateValue;
        this.delta = delta;
        this.percentChange = percentChange;
        this.direction = direction;
        this.assessment = assessment;
    }

    public MetricComparisonDto(
            String metricName,
            String module,
            Double baselineValue,
            Double candidateValue,
            Double delta,
            Double percentChange,
            boolean isHigherBetter,
            String direction,
            String assessment,
            String rationale) {
        this.metricName = metricName;
        this.module = module;
        this.baselineValue = baselineValue;
        this.candidateValue = candidateValue;
        this.delta = delta;
        this.percentChange = percentChange;
        this.isHigherBetter = isHigherBetter;
        this.direction = direction;
        this.assessment = assessment;
        this.rationale = rationale;
    }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public Double getBaselineValue() { return baselineValue; }
    public void setBaselineValue(Double baselineValue) { this.baselineValue = baselineValue; }

    public Double getCandidateValue() { return candidateValue; }
    public void setCandidateValue(Double candidateValue) { this.candidateValue = candidateValue; }

    public Double getDelta() { return delta; }
    public void setDelta(Double delta) { this.delta = delta; }

    public Double getPercentChange() { return percentChange; }
    public void setPercentChange(Double percentChange) { this.percentChange = percentChange; }

    public boolean isHigherBetter() { return isHigherBetter; }
    public void setHigherBetter(boolean higherBetter) { isHigherBetter = higherBetter; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }
}
