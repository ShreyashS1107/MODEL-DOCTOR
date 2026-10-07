package com.modeldoctor.dto;

import java.util.List;

public class TargetSuggestionDto {
    private String column;
    private Double score;
    private List<String> reasons;

    public TargetSuggestionDto() {}

    public TargetSuggestionDto(String column, Double score, List<String> reasons) {
        this.column = column;
        this.score = score;
        this.reasons = reasons;
    }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }

    public List<String> getReasons() { return reasons; }
    public void setReasons(List<String> reasons) { this.reasons = reasons; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String column;
        private Double score;
        private List<String> reasons;

        public Builder column(String column) { this.column = column; return this; }
        public Builder score(Double score) { this.score = score; return this; }
        public Builder reasons(List<String> reasons) { this.reasons = reasons; return this; }

        public TargetSuggestionDto build() {
            return new TargetSuggestionDto(column, score, reasons);
        }
    }
}
