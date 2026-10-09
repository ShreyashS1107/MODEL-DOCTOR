package com.modeldoctor.dto;

public class ReliabilityRiskFactorDto {

    private String severity; // CRITICAL, HIGH, MEDIUM, LOW
    private String category; // INCIDENT, DRIFT, REMEDIATION, RECOVERY, TREND
    private String title;
    private String description;
    private String evidenceRef;

    public ReliabilityRiskFactorDto() {}

    public ReliabilityRiskFactorDto(String severity, String category, String title, String description, String evidenceRef) {
        this.severity = severity;
        this.category = category;
        this.title = title;
        this.description = description;
        this.evidenceRef = evidenceRef;
    }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getEvidenceRef() { return evidenceRef; }
    public void setEvidenceRef(String evidenceRef) { this.evidenceRef = evidenceRef; }
}
