package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class ModelComparisonDto {

    private ModelReliabilityProfileDto left;
    private ModelReliabilityProfileDto right;
    private int scoreDelta; // left.score - right.score
    private List<String> keyDifferences = new ArrayList<>();
    private String governanceComparisonSummary;

    public ModelComparisonDto() {}

    public ModelComparisonDto(ModelReliabilityProfileDto left, ModelReliabilityProfileDto right,
                              int scoreDelta, List<String> keyDifferences, String governanceComparisonSummary) {
        this.left = left;
        this.right = right;
        this.scoreDelta = scoreDelta;
        this.keyDifferences = keyDifferences != null ? keyDifferences : new ArrayList<>();
        this.governanceComparisonSummary = governanceComparisonSummary;
    }

    public ModelReliabilityProfileDto getLeft() { return left; }
    public void setLeft(ModelReliabilityProfileDto left) { this.left = left; }

    public ModelReliabilityProfileDto getRight() { return right; }
    public void setRight(ModelReliabilityProfileDto right) { this.right = right; }

    public int getScoreDelta() { return scoreDelta; }
    public void setScoreDelta(int scoreDelta) { this.scoreDelta = scoreDelta; }

    public List<String> getKeyDifferences() { return keyDifferences; }
    public void setKeyDifferences(List<String> keyDifferences) { this.keyDifferences = keyDifferences; }

    public String getGovernanceComparisonSummary() { return governanceComparisonSummary; }
    public void setGovernanceComparisonSummary(String governanceComparisonSummary) { this.governanceComparisonSummary = governanceComparisonSummary; }
}
