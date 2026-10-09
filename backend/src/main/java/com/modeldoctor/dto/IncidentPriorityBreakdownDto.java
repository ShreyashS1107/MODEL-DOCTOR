package com.modeldoctor.dto;

import java.util.ArrayList;
import java.util.List;

public class IncidentPriorityBreakdownDto {

    private int baseScore;
    private int severityContribution;
    private int independentEvidenceContribution;
    private int persistenceContribution;
    private int healthImpactContribution;
    private int totalPriorityScore;
    private String priorityTier;
    private List<String> explanationItems = new ArrayList<>();

    public IncidentPriorityBreakdownDto() {}

    public IncidentPriorityBreakdownDto(int severityContribution, int independentEvidenceContribution,
                                        int persistenceContribution, int healthImpactContribution,
                                        int totalPriorityScore, String priorityTier) {
        this.severityContribution = severityContribution;
        this.independentEvidenceContribution = independentEvidenceContribution;
        this.persistenceContribution = persistenceContribution;
        this.healthImpactContribution = healthImpactContribution;
        this.totalPriorityScore = totalPriorityScore;
        this.priorityTier = priorityTier;
    }

    public int getBaseScore() { return baseScore; }
    public void setBaseScore(int baseScore) { this.baseScore = baseScore; }

    public int getSeverityContribution() { return severityContribution; }
    public void setSeverityContribution(int severityContribution) { this.severityContribution = severityContribution; }

    public int getIndependentEvidenceContribution() { return independentEvidenceContribution; }
    public void setIndependentEvidenceContribution(int independentEvidenceContribution) { this.independentEvidenceContribution = independentEvidenceContribution; }

    public int getPersistenceContribution() { return persistenceContribution; }
    public void setPersistenceContribution(int persistenceContribution) { this.persistenceContribution = persistenceContribution; }

    public int getHealthImpactContribution() { return healthImpactContribution; }
    public void setHealthImpactContribution(int healthImpactContribution) { this.healthImpactContribution = healthImpactContribution; }

    public int getTotalPriorityScore() { return totalPriorityScore; }
    public void setTotalPriorityScore(int totalPriorityScore) { this.totalPriorityScore = totalPriorityScore; }

    public String getPriorityTier() { return priorityTier; }
    public void setPriorityTier(String priorityTier) { this.priorityTier = priorityTier; }

    public List<String> getExplanationItems() { return explanationItems; }
    public void setExplanationItems(List<String> explanationItems) { this.explanationItems = explanationItems; }
}
