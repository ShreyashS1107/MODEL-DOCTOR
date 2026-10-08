package com.modeldoctor.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class StatisticalEvidenceDto {

    private Integer sampleSize;
    private Integer positiveCount;
    private Integer negativeCount;
    private Integer changedPredictionsCount;
    private Double predictionFlipRate;
    private Map<String, Integer> contingencyTable;
    private Double mcNemarStatistic;
    private Double mcNemarPValue;
    private Double meanProbabilityShift;
    private Double medianProbabilityShift;
    private Double meanAbsoluteProbabilityShift;
    private Double probShiftCiLower;
    private Double probShiftCiUpper;
    private List<Map<String, Object>> subgroups;
    private Integer deterministicSeed;

    public StatisticalEvidenceDto() {}

    public Integer getSampleSize() { return sampleSize; }
    public void setSampleSize(Integer sampleSize) { this.sampleSize = sampleSize; }

    public Integer getPositiveCount() { return positiveCount; }
    public void setPositiveCount(Integer positiveCount) { this.positiveCount = positiveCount; }

    public Integer getNegativeCount() { return negativeCount; }
    public void setNegativeCount(Integer negativeCount) { this.negativeCount = negativeCount; }

    public Integer getChangedPredictionsCount() { return changedPredictionsCount; }
    public void setChangedPredictionsCount(Integer changedPredictionsCount) { this.changedPredictionsCount = changedPredictionsCount; }

    public Double getPredictionFlipRate() { return predictionFlipRate; }
    public void setPredictionFlipRate(Double predictionFlipRate) { this.predictionFlipRate = predictionFlipRate; }

    public Map<String, Integer> getContingencyTable() { return contingencyTable; }
    public void setContingencyTable(Map<String, Integer> contingencyTable) { this.contingencyTable = contingencyTable; }

    public Double getMcNemarStatistic() { return mcNemarStatistic; }
    public void setMcNemarStatistic(Double mcNemarStatistic) { this.mcNemarStatistic = mcNemarStatistic; }

    public Double getMcNemarPValue() { return mcNemarPValue; }
    public void setMcNemarPValue(Double mcNemarPValue) { this.mcNemarPValue = mcNemarPValue; }

    public Double getMeanProbabilityShift() { return meanProbabilityShift; }
    public void setMeanProbabilityShift(Double meanProbabilityShift) { this.meanProbabilityShift = meanProbabilityShift; }

    public Double getMedianProbabilityShift() { return medianProbabilityShift; }
    public void setMedianProbabilityShift(Double medianProbabilityShift) { this.medianProbabilityShift = medianProbabilityShift; }

    public Double getMeanAbsoluteProbabilityShift() { return meanAbsoluteProbabilityShift; }
    public void setMeanAbsoluteProbabilityShift(Double meanAbsoluteProbabilityShift) { this.meanAbsoluteProbabilityShift = meanAbsoluteProbabilityShift; }

    public Double getProbShiftCiLower() { return probShiftCiLower; }
    public void setProbShiftCiLower(Double probShiftCiLower) { this.probShiftCiLower = probShiftCiLower; }

    public Double getProbShiftCiUpper() { return probShiftCiUpper; }
    public void setProbShiftCiUpper(Double probShiftCiUpper) { this.probShiftCiUpper = probShiftCiUpper; }

    public List<Map<String, Object>> getSubgroups() { return subgroups; }
    public void setSubgroups(List<Map<String, Object>> subgroups) { this.subgroups = subgroups; }

    public Integer getDeterministicSeed() { return deterministicSeed; }
    public void setDeterministicSeed(Integer deterministicSeed) { this.deterministicSeed = deterministicSeed; }
}
