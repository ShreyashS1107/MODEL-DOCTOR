package com.modeldoctor.dto;

public class EvidenceGraphEdgeDto {

    private String id;
    private String source;
    private String target;
    private String relationship; // OBSERVES, PRODUCES, IMPLICATES, INVOLVES, SUPPORTED_BY, DRIFTED_IN, INFLUENCES, ASSOCIATED_WITH, SENSITIVE_UNDER, CO_OCCURS_WITH, SENSITIVE_TO
    private String sourceModule;
    private Long sourceResultId;
    private String ruleId;
    private String metricName;
    private Double metricValue;
    private Double threshold;
    private String thresholdComparison;
    private String evidenceStrength; // HIGH, MEDIUM, LOW
    private Double weight;
    private String description;
    private boolean isAssociativeOnly = true;

    public EvidenceGraphEdgeDto() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTarget() { return target; }
    public void setTarget(String target) { this.target = target; }

    public String getRelationship() { return relationship; }
    public void setRelationship(String relationship) { this.relationship = relationship; }

    public String getRelationType() { return relationship; }
    public void setRelationType(String relationType) { this.relationship = relationType; }


    public String getSourceModule() { return sourceModule; }
    public void setSourceModule(String sourceModule) { this.sourceModule = sourceModule; }

    public Long getSourceResultId() { return sourceResultId; }
    public void setSourceResultId(Long sourceResultId) { this.sourceResultId = sourceResultId; }

    public String getRuleId() { return ruleId; }
    public void setRuleId(String ruleId) { this.ruleId = ruleId; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public Double getMetricValue() { return metricValue; }
    public void setMetricValue(Double metricValue) { this.metricValue = metricValue; }

    public Double getThreshold() { return threshold; }
    public void setThreshold(Double threshold) { this.threshold = threshold; }

    public String getThresholdComparison() { return thresholdComparison; }
    public void setThresholdComparison(String thresholdComparison) { this.thresholdComparison = thresholdComparison; }

    public String getEvidenceStrength() { return evidenceStrength; }
    public void setEvidenceStrength(String evidenceStrength) { this.evidenceStrength = evidenceStrength; }

    public Double getWeight() { return weight; }
    public void setWeight(Double weight) { this.weight = weight; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isAssociativeOnly() { return isAssociativeOnly; }
    public void setAssociativeOnly(boolean associativeOnly) { isAssociativeOnly = associativeOnly; }
}
