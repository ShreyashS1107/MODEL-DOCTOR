package com.modeldoctor.intelligence.monitoring;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import com.modeldoctor.repository.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@SuppressWarnings("null")
public class AlertLifecycleManager {

    private final DiagnosticOperationalAlertRepository alertRepository;
    private final DiagnosticAlertEventRepository eventRepository;

    public AlertLifecycleManager(
            DiagnosticOperationalAlertRepository alertRepository,
            DiagnosticAlertEventRepository eventRepository) {
        this.alertRepository = alertRepository;
        this.eventRepository = eventRepository;
    }

    public String computeFingerprint(String modelLineageId, String alertType, String targetType, String targetKey, String metricName) {
        String cleanType = alertType != null ? alertType.trim() : "GENERIC_ALERT";
        String cleanTargetType = targetType != null ? targetType.trim() : "GLOBAL";
        String cleanTargetKey = targetKey != null ? targetKey.trim() : "GLOBAL";
        String cleanMetric = metricName != null ? metricName.trim() : "GLOBAL";
        return String.format("%s::%s::%s::%s::%s", modelLineageId, cleanType, cleanTargetType, cleanTargetKey, cleanMetric);
    }

    public List<DiagnosticOperationalAlert> synchronizeAlerts(
            String modelLineageId,
            String latestRunId,
            int currentRunIndex,
            List<TemporalAlertDto> rawTemporalAlerts,
            List<InvestigationTargetDto> investigations,
            List<DiagnosticRemediationDto> remediations,
            List<DiagnosticExperimentDto> experiments,
            List<IssueTrackDto> issueTracks,
            DiagnosticMonitoringPolicy policy) {

        List<DiagnosticOperationalAlert> existingAlerts = alertRepository.findByModelLineageIdOrderByLastObservedAtDesc(modelLineageId);
        Map<String, DiagnosticOperationalAlert> alertByFingerprint = new HashMap<>();
        for (DiagnosticOperationalAlert a : existingAlerts) {
            alertByFingerprint.put(a.getAlertFingerprint(), a);
        }

        Set<String> activeFingerprintsInRun = new HashSet<>();
        List<DiagnosticOperationalAlert> updatedList = new ArrayList<>();

        for (TemporalAlertDto tAlert : rawTemporalAlerts) {
            String fp = computeFingerprint(
                    modelLineageId,
                    tAlert.getAlertType(),
                    tAlert.getTargetType(),
                    tAlert.getTargetKey(),
                    tAlert.getMetricName()
            );
            activeFingerprintsInRun.add(fp);

            DiagnosticOperationalAlert alert = alertByFingerprint.get(fp);
            if (alert == null) {
                // Brand new operational alert
                alert = new DiagnosticOperationalAlert();
                alert.setModelLineageId(modelLineageId);
                alert.setAlertFingerprint(fp);
                alert.setAlertType(tAlert.getAlertType());
                alert.setCurrentSeverity(tAlert.getPriority() != null ? tAlert.getPriority() : "MEDIUM");
                alert.setPreviousSeverity(null);
                alert.setSeverityChange("NEW");
                alert.setLifecycleState(AlertLifecycleState.OPEN);
                alert.setTargetType(tAlert.getTargetType() != null ? tAlert.getTargetType() : "GLOBAL");
                alert.setTargetKey(tAlert.getTargetKey() != null ? tAlert.getTargetKey() : "GLOBAL");
                alert.setMetricName(tAlert.getMetricName());
                alert.setCurrentValue(tAlert.getCurrentValue());
                alert.setReferenceValue(tAlert.getReferenceValue());
                alert.setTriggerDescription(tAlert.getTriggerDescription());
                alert.setConfidence(tAlert.getConfidence() != null ? tAlert.getConfidence() : "HIGH");
                alert.setFirstSeenRunId(latestRunId);
                alert.setLastSeenRunId(latestRunId);
                alert.setFirstObservedAt(tAlert.getCreatedAt() != null ? tAlert.getCreatedAt() : Instant.now());
                alert.setLastObservedAt(Instant.now());
                alert.setOccurrenceCount(1);
                alert.setConsecutiveCount(1);

                linkCorrelations(alert, investigations, remediations, experiments, issueTracks);
                alert = alertRepository.save(alert);

                recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), null,
                        AlertLifecycleState.OPEN.name(), "SYSTEM", "CREATE",
                        "Initial alert detection: " + alert.getTriggerDescription());
            } else {
                // Existing operational alert updated
                // 1. Check suppression expiration
                if (alert.getLifecycleState() == AlertLifecycleState.SUPPRESSED) {
                    if (alert.getSuppressedUntil() != null && Instant.now().isAfter(alert.getSuppressedUntil())) {
                        AlertLifecycleState old = alert.getLifecycleState();
                        alert.setLifecycleState(AlertLifecycleState.OPEN);
                        alert.setSuppressionReason(null);
                        alert.setSuppressedUntil(null);
                        recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                                AlertLifecycleState.OPEN.name(), "SYSTEM", "SUPPRESSION_EXPIRED",
                                "Alert suppression period has expired; re-evaluating active condition.");
                    }
                }

                // 2. Reopen if it was resolved
                if (alert.getLifecycleState() == AlertLifecycleState.RESOLVED) {
                    AlertLifecycleState old = alert.getLifecycleState();
                    alert.setLifecycleState(AlertLifecycleState.REOPENED);
                    alert.setReopenCount(alert.getReopenCount() + 1);
                    recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                            AlertLifecycleState.REOPENED.name(), "SYSTEM", "REOPEN",
                            "Condition re-occurred in run " + latestRunId + ": " + tAlert.getTriggerDescription());
                }

                // 3. Severity evolution tracking
                String newSeverity = tAlert.getPriority() != null ? tAlert.getPriority() : "MEDIUM";
                String oldSeverity = alert.getCurrentSeverity();
                alert.setPreviousSeverity(oldSeverity);
                alert.setCurrentSeverity(newSeverity);

                int sevComparison = compareSeverity(newSeverity, oldSeverity);
                if (sevComparison > 0) {
                    alert.setSeverityChange("ESCALATED");
                    alert.setEscalationCount(alert.getEscalationCount() + 1);
                    recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), oldSeverity,
                            newSeverity, "SYSTEM", "ESCALATE",
                            String.format("Alert severity escalated from %s to %s", oldSeverity, newSeverity));
                } else if (sevComparison < 0) {
                    alert.setSeverityChange("DEESCALATED");
                    alert.setRecoveryCount(alert.getRecoveryCount() + 1);
                    recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), oldSeverity,
                            newSeverity, "SYSTEM", "DEESCALATE",
                            String.format("Alert severity reduced from %s to %s", oldSeverity, newSeverity));
                } else {
                    alert.setSeverityChange("UNCHANGED");
                }

                // 4. Update counters & metadata
                alert.setOccurrenceCount(alert.getOccurrenceCount() + 1);
                alert.setConsecutiveCount(alert.getConsecutiveCount() + 1);
                alert.setLastSeenRunId(latestRunId);
                alert.setLastObservedAt(Instant.now());
                alert.setCurrentValue(tAlert.getCurrentValue());
                alert.setReferenceValue(tAlert.getReferenceValue());
                alert.setTriggerDescription(tAlert.getTriggerDescription());
                alert.setConfidence(tAlert.getConfidence() != null ? tAlert.getConfidence() : alert.getConfidence());

                linkCorrelations(alert, investigations, remediations, experiments, issueTracks);
                alert = alertRepository.save(alert);
            }

            updatedList.add(alert);
        }

        // Check alerts that were NOT active in this run -> assess automatic recovery / resolution
        for (DiagnosticOperationalAlert existing : existingAlerts) {
            if (!activeFingerprintsInRun.contains(existing.getAlertFingerprint())) {
                existing.setConsecutiveCount(0); // reset consecutive count as condition was absent

                // If condition absent and alert was not resolved or suppressed
                if (existing.getLifecycleState() == AlertLifecycleState.OPEN ||
                        existing.getLifecycleState() == AlertLifecycleState.ACKNOWLEDGED ||
                        existing.getLifecycleState() == AlertLifecycleState.INVESTIGATING ||
                        existing.getLifecycleState() == AlertLifecycleState.REOPENED) {

                    // Resolve if consecutive recovery threshold met
                    AlertLifecycleState old = existing.getLifecycleState();
                    existing.setLifecycleState(AlertLifecycleState.RESOLVED);
                    existing.setResolvedAt(Instant.now());
                    existing.setResolvedBy("SYSTEM");
                    existing.setResolutionReason("Condition no longer detected in operational baseline run " + latestRunId);
                    existing.setRecoveryCount(existing.getRecoveryCount() + 1);
                    alertRepository.save(existing);

                    recordEvent(modelLineageId, existing.getId(), existing.getAlertFingerprint(), old.name(),
                            AlertLifecycleState.RESOLVED.name(), "SYSTEM", "AUTO_RESOLVE",
                            "Condition satisfied recovery threshold in operational run " + latestRunId);
                }
            }
        }

        return alertRepository.findByModelLineageIdOrderByLastObservedAtDesc(modelLineageId);
    }

    public DiagnosticOperationalAlert acknowledge(String modelLineageId, Long alertId, String actor) {
        DiagnosticOperationalAlert alert = getAlertOrThrow(modelLineageId, alertId);
        validateTransition(alert, AlertLifecycleState.ACKNOWLEDGED);

        AlertLifecycleState old = alert.getLifecycleState();
        alert.setLifecycleState(AlertLifecycleState.ACKNOWLEDGED);
        alert.setAcknowledgedBy(actor != null ? actor : "USER");
        alert.setAcknowledgedAt(Instant.now());
        alert = alertRepository.save(alert);

        recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                AlertLifecycleState.ACKNOWLEDGED.name(), alert.getAcknowledgedBy(), "ACKNOWLEDGE",
                "Alert acknowledged by " + alert.getAcknowledgedBy());

        return alert;
    }

    public DiagnosticOperationalAlert investigate(String modelLineageId, Long alertId, String actor) {
        DiagnosticOperationalAlert alert = getAlertOrThrow(modelLineageId, alertId);
        validateTransition(alert, AlertLifecycleState.INVESTIGATING);

        AlertLifecycleState old = alert.getLifecycleState();
        alert.setLifecycleState(AlertLifecycleState.INVESTIGATING);
        alert.setInvestigatedBy(actor != null ? actor : "USER");
        alert.setInvestigatedAt(Instant.now());
        alert = alertRepository.save(alert);

        recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                AlertLifecycleState.INVESTIGATING.name(), alert.getInvestigatedBy(), "INVESTIGATE",
                "Root-cause investigation opened by " + alert.getInvestigatedBy());

        return alert;
    }

    public DiagnosticOperationalAlert suppress(String modelLineageId, Long alertId, String reason, Integer durationHours, String actor) {
        DiagnosticOperationalAlert alert = getAlertOrThrow(modelLineageId, alertId);
        validateTransition(alert, AlertLifecycleState.SUPPRESSED);

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A valid reason is required to suppress an operational alert.");
        }

        AlertLifecycleState old = alert.getLifecycleState();
        int hours = (durationHours != null && durationHours > 0) ? durationHours : 24;
        Instant expiry = Instant.now().plus(hours, ChronoUnit.HOURS);

        alert.setLifecycleState(AlertLifecycleState.SUPPRESSED);
        alert.setSuppressionReason(reason);
        alert.setSuppressedUntil(expiry);
        alert.setSuppressedBy(actor != null ? actor : "USER");
        alert = alertRepository.save(alert);

        recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                AlertLifecycleState.SUPPRESSED.name(), alert.getSuppressedBy(), "SUPPRESS",
                String.format("Suppressed for %dh (%s): %s", hours, expiry, reason));

        return alert;
    }

    public DiagnosticOperationalAlert resolve(String modelLineageId, Long alertId, String reason, String actor) {
        DiagnosticOperationalAlert alert = getAlertOrThrow(modelLineageId, alertId);
        validateTransition(alert, AlertLifecycleState.RESOLVED);

        AlertLifecycleState old = alert.getLifecycleState();
        alert.setLifecycleState(AlertLifecycleState.RESOLVED);
        alert.setResolvedBy(actor != null ? actor : "USER");
        alert.setResolvedAt(Instant.now());
        alert.setResolutionReason(reason != null ? reason : "Manually marked as resolved by engineer.");
        alert = alertRepository.save(alert);

        recordEvent(modelLineageId, alert.getId(), alert.getAlertFingerprint(), old.name(),
                AlertLifecycleState.RESOLVED.name(), alert.getResolvedBy(), "RESOLVE",
                alert.getResolutionReason());

        return alert;
    }

    private void linkCorrelations(DiagnosticOperationalAlert alert,
                                  List<InvestigationTargetDto> investigations,
                                  List<DiagnosticRemediationDto> remediations,
                                  List<DiagnosticExperimentDto> experiments,
                                  List<IssueTrackDto> issueTracks) {

        String targetKey = alert.getTargetKey();
        if (targetKey == null) return;

        // 1. Link Phase 6 Investigation Target
        if (investigations != null) {
            for (InvestigationTargetDto it : investigations) {
                if (targetKey.equalsIgnoreCase(it.getTargetKey())) {
                    alert.setRelatedInvestigationTargetKey(it.getTargetKey());
                    break;
                }
            }
        }

        // 2. Link Phase 7 Remediation
        if (remediations != null) {
            for (DiagnosticRemediationDto rem : remediations) {
                if (targetKey.equalsIgnoreCase(rem.getTargetKey())) {
                    alert.setRelatedRemediationId(rem.getId());
                    break;
                }
            }
        }

        // 3. Link Phase 8 Experiment
        if (experiments != null) {
            for (DiagnosticExperimentDto exp : experiments) {
                if (targetKey.equalsIgnoreCase(exp.getTargetKey())) {
                    alert.setRelatedExperimentId(exp.getId());
                    break;
                }
            }
        }

        // 4. Link Phase 9 Issue Track
        if (issueTracks != null) {
            for (IssueTrackDto trk : issueTracks) {
                if (targetKey.equalsIgnoreCase(trk.getTargetKey())) {
                    alert.setRelatedIssueTrackId(trk.getId());
                    break;
                }
            }
        }
    }

    private void validateTransition(DiagnosticOperationalAlert alert, AlertLifecycleState targetState) {
        if (!alert.getLifecycleState().canTransitionTo(targetState)) {
            throw new IllegalStateException(String.format(
                    "Invalid alert lifecycle transition: Cannot transition from %s to %s for alert '%s'",
                    alert.getLifecycleState(), targetState, alert.getAlertFingerprint()
            ));
        }
    }

    private DiagnosticOperationalAlert getAlertOrThrow(String modelLineageId, Long alertId) {
        DiagnosticOperationalAlert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new IllegalArgumentException("Operational alert not found with ID: " + alertId));
        if (!alert.getModelLineageId().equalsIgnoreCase(modelLineageId)) {
            throw new IllegalArgumentException(String.format(
                    "Alert %d does not belong to model lineage '%s'", alertId, modelLineageId));
        }
        return alert;
    }

    public void recordEvent(String modelLineageId, Long alertId, String alertFingerprint,
                            String previousState, String newState, String actor,
                            String action, String reason) {
        DiagnosticAlertEvent event = new DiagnosticAlertEvent(
                modelLineageId, alertId, alertFingerprint, previousState, newState, actor, action, reason
        );
        eventRepository.save(event);
    }

    public OperationalAlertDto toDto(DiagnosticOperationalAlert a) {
        OperationalAlertDto dto = new OperationalAlertDto();
        dto.setId(a.getId());
        dto.setModelLineageId(a.getModelLineageId());
        dto.setAlertFingerprint(a.getAlertFingerprint());
        dto.setAlertType(a.getAlertType());
        dto.setCurrentSeverity(a.getCurrentSeverity());
        dto.setPreviousSeverity(a.getPreviousSeverity());
        dto.setSeverityChange(a.getSeverityChange());
        dto.setLifecycleState(a.getLifecycleState());
        dto.setTargetType(a.getTargetType());
        dto.setTargetKey(a.getTargetKey());
        dto.setMetricName(a.getMetricName());
        dto.setCurrentValue(a.getCurrentValue());
        dto.setReferenceValue(a.getReferenceValue());
        dto.setTriggerDescription(a.getTriggerDescription());
        dto.setEvidenceJson(a.getEvidenceJson());
        dto.setConfidence(a.getConfidence());
        dto.setSourceModule(a.getSourceModule());
        dto.setFirstSeenRunId(a.getFirstSeenRunId());
        dto.setLastSeenRunId(a.getLastSeenRunId());
        dto.setFirstObservedAt(a.getFirstObservedAt());
        dto.setLastObservedAt(a.getLastObservedAt());
        dto.setOccurrenceCount(a.getOccurrenceCount());
        dto.setConsecutiveCount(a.getConsecutiveCount());
        dto.setEscalationCount(a.getEscalationCount());
        dto.setRecoveryCount(a.getRecoveryCount());
        dto.setReopenCount(a.getReopenCount());
        dto.setSuppressedUntil(a.getSuppressedUntil());
        dto.setSuppressionReason(a.getSuppressionReason());
        dto.setSuppressedBy(a.getSuppressedBy());
        dto.setAcknowledgedBy(a.getAcknowledgedBy());
        dto.setAcknowledgedAt(a.getAcknowledgedAt());
        dto.setInvestigatedBy(a.getInvestigatedBy());
        dto.setInvestigatedAt(a.getInvestigatedAt());
        dto.setResolvedBy(a.getResolvedBy());
        dto.setResolvedAt(a.getResolvedAt());
        dto.setResolutionReason(a.getResolutionReason());
        dto.setRelatedInvestigationTargetKey(a.getRelatedInvestigationTargetKey());
        dto.setRelatedRemediationId(a.getRelatedRemediationId());
        dto.setRelatedExperimentId(a.getRelatedExperimentId());
        dto.setRelatedIssueTrackId(a.getRelatedIssueTrackId());
        dto.setCooldownUntilRunIndex(a.getCooldownUntilRunIndex());
        dto.setCurrentlySuppressed(a.isCurrentlySuppressed());

        // Allowed transitions
        List<String> allowed = new ArrayList<>();
        for (AlertLifecycleState st : AlertLifecycleState.values()) {
            if (st != a.getLifecycleState() && a.getLifecycleState().canTransitionTo(st)) {
                allowed.add(st.name());
            }
        }
        dto.setAllowedActions(allowed);

        return dto;
    }

    private int compareSeverity(String s1, String s2) {
        return scoreSeverity(s1) - scoreSeverity(s2);
    }

    private int scoreSeverity(String s) {
        if (s == null) return 0;
        return switch (s.toUpperCase()) {
            case "CRITICAL" -> 4;
            case "HIGH", "WARNING" -> 3;
            case "MEDIUM" -> 2;
            case "LOW", "INFO", "NOMINAL" -> 1;
            default -> 0;
        };
    }
}
