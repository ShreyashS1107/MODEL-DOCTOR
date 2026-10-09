package com.modeldoctor.dto;

public class HealthPenaltyDto {

    private String category;
    private String description;
    private int penaltyPoints;
    private String traceableEvidence;

    public HealthPenaltyDto() {}

    public HealthPenaltyDto(String category, String description, int penaltyPoints, String traceableEvidence) {
        this.category = category;
        this.description = description;
        this.penaltyPoints = penaltyPoints;
        this.traceableEvidence = traceableEvidence;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getPenaltyPoints() { return penaltyPoints; }
    public void setPenaltyPoints(int penaltyPoints) { this.penaltyPoints = penaltyPoints; }

    public String getTraceableEvidence() { return traceableEvidence; }
    public void setTraceableEvidence(String traceableEvidence) { this.traceableEvidence = traceableEvidence; }
}
