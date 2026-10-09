package com.modeldoctor.dto;

public class RecoveryProfileDto {

    private int degradationEventsCount;
    private int recoveredEventsCount;
    private int unresolvedEventsCount;
    private Double recoveryRate; // Percentage e.g. 75.0
    private int regressionsAfterRecoveryCount;
    private int repeatedCyclesCount;
    private String summary;

    public RecoveryProfileDto() {}

    public RecoveryProfileDto(int degradationEventsCount, int recoveredEventsCount, int unresolvedEventsCount,
                              Double recoveryRate, int regressionsAfterRecoveryCount, int repeatedCyclesCount, String summary) {
        this.degradationEventsCount = degradationEventsCount;
        this.recoveredEventsCount = recoveredEventsCount;
        this.unresolvedEventsCount = unresolvedEventsCount;
        this.recoveryRate = recoveryRate;
        this.regressionsAfterRecoveryCount = regressionsAfterRecoveryCount;
        this.repeatedCyclesCount = repeatedCyclesCount;
        this.summary = summary;
    }

    public int getDegradationEventsCount() { return degradationEventsCount; }
    public void setDegradationEventsCount(int degradationEventsCount) { this.degradationEventsCount = degradationEventsCount; }

    public int getRecoveredEventsCount() { return recoveredEventsCount; }
    public void setRecoveredEventsCount(int recoveredEventsCount) { this.recoveredEventsCount = recoveredEventsCount; }

    public int getUnresolvedEventsCount() { return unresolvedEventsCount; }
    public void setUnresolvedEventsCount(int unresolvedEventsCount) { this.unresolvedEventsCount = unresolvedEventsCount; }

    public Double getRecoveryRate() { return recoveryRate; }
    public void setRecoveryRate(Double recoveryRate) { this.recoveryRate = recoveryRate; }

    public int getRegressionsAfterRecoveryCount() { return regressionsAfterRecoveryCount; }
    public void setRegressionsAfterRecoveryCount(int regressionsAfterRecoveryCount) { this.regressionsAfterRecoveryCount = regressionsAfterRecoveryCount; }

    public int getRepeatedCyclesCount() { return repeatedCyclesCount; }
    public void setRepeatedCyclesCount(int repeatedCyclesCount) { this.repeatedCyclesCount = repeatedCyclesCount; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
}
