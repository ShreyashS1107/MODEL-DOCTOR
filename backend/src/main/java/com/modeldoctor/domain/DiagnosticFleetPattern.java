package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Discovered fleet-wide recurring operational pattern across multiple model lineages.
 */
@Entity
@Table(name = "diagnostic_fleet_patterns", indexes = {
        @Index(name = "idx_fleet_pat_type", columnList = "pattern_type"),
        @Index(name = "idx_fleet_pat_key", columnList = "pattern_key"),
        @Index(name = "idx_fleet_pat_updated", columnList = "updated_at")
})
public class DiagnosticFleetPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "pattern_type", length = 64, nullable = false)
    private FleetPatternType patternType;

    @Column(name = "pattern_key", length = 255, nullable = false, unique = true)
    private String patternKey;

    @Column(name = "pattern_title", length = 255, nullable = false)
    private String patternTitle;

    @Column(name = "affected_lineages_json", columnDefinition = "TEXT", nullable = false)
    private String affectedLineagesJson;

    @Column(name = "affected_lineages_count", nullable = false)
    private int affectedLineagesCount;

    @Column(name = "total_incidents_count", nullable = false)
    private int totalIncidentsCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence", length = 32, nullable = false)
    private DecisionConfidence confidence = DecisionConfidence.HIGH;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "first_observed_at", nullable = false)
    private Instant firstObservedAt = Instant.now();

    @Column(name = "last_observed_at", nullable = false)
    private Instant lastObservedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DiagnosticFleetPattern() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public FleetPatternType getPatternType() { return patternType; }
    public void setPatternType(FleetPatternType patternType) { this.patternType = patternType; }

    public String getPatternKey() { return patternKey; }
    public void setPatternKey(String patternKey) { this.patternKey = patternKey; }

    public String getPatternTitle() { return patternTitle; }
    public void setPatternTitle(String patternTitle) { this.patternTitle = patternTitle; }

    public String getAffectedLineagesJson() { return affectedLineagesJson; }
    public void setAffectedLineagesJson(String affectedLineagesJson) { this.affectedLineagesJson = affectedLineagesJson; }

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

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
