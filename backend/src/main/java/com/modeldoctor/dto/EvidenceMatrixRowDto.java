package com.modeldoctor.dto;

public class EvidenceMatrixRowDto {

    private String evidenceType;
    private String module;
    private String metricName;
    private String valueFormatted;
    private String thresholdFormatted;
    private String severity;
    private String sourceRunId;
    private Long sourceResultId;
    private String confidence;
    private String relationshipType;
    private String details;

    public EvidenceMatrixRowDto() {}

    public EvidenceMatrixRowDto(String evidenceType, String module, String metricName, String valueFormatted,
                                String thresholdFormatted, String severity, String sourceRunId, Long sourceResultId,
                                String confidence, String relationshipType, String details) {
        this.evidenceType = evidenceType;
        this.module = module;
        this.metricName = metricName;
        this.valueFormatted = valueFormatted;
        this.thresholdFormatted = thresholdFormatted;
        this.severity = severity;
        this.sourceRunId = sourceRunId;
        this.sourceResultId = sourceResultId;
        this.confidence = confidence;
        this.relationshipType = relationshipType;
        this.details = details;
    }

    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }

    public String getModule() { return module; }
    public void setModule(String module) { this.module = module; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getValueFormatted() { return valueFormatted; }
    public void setValueFormatted(String valueFormatted) { this.valueFormatted = valueFormatted; }

    public String getThresholdFormatted() { return thresholdFormatted; }
    public void setThresholdFormatted(String thresholdFormatted) { this.thresholdFormatted = thresholdFormatted; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public String getSourceRunId() { return sourceRunId; }
    public void setSourceRunId(String sourceRunId) { this.sourceRunId = sourceRunId; }

    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }

    public String getConfidence() { return confidence; }
    public void setConfidence(String confidence) { this.confidence = confidence; }

    public String getRelationshipType() { return relationshipType; }
    public void setRelationshipType(String relationshipType) { this.relationshipType = relationshipType; }

    public String getDetails() { return details; }
    public void setDetails(String details) { this.details = details; }
}
