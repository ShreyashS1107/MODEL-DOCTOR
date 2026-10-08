package com.modeldoctor.dto;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class ChangePointDto {
    private Long id;
    private String modelLineageId;
    private String metricName;
    private String targetKey;
    private String changeRunId;
    private Instant changeTimestamp;
    private Double beforeMean;
    private Double afterMean;
    private Double absoluteShift;
    private Double relativeShift;
    private String confidenceLevel; // "HIGH", "MEDIUM", "LOW"
    private List<String> runIdsBefore = new ArrayList<>();
    private List<String> runIdsAfter = new ArrayList<>();

    public ChangePointDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getModelLineageId() { return modelLineageId; }
    public void setModelLineageId(String modelLineageId) { this.modelLineageId = modelLineageId; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getChangeRunId() { return changeRunId; }
    public void setChangeRunId(String changeRunId) { this.changeRunId = changeRunId; }

    public Instant getChangeTimestamp() { return changeTimestamp; }
    public void setChangeTimestamp(Instant changeTimestamp) { this.changeTimestamp = changeTimestamp; }

    public Double getBeforeMean() { return beforeMean; }
    public void setBeforeMean(Double beforeMean) { this.beforeMean = beforeMean; }

    public Double getAfterMean() { return afterMean; }
    public void setAfterMean(Double afterMean) { this.afterMean = afterMean; }

    public Double getAbsoluteShift() { return absoluteShift; }
    public void setAbsoluteShift(Double absoluteShift) { this.absoluteShift = absoluteShift; }

    public Double getRelativeShift() { return relativeShift; }
    public void setRelativeShift(Double relativeShift) { this.relativeShift = relativeShift; }

    public String getConfidenceLevel() { return confidenceLevel; }
    public void setConfidenceLevel(String confidenceLevel) { this.confidenceLevel = confidenceLevel; }

    public List<String> getRunIdsBefore() { return runIdsBefore; }
    public void setRunIdsBefore(List<String> runIdsBefore) { this.runIdsBefore = runIdsBefore; }

    public List<String> getRunIdsAfter() { return runIdsAfter; }
    public void setRunIdsAfter(List<String> runIdsAfter) { this.runIdsAfter = runIdsAfter; }
}
