package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.IncidentDecisionState;

public class IncidentDecisionDto {

    private IncidentDecisionState recommendation;
    private DecisionConfidence confidence;
    private String rationale;
    private String nextAction;
    private String constraints = "This recommendation is evidence-driven and associative. It does not establish causality or authorize autonomous model modification.";

    public IncidentDecisionDto() {}

    public IncidentDecisionDto(IncidentDecisionState recommendation, DecisionConfidence confidence,
                               String rationale, String nextAction) {
        this.recommendation = recommendation;
        this.confidence = confidence;
        this.rationale = rationale;
        this.nextAction = nextAction;
    }

    public IncidentDecisionState getRecommendation() { return recommendation; }
    public void setRecommendation(IncidentDecisionState recommendation) { this.recommendation = recommendation; }

    public DecisionConfidence getConfidence() { return confidence; }
    public void setConfidence(DecisionConfidence confidence) { this.confidence = confidence; }

    public String getRationale() { return rationale; }
    public void setRationale(String rationale) { this.rationale = rationale; }

    public String getNextAction() { return nextAction; }
    public void setNextAction(String nextAction) { this.nextAction = nextAction; }

    public String getConstraints() { return constraints; }
    public void setConstraints(String constraints) { this.constraints = constraints; }
}
