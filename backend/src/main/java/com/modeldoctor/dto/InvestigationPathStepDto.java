package com.modeldoctor.dto;

public class InvestigationPathStepDto {

    private int stepNumber;
    private String stepType; // TARGET_IDENTIFICATION, MODULE_OBSERVATION, CORRELATION_INTERACTION, ACTION_RECOMMENDATION
    private String module;
    private String title;
    private String detail;
    private String metric;
    private Double metricValue;
    private String threshold;
    private Long sourceResultId;

    public InvestigationPathStepDto() {}

    public InvestigationPathStepDto(int stepNumber, String stepType, String module, String title, String detail, String metric, Double metricValue, String threshold, Long sourceResultId) {
        this.stepNumber = stepNumber;
        this.stepType = stepType;
        this.module = module;
        this.title = title;
        this.detail = detail;
        this.metric = metric;
        this.metricValue = metricValue;
        this.threshold = threshold;
        this.sourceResultId = sourceResultId;
    }

    public int getStepNumber() { return stepNumber; }
    public void setStepNumber(int stepNumber) { this.stepNumber = stepNumber; }

    public String getStepType() { return stepType; }
    public void setStepType(String stepType) { this.stepType = stepType; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getSourceModule() { return module; }
    public void setSourceModule(String sourceModule) { this.module = sourceModule; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getDescription() { return detail; }
    public void setDescription(String description) { this.detail = description; }


    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public String getThreshold() { return threshold; }
    public void setThreshold(String threshold) { this.threshold = threshold; }

    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }
}
