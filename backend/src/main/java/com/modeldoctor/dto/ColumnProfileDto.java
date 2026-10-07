package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public class ColumnProfileDto {
    private String name;
    private String dtype;
    private String classification; // numeric, categorical, boolean, datetime, text
    private Long nullCount;
    private Double nullPercentage;
    private Long uniqueCount;
    private Double uniquePercentage;

    @JsonProperty("isConstant")
    private Boolean isConstant;

    @JsonProperty("isNearConstant")
    private Boolean isNearConstant;

    private Double min;
    private Double max;
    private Double mean;
    private Double std;
    private Map<String, Double> quantiles;
    private List<String> exampleValues;

    @JsonProperty("isIdentifierLike")
    private Boolean isIdentifierLike;

    @JsonProperty("isHighCardinality")
    private Boolean isHighCardinality;

    public ColumnProfileDto() {}

    public ColumnProfileDto(String name, String dtype, String classification, Long nullCount, Double nullPercentage,
                            Long uniqueCount, Double uniquePercentage, Boolean isConstant, Boolean isNearConstant,
                            Double min, Double max, Double mean, Double std, Map<String, Double> quantiles,
                            List<String> exampleValues, Boolean isIdentifierLike, Boolean isHighCardinality) {
        this.name = name;
        this.dtype = dtype;
        this.classification = classification;
        this.nullCount = nullCount;
        this.nullPercentage = nullPercentage;
        this.uniqueCount = uniqueCount;
        this.uniquePercentage = uniquePercentage;
        this.isConstant = isConstant;
        this.isNearConstant = isNearConstant;
        this.min = min;
        this.max = max;
        this.mean = mean;
        this.std = std;
        this.quantiles = quantiles;
        this.exampleValues = exampleValues;
        this.isIdentifierLike = isIdentifierLike;
        this.isHighCardinality = isHighCardinality;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDtype() { return dtype; }
    public void setDtype(String dtype) { this.dtype = dtype; }

    public String getClassification() { return classification; }
    public void setClassification(String classification) { this.classification = classification; }

    public Long getNullCount() { return nullCount; }
    public void setNullCount(Long nullCount) { this.nullCount = nullCount; }

    public Double getNullPercentage() { return nullPercentage; }
    public void setNullPercentage(Double nullPercentage) { this.nullPercentage = nullPercentage; }

    public Long getUniqueCount() { return uniqueCount; }
    public void setUniqueCount(Long uniqueCount) { this.uniqueCount = uniqueCount; }

    public Double getUniquePercentage() { return uniquePercentage; }
    public void setUniquePercentage(Double uniquePercentage) { this.uniquePercentage = uniquePercentage; }

    @JsonProperty("isConstant")
    public Boolean getIsConstant() { return isConstant; }

    @JsonProperty("isConstant")
    public void setIsConstant(Boolean isConstant) { this.isConstant = isConstant; }

    @JsonProperty("isNearConstant")
    public Boolean getIsNearConstant() { return isNearConstant; }

    @JsonProperty("isNearConstant")
    public void setIsNearConstant(Boolean isNearConstant) { this.isNearConstant = isNearConstant; }

    public Double getMin() { return min; }
    public void setMin(Double min) { this.min = min; }

    public Double getMax() { return max; }
    public void setMax(Double max) { this.max = max; }

    public Double getMean() { return mean; }
    public void setMean(Double mean) { this.mean = mean; }

    public Double getStd() { return std; }
    public void setStd(Double std) { this.std = std; }

    public Map<String, Double> getQuantiles() { return quantiles; }
    public void setQuantiles(Map<String, Double> quantiles) { this.quantiles = quantiles; }

    public List<String> getExampleValues() { return exampleValues; }
    public void setExampleValues(List<String> exampleValues) { this.exampleValues = exampleValues; }

    @JsonProperty("isIdentifierLike")
    public Boolean getIsIdentifierLike() { return isIdentifierLike; }

    @JsonProperty("isIdentifierLike")
    public void setIsIdentifierLike(Boolean isIdentifierLike) { this.isIdentifierLike = isIdentifierLike; }

    @JsonProperty("isHighCardinality")
    public Boolean getIsHighCardinality() { return isHighCardinality; }

    @JsonProperty("isHighCardinality")
    public void setIsHighCardinality(Boolean isHighCardinality) { this.isHighCardinality = isHighCardinality; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String name;
        private String dtype;
        private String classification;
        private Long nullCount;
        private Double nullPercentage;
        private Long uniqueCount;
        private Double uniquePercentage;
        private Boolean isConstant;
        private Boolean isNearConstant;
        private Double min;
        private Double max;
        private Double mean;
        private Double std;
        private Map<String, Double> quantiles;
        private List<String> exampleValues;
        private Boolean isIdentifierLike;
        private Boolean isHighCardinality;

        public Builder name(String name) { this.name = name; return this; }
        public Builder dtype(String dtype) { this.dtype = dtype; return this; }
        public Builder classification(String classification) { this.classification = classification; return this; }
        public Builder nullCount(Long nullCount) { this.nullCount = nullCount; return this; }
        public Builder nullPercentage(Double nullPercentage) { this.nullPercentage = nullPercentage; return this; }
        public Builder uniqueCount(Long uniqueCount) { this.uniqueCount = uniqueCount; return this; }
        public Builder uniquePercentage(Double uniquePercentage) { this.uniquePercentage = uniquePercentage; return this; }
        public Builder isConstant(Boolean isConstant) { this.isConstant = isConstant; return this; }
        public Builder isNearConstant(Boolean isNearConstant) { this.isNearConstant = isNearConstant; return this; }
        public Builder min(Double min) { this.min = min; return this; }
        public Builder max(Double max) { this.max = max; return this; }
        public Builder mean(Double mean) { this.mean = mean; return this; }
        public Builder std(Double std) { this.std = std; return this; }
        public Builder quantiles(Map<String, Double> quantiles) { this.quantiles = quantiles; return this; }
        public Builder exampleValues(List<String> exampleValues) { this.exampleValues = exampleValues; return this; }
        public Builder isIdentifierLike(Boolean isIdentifierLike) { this.isIdentifierLike = isIdentifierLike; return this; }
        public Builder isHighCardinality(Boolean isHighCardinality) { this.isHighCardinality = isHighCardinality; return this; }

        public ColumnProfileDto build() {
            return new ColumnProfileDto(name, dtype, classification, nullCount, nullPercentage, uniqueCount,
                    uniquePercentage, isConstant, isNearConstant, min, max, mean, std, quantiles, exampleValues,
                    isIdentifierLike, isHighCardinality);
        }
    }
}
