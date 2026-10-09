package com.modeldoctor.dto;

public class RemediationDurabilitySummaryDto {

    private int proposedCount;
    private int validatedCount;
    private int sustainedCount;
    private int temporaryCount;
    private int failedCount;
    private int insufficientFollowupCount;
    private Double durabilityRate; // Percentage e.g. 80.0
    private String summary;

    public RemediationDurabilitySummaryDto() {}

    public RemediationDurabilitySummaryDto(int proposedCount, int validatedCount, int sustainedCount,
                                           int temporaryCount, int failedCount, int insufficientFollowupCount,
                                           Double durabilityRate, String summary) {
        this.proposedCount = proposedCount;
        this.validatedCount = validatedCount;
        this.sustainedCount = sustainedCount;
        this.temporaryCount = temporaryCount;
        this.failedCount = failedCount;
        this.insufficientFollowupCount = insufficientFollowupCount;
        this.durabilityRate = durabilityRate;
        this.summary = summary;
    }

    public int getProposedCount() { return proposedCount; }
    public void setProposedCount(int proposedCount) { this.proposedCount = proposedCount; }

    public int getValidatedCount() { return validatedCount; }
    public void setValidatedCount(int validatedCount) { this.validatedCount = validatedCount; }

    public int getSustainedCount() { return sustainedCount; }
    public void setSustainedCount(int sustainedCount) { this.sustainedCount = sustainedCount; }

    public int getTemporaryCount() { return temporaryCount; }
    public void setTemporaryCount(int temporaryCount) { this.temporaryCount = temporaryCount; }

    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }

    public int getInsufficientFollowupCount() { return insufficientFollowupCount; }
    public void setInsufficientFollowupCount(int insufficientFollowupCount) { this.insufficientFollowupCount = insufficientFollowupCount; }

    public Double getDurabilityRate() { return durabilityRate; }
    public void setDurabilityRate(Double durabilityRate) { this.durabilityRate = durabilityRate; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
