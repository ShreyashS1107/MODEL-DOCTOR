package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class ContradictoryEvidenceDto {

    private boolean hasConflict = false;
    private String summary;
    private List<String> conflictingSignals = new ArrayList<>();
    private String confidenceImpact;
    private String recommendedAction;

    public ContradictoryEvidenceDto() {}

    public ContradictoryEvidenceDto(boolean hasConflict, String summary, List<String> conflictingSignals,
                                  String confidenceImpact, String recommendedAction) {
        this.hasConflict = hasConflict;
        this.summary = summary;
        this.conflictingSignals = conflictingSignals;
        this.confidenceImpact = confidenceImpact;
        this.recommendedAction = recommendedAction;
    }

    public boolean isHasConflict() { return hasConflict; }
    public void setHasConflict(boolean hasConflict) { this.hasConflict = hasConflict; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<String> getConflictingSignals() { return conflictingSignals; }
    public void setConflictingSignals(List<String> conflictingSignals) { this.conflictingSignals = conflictingSignals; }

    public String getConfidenceImpact() { return confidenceImpact; }
    public void setConfidenceImpact(String confidenceImpact) { this.confidenceImpact = confidenceImpact; }

    public String getRecommendedAction() { return recommendedAction; }
    public void setRecommendedAction(String recommendedAction) { this.recommendedAction = recommendedAction; }
}
