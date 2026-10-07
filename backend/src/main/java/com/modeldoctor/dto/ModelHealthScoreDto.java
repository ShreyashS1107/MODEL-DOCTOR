package com.modeldoctor.dto;

import java.util.Map;

public class ModelHealthScoreDto {
    private String modelId;
    private String modelName;
    private Double overallScore;
    private String statusCategory;
    private Map<String, Double> categoryBreakdown;
    private Boolean isMockData;

    public ModelHealthScoreDto() {}

    public ModelHealthScoreDto(String modelId, String modelName, Double overallScore, String statusCategory,
                               Map<String, Double> categoryBreakdown, Boolean isMockData) {
        this.modelId = modelId;
        this.modelName = modelName;
        this.overallScore = overallScore;
        this.statusCategory = statusCategory;
        this.categoryBreakdown = categoryBreakdown;
        this.isMockData = isMockData;
    }

    public String getModelId() { return modelId; }
    public void setModelId(String modelId) { this.modelId = modelId; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Double getOverallScore() { return overallScore; }
    public void setOverallScore(Double overallScore) { this.overallScore = overallScore; }

    public String getStatusCategory() { return statusCategory; }
    public void setStatusCategory(String statusCategory) { this.statusCategory = statusCategory; }

    public Map<String, Double> getCategoryBreakdown() { return categoryBreakdown; }
    public void setCategoryBreakdown(Map<String, Double> categoryBreakdown) { this.categoryBreakdown = categoryBreakdown; }

    public Boolean getIsMockData() { return isMockData; }
    public void setIsMockData(Boolean isMockData) { this.isMockData = isMockData; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String modelId;
        private String modelName;
        private Double overallScore;
        private String statusCategory;
        private Map<String, Double> categoryBreakdown;
        private Boolean isMockData;

        public Builder modelId(String modelId) { this.modelId = modelId; return this; }
        public Builder modelName(String modelName) { this.modelName = modelName; return this; }
        public Builder overallScore(Double overallScore) { this.overallScore = overallScore; return this; }
        public Builder statusCategory(String statusCategory) { this.statusCategory = statusCategory; return this; }
        public Builder categoryBreakdown(Map<String, Double> categoryBreakdown) { this.categoryBreakdown = categoryBreakdown; return this; }
        public Builder isMockData(Boolean isMockData) { this.isMockData = isMockData; return this; }

        public ModelHealthScoreDto build() {
            return new ModelHealthScoreDto(modelId, modelName, overallScore, statusCategory, categoryBreakdown, isMockData);
        }
    }
}
