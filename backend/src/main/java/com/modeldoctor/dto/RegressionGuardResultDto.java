package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RegressionGuardResultDto {

    private String guard;
    private Double baselineValue;
    private Double candidateValue;
    private Double delta;
    private String operator;
    private Double threshold;
    private boolean passed;
    private String reason;

    public RegressionGuardResultDto() {}

    public RegressionGuardResultDto(String guard, Double baselineValue, Double candidateValue,
                                    Double delta, String operator, Double threshold, boolean passed, String reason) {
        this.guard = guard;
        this.baselineValue = baselineValue;
        this.candidateValue = candidateValue;
        this.delta = delta;
        this.operator = operator;
        this.threshold = threshold;
        this.passed = passed;
        this.reason = reason;
    }

    public String getGuard() { return guard; }
    public void setGuard(String guard) { this.guard = guard; }

    public Double getBaselineValue() { return baselineValue; }
    public void setBaselineValue(Double baselineValue) { this.baselineValue = baselineValue; }

    public Double getCandidateValue() { return candidateValue; }
    public void setCandidateValue(Double candidateValue) { this.candidateValue = candidateValue; }

    public Double getDelta() { return delta; }
    public void setDelta(Double delta) { this.delta = delta; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public boolean isPassed() { return passed; }
    public void setPassed(boolean passed) { this.passed = passed; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
