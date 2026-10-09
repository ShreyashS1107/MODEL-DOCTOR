package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class ReliabilityScoreBreakdownDto {

    public static class ScoreItem {
        private String code;
        private String description;
        private int points; // positive for addition, negative for deduction
        private String evidence;

        public ScoreItem() {}

        public ScoreItem(String code, String description, int points, String evidence) {
            this.code = code;
            this.description = description;
            this.points = points;
            this.evidence = evidence;
        }

        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getPoints() { return points; }
        public void setPoints(int points) { this.points = points; }

        public String getEvidence() { return evidence; }
        public void setEvidence(String evidence) { this.evidence = evidence; }
    }

    private int baseScore = 100;
    private int totalDeductions = 0;
    private int totalBonuses = 0;
    private int netScore = 100;
    private List<ScoreItem> items = new ArrayList<>();

    public ReliabilityScoreBreakdownDto() {}

    public ReliabilityScoreBreakdownDto(int baseScore, int totalDeductions, int totalBonuses, int netScore, List<ScoreItem> items) {
        this.baseScore = baseScore;
        this.totalDeductions = totalDeductions;
        this.totalBonuses = totalBonuses;
        this.netScore = netScore;
        this.items = items != null ? items : new ArrayList<>();
    }

    public int getBaseScore() { return baseScore; }
    public void setBaseScore(int baseScore) { this.baseScore = baseScore; }

    public int getTotalDeductions() { return totalDeductions; }
    public void setTotalDeductions(int totalDeductions) { this.totalDeductions = totalDeductions; }

    public int getTotalBonuses() { return totalBonuses; }
    public void setTotalBonuses(int totalBonuses) { this.totalBonuses = totalBonuses; }

    public int getNetScore() { return netScore; }
    public void setNetScore(int netScore) { this.netScore = netScore; }

    public List<ScoreItem> getItems() { return items; }
    public void setItems(List<ScoreItem> items) { this.items = items; }
}
