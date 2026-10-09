package com.modeldoctor.dto;

import com.modeldoctor.domain.ModelHealthState;
import com.modeldoctor.domain.ModelReliabilityState;
import java.time.Instant;

public class ReliabilityTrajectoryPointDto {

    private String runId;
    private String runType; // BASELINE (authoritative) or EXPERIMENT (overlay)
    private int reliabilityScore;
    private ModelReliabilityState reliabilityState;
    private ModelHealthState healthState;
    private Integer healthIndex;
    private int activeIncidentsCount;
    private int criticalIncidentsCount;
    private Instant timestamp;

    public ReliabilityTrajectoryPointDto() {}

    public ReliabilityTrajectoryPointDto(String runId, String runType, int reliabilityScore,
                                         ModelReliabilityState reliabilityState, ModelHealthState healthState,
                                         Integer healthIndex, int activeIncidentsCount,
                                         int criticalIncidentsCount, Instant timestamp) {
        this.runId = runId;
        this.runType = runType;
        this.reliabilityScore = reliabilityScore;
        this.reliabilityState = reliabilityState;
        this.healthState = healthState;
        this.healthIndex = healthIndex;
        this.activeIncidentsCount = activeIncidentsCount;
        this.criticalIncidentsCount = criticalIncidentsCount;
        this.timestamp = timestamp;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getRunType() { return runType; }
    public void setRunType(String runType) { this.runType = runType; }

    public int getReliabilityScore() { return reliabilityScore; }
    public void setReliabilityScore(int reliabilityScore) { this.reliabilityScore = reliabilityScore; }

    public ModelReliabilityState getReliabilityState() { return reliabilityState; }
    public void setReliabilityState(ModelReliabilityState reliabilityState) { this.reliabilityState = reliabilityState; }

    public ModelHealthState getHealthState() { return healthState; }
    public void setHealthState(ModelHealthState healthState) { this.healthState = healthState; }

    public Integer getHealthIndex() { return healthIndex; }
    public void setHealthIndex(Integer healthIndex) { this.healthIndex = healthIndex; }

    public int getActiveIncidentsCount() { return activeIncidentsCount; }
    public void setActiveIncidentsCount(int activeIncidentsCount) { this.activeIncidentsCount = activeIncidentsCount; }

    public int getCriticalIncidentsCount() { return criticalIncidentsCount; }
    public void setCriticalIncidentsCount(int criticalIncidentsCount) { this.criticalIncidentsCount = criticalIncidentsCount; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}
