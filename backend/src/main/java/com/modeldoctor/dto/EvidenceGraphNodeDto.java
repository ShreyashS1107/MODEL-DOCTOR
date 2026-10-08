package com.modeldoctor.dto;

import java.util.Map;

public class EvidenceGraphNodeDto {

    private String id;
    private String label;
    private String nodeType; // MODULE, FEATURE, SUBGROUP, ERROR_TYPE, FINDING, METRIC, BEHAVIOR
    private String category; // MODULE, TARGET, FINDING, METRIC
    private String severity; // CRITICAL, HIGH, MEDIUM, LOW, INFO, NEUTRAL
    private Double priorityScore;
    private int degree;
    private Map<String, Object> metadata;

    public EvidenceGraphNodeDto() {}

    public EvidenceGraphNodeDto(String id, String label, String nodeType, String category, String severity, Double priorityScore, int degree, Map<String, Object> metadata) {
        this.id = id;
        this.label = label;
        this.nodeType = nodeType;
        this.category = category;
        this.severity = severity;
        this.priorityScore = priorityScore;
        this.degree = degree;
        this.metadata = metadata;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getNodeType() { return nodeType; }
    public void setNodeType(String nodeType) { this.nodeType = nodeType; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getSeverity() { return severity; }
    public void setSeverity(String severity) { this.severity = severity; }

    public Double getPriorityScore() { return priorityScore; }
    public void setPriorityScore(Double priorityScore) { this.priorityScore = priorityScore; }

    public int getDegree() { return degree; }
    public void setDegree(int degree) { this.degree = degree; }

    public Map<String, Object> getMetadata() { return metadata; }
    public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
}
