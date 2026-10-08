package com.modeldoctor.dto;

public class ExpectedImpactDto {

    private String metric;
    private String direction; // DECREASE, INCREASE, STABLE
    private String rationale;
    private String confidence; // HIGH, MEDIUM, LOW
    private String expectedMagnitude; // UNKNOWN or estimated interval

    public ExpectedImpactDto() {}

    public ExpectedImpactDto(String metric, String direction, String rationale, String confidence, String expectedMagnitude) {
        this.metric = metric;
        this.direction = direction;
        this.rationale = rationale;
        this.confidence = confidence;
        this.expectedMagnitude = expectedMagnitude != null ? expectedMagnitude : "UNKNOWN";
    }

    public String getMetric() { return metric; }
    public void setMetric(String metric) { this.metric = metric; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getExpectedMagnitude() { return expectedMagnitude; }
    public void setExpectedMagnitude(String expectedMagnitude) { this.expectedMagnitude = expectedMagnitude; }
}
