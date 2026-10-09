package com.modeldoctor.dto;

import com.modeldoctor.domain.DecisionConfidence;
import com.modeldoctor.domain.FleetPatternType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FleetPatternDto {

    private Long id;
    private FleetPatternType patternType;
    private String patternKey;
    private String patternTitle;
    private List<String> affectedLineages = new ArrayList<>();
    private int affectedLineagesCount;
    private int totalIncidentsCount;
    private DecisionConfidence confidence;
    private String description;
    private Instant firstObservedAt;
    private Instant lastObservedAt;
    private String nonCausalDisclaimer = "A recurring operational pattern exists across multiple model lineages. Causal relationship has not been established.";

    public FleetPatternDto() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FleetPatternType getPatternType() { return patternType; }
    public void setPatternType(FleetPatternType patternType) { this.patternType = patternType; }

    public String getPatternKey() { return patternKey; }
    public void setPatternKey(String patternKey) { this.patternKey = patternKey; }

    public String getPatternTitle() { return patternTitle; }
    public void setPatternTitle(String patternTitle) { this.patternTitle = patternTitle; }

    public List<String> getAffectedLineages() { return affectedLineages; }
    public void setAffectedLineages(List<String> affectedLineages) { this.affectedLineages = affectedLineages; }

    public int getAffectedLineagesCount() { return affectedLineagesCount; }
    public void setAffectedLineagesCount(int affectedLineagesCount) { this.affectedLineagesCount = affectedLineagesCount; }

    public int getTotalIncidentsCount() { return totalIncidentsCount; }
    public void setTotalIncidentsCount(int totalIncidentsCount) { this.totalIncidentsCount = totalIncidentsCount; }

    public DecisionConfidence getConfidence() { return confidence; }
    public void setConfidence(DecisionConfidence confidence) { this.confidence = confidence; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getFirstObservedAt() { return firstObservedAt; }
    public void setFirstObservedAt(Instant firstObservedAt) { this.firstObservedAt = firstObservedAt; }

    public Instant getLastObservedAt() { return lastObservedAt; }
    public void setLastObservedAt(Instant lastObservedAt) { this.lastObservedAt = lastObservedAt; }

    public String getNonCausalDisclaimer() { return nonCausalDisclaimer; }
    public void setNonCausalDisclaimer(String nonCausalDisclaimer) { this.nonCausalDisclaimer = nonCausalDisclaimer; }
}
