package com.modeldoctor.dto;

public class ReliabilityStrengthDto {

    private String category; // STABILITY, PERFORMANCE, REMEDIATION, RECOVERY, FAIRNESS
    private String title;
    private String description;
    private String evidenceRef;

    public ReliabilityStrengthDto() {}

    public ReliabilityStrengthDto(String category, String title, String description, String evidenceRef) {
        this.category = category;
        this.title = title;
        this.description = description;
        this.evidenceRef = evidenceRef;
    }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
}
