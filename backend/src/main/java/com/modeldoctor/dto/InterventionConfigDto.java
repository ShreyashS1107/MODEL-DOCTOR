package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.HashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class InterventionConfigDto {

    private String feature;
    private String strategy = "zero"; // "drop", "zero", "median"
    private String transformationType = "CLIP"; // "CLIP", "WINSORIZE", "MISSING_REPLACE", "STANDARDIZE"
    private Double lowerQuantile = 0.01;
    private Double upperQuantile = 0.99;
    private Double missingnessRate = 0.10;
    private Double baselineThreshold = 0.50;
    private Double candidateThreshold = 0.50;
    private String calibrationMethod = "PLATT"; // "PLATT", "ISOTONIC"
    private String subgroupAttribute;
    private Integer deterministicSeed = 42;
    private Map<String, Object> parameters = new HashMap<>();

    public InterventionConfigDto() {}

    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public String getTransformationType() { return transformationType; }
    public void setTransformationType(String transformationType) { this.transformationType = transformationType; }

    public Double getLowerQuantile() { return lowerQuantile; }
    public void setLowerQuantile(Double lowerQuantile) { this.lowerQuantile = lowerQuantile; }

    public Double getUpperQuantile() { return upperQuantile; }
    public void setUpperQuantile(Double upperQuantile) { this.upperQuantile = upperQuantile; }

    public Double getMissingnessRate() { return missingnessRate; }
    public void setMissingnessRate(Double missingnessRate) { this.missingnessRate = missingnessRate; }

    public Double getBaselineThreshold() { return baselineThreshold; }
    public void setBaselineThreshold(Double baselineThreshold) { this.baselineThreshold = baselineThreshold; }

    public Double getCandidateThreshold() { return candidateThreshold; }
    public void setCandidateThreshold(Double candidateThreshold) { this.candidateThreshold = candidateThreshold; }

    public String getCalibrationMethod() { return calibrationMethod; }
    public void setCalibrationMethod(String calibrationMethod) { this.calibrationMethod = calibrationMethod; }

    public String getSubgroupAttribute() { return subgroupAttribute; }
    public void setSubgroupAttribute(String subgroupAttribute) { this.subgroupAttribute = subgroupAttribute; }

    public Integer getDeterministicSeed() { return deterministicSeed; }
    public void setDeterministicSeed(Integer deterministicSeed) { this.deterministicSeed = deterministicSeed; }

    public Map<String, Object> getParameters() { return parameters; }
    public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
}
