package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class HealthIndexBreakdownDto {

    private int score = 100;
    private int baseScore = 100;
    private int totalPenalty = 0;
    private List<HealthPenaltyDto> penalties = new ArrayList<>();
    private boolean sufficientData = true;

    public HealthIndexBreakdownDto() {}

    public HealthIndexBreakdownDto(int score, int totalPenalty, List<HealthPenaltyDto> penalties, boolean sufficientData) {
        this.score = score;
        this.baseScore = 100;
        this.totalPenalty = totalPenalty;
        this.penalties = penalties;
        this.sufficientData = sufficientData;
    }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public int getBaseScore() { return baseScore; }
    public void setBaseScore(int baseScore) { this.baseScore = baseScore; }

    public int getTotalPenalty() { return totalPenalty; }
    public void setTotalPenalty(int totalPenalty) { this.totalPenalty = totalPenalty; }

    public List<HealthPenaltyDto> getPenalties() { return penalties; }
    public void setPenalties(List<HealthPenaltyDto> penalties) { this.penalties = penalties; }

    public boolean isSufficientData() { return sufficientData; }
    public void setSufficientData(boolean sufficientData) { this.sufficientData = sufficientData; }
}
