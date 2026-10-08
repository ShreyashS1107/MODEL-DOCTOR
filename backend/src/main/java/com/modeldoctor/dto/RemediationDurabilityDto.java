package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class RemediationDurabilityDto {
    private Long remediationId;
    private String experimentId;
    private String targetKey;
    private String remediationType;
    private String experimentType;
    private String baselineRunId;
    private String candidateRunId;
    private String experimentConclusion;

    private String metricName;
    private Double baselineValue;
    private Double experimentCandidateValue;
    private List<Double> followUpValues = new ArrayList<>();
    private List<String> followUpRunIds = new ArrayList<>();

    private String durabilityStatus; // "SUSTAINED", "TEMPORARY", "FAILED_TO_SUSTAIN", "INSUFFICIENT_FOLLOWUP", "NOT_APPLICABLE"
    private String assessment;

    public RemediationDurabilityDto() {}

    public Long getRemediationId() { return remediationId; }
    public void setRemediationId(Long remediationId) { this.remediationId = remediationId; }

    public String getExperimentId() { return experimentId; }
    public void setExperimentId(String experimentId) { this.experimentId = experimentId; }

    public String getTargetKey() { return targetKey; }
    public void setTargetKey(String targetKey) { this.targetKey = targetKey; }

    public String getRemediationType() { return remediationType; }
    public void setRemediationType(String remediationType) { this.remediationType = remediationType; }

    public String getExperimentType() { return experimentType; }
    public void setExperimentType(String experimentType) { this.experimentType = experimentType; }

    public String getBaselineRunId() { return baselineRunId; }
    public void setBaselineRunId(String baselineRunId) { this.baselineRunId = baselineRunId; }

    public String getCandidateRunId() { return candidateRunId; }
    public void setCandidateRunId(String candidateRunId) { this.candidateRunId = candidateRunId; }

    public String getExperimentConclusion() { return experimentConclusion; }
    public void setExperimentConclusion(String experimentConclusion) { this.experimentConclusion = experimentConclusion; }

    public String getMetricName() { return metricName; }
    public void setMetricName(String metricName) { this.metricName = metricName; }

    public Double getBaselineValue() { return baselineValue; }
    public void setBaselineValue(Double baselineValue) { this.baselineValue = baselineValue; }

    public Double getExperimentCandidateValue() { return experimentCandidateValue; }
    public void setExperimentCandidateValue(Double experimentCandidateValue) { this.experimentCandidateValue = experimentCandidateValue; }

    public List<Double> getFollowUpValues() { return followUpValues; }
    public void setFollowUpValues(List<Double> followUpValues) { this.followUpValues = followUpValues; }

    public List<String> getFollowUpRunIds() { return followUpRunIds; }
    public void setFollowUpRunIds(List<String> followUpRunIds) { this.followUpRunIds = followUpRunIds; }

    public String getDurabilityStatus() { return durabilityStatus; }
    public void setDurabilityStatus(String durabilityStatus) { this.durabilityStatus = durabilityStatus; }

    public String getAssessment() { return assessment; }
    public void setAssessment(String assessment) { this.assessment = assessment; }
}
