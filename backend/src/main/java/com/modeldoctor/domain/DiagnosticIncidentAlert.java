package com.modeldoctor.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "diagnostic_incident_alerts", indexes = {
        @Index(name = "idx_inc_alert_incident", columnList = "incident_id"),
        @Index(name = "idx_inc_alert_alert", columnList = "alert_id")
})
public class DiagnosticIncidentAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "incident_id", nullable = false)
    private Long incidentId;

    @Column(name = "alert_id", nullable = false)
    private Long alertId;

    @Column(name = "alert_fingerprint", length = 255)
    private String alertFingerprint;

    @Column(name = "correlation_score", nullable = false)
    private int correlationScore = 50;

    @Column(name = "correlation_reasons_json", columnDefinition = "TEXT")
    private String correlationReasonsJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public DiagnosticIncidentAlert() {}

    public DiagnosticIncidentAlert(Long incidentId, Long alertId, String alertFingerprint, int correlationScore, String correlationReasonsJson) {
        this.incidentId = incidentId;
        this.alertId = alertId;
        this.alertFingerprint = alertFingerprint;
        this.correlationScore = correlationScore;
        this.correlationReasonsJson = correlationReasonsJson;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getIncidentId() { return incidentId; }
    public void setIncidentId(Long incidentId) { this.incidentId = incidentId; }

    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }

    public String getAlertFingerprint() { return alertFingerprint; }
    public void setAlertFingerprint(String alertFingerprint) { this.alertFingerprint = alertFingerprint; }

    public int getCorrelationScore() { return correlationScore; }
    public void setCorrelationScore(int correlationScore) { this.correlationScore = correlationScore; }

    public String getCorrelationReasonsJson() { return correlationReasonsJson; }
    public void setCorrelationReasonsJson(String correlationReasonsJson) { this.correlationReasonsJson = correlationReasonsJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
