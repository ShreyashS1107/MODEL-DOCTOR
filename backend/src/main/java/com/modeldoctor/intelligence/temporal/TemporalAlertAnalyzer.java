package com.modeldoctor.intelligence.temporal;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticRun;
import com.modeldoctor.dto.ChangePointDto;
import com.modeldoctor.dto.IssueTrackDto;
import com.modeldoctor.dto.RemediationDurabilityDto;
import com.modeldoctor.dto.TemporalAlertDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

@Component
public class TemporalAlertAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(TemporalAlertAnalyzer.class);
    private final ObjectMapper objectMapper;

    public TemporalAlertAnalyzer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TemporalAlertDto> generateAlerts(
            String modelLineageId,
            List<DiagnosticRun> runs,
            List<IssueTrackDto> issueTracks,
            List<ChangePointDto> changePoints,
            List<RemediationDurabilityDto> durabilityList) {

        List<TemporalAlertDto> alerts = new ArrayList<>();
        if (runs == null || runs.isEmpty()) {
            return alerts;
        }

        DiagnosticRun latestRun = runs.get(runs.size() - 1);
        String latestRunId = latestRun.getId();

        // 1. Issue Track Alerts
        for (IssueTrackDto track : issueTracks) {
            String status = track.getStatus();
            String currentSev = track.getCurrentSeverity();

            if ("PERSISTENT".equalsIgnoreCase(status)) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("PERSISTENT_DEGRADATION");
                alert.setPriority("CRITICAL".equalsIgnoreCase(currentSev) ? "CRITICAL" : "HIGH");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName(track.getMetricNames().isEmpty() ? "composite" : track.getMetricNames().get(0));
                alert.setTriggerDescription("Target '" + track.getTargetKey() + "' exhibits persistent elevated severity (" +
                        currentSev + ") across " + track.getConsecutiveCount() + " consecutive diagnostic runs.");
                alert.setConfidence("VERY_HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            } else if ("EMERGING".equalsIgnoreCase(status) && ("CRITICAL".equalsIgnoreCase(currentSev) || "HIGH".equalsIgnoreCase(currentSev))) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("NEW_DEGRADATION");
                alert.setPriority("CRITICAL".equalsIgnoreCase(currentSev) ? "CRITICAL" : "HIGH");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName(track.getMetricNames().isEmpty() ? "composite" : track.getMetricNames().get(0));
                alert.setTriggerDescription("Newly emerging degradation detected on target '" + track.getTargetKey() +
                        "' with severity " + currentSev + " in recent run.");
                alert.setConfidence("HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            } else if ("ESCALATING".equalsIgnoreCase(status)) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("ESCALATING_DEGRADATION");
                alert.setPriority("CRITICAL");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName(track.getMetricNames().isEmpty() ? "composite" : track.getMetricNames().get(0));
                alert.setTriggerDescription("Escalating degradation trajectory observed on target '" + track.getTargetKey() +
                        "', increasing to peak severity " + track.getPeakSeverity() + ".");
                alert.setConfidence("VERY_HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            } else if ("RECURRING".equalsIgnoreCase(status)) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("RECURRING_ISSUE");
                alert.setPriority("HIGH");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName(track.getMetricNames().isEmpty() ? "composite" : track.getMetricNames().get(0));
                alert.setTriggerDescription("Recurring issue detected on target '" + track.getTargetKey() +
                        "', which re-emerged after prior periods of low severity.");
                alert.setConfidence("HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            } else if ("RECOVERED".equalsIgnoreCase(status)) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("RECOVERY");
                alert.setPriority("LOW");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName(track.getMetricNames().isEmpty() ? "composite" : track.getMetricNames().get(0));
                alert.setTriggerDescription("Target '" + track.getTargetKey() + "' has recovered below warning thresholds for " +
                        track.getConsecutiveCount() + " consecutive run(s).");
                alert.setConfidence("HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            }

            // Multi-module escalation check
            if (track.getModulesInvolved().size() >= 3 && ("HIGH".equalsIgnoreCase(currentSev) || "CRITICAL".equalsIgnoreCase(currentSev))) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("MULTI_MODULE_ESCALATION");
                alert.setPriority("CRITICAL");
                alert.setTargetType(track.getTargetType());
                alert.setTargetKey(track.getTargetKey());
                alert.setMetricName("cross_module");
                alert.setTriggerDescription("Multi-module escalation on target '" + track.getTargetKey() +
                        "' spanning " + track.getModulesInvolved().size() + " independent diagnostic modules: " + track.getModulesInvolved());
                alert.setConfidence("VERY_HIGH");
                alert.setRunIds(track.getRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            }
        }

        // 2. Change Point Alerts
        for (ChangePointDto cp : changePoints) {
            TemporalAlertDto alert = new TemporalAlertDto();
            alert.setModelLineageId(modelLineageId);
            alert.setRunId(cp.getChangeRunId() != null ? cp.getChangeRunId() : latestRunId);
            alert.setAlertType("CHANGE_POINT_DETECTED");
            alert.setPriority("HIGH");
            alert.setTargetType("FEATURE");
            alert.setTargetKey(cp.getTargetKey());
            alert.setMetricName(cp.getMetricName());
            alert.setCurrentValue(cp.getAfterMean());
            alert.setReferenceValue(cp.getBeforeMean());
            alert.setTriggerDescription("Statistical change point detected for " + cp.getMetricName() + " on " + cp.getTargetKey() +
                    ": mean shifted by " + String.format("%+.4f", cp.getAbsoluteShift()) + " (from " + cp.getBeforeMean() + " to " + cp.getAfterMean() + ").");
            alert.setConfidence(cp.getConfidenceLevel());
            alert.getRunIds().addAll(cp.getRunIdsBefore());
            alert.getRunIds().addAll(cp.getRunIdsAfter());
            alert.setCreatedAt(cp.getChangeTimestamp() != null ? cp.getChangeTimestamp() : Instant.now());
            alerts.add(alert);
        }

        // 3. Remediation Durability Alerts
        for (RemediationDurabilityDto dur : durabilityList) {
            if ("FAILED_TO_SUSTAIN".equalsIgnoreCase(dur.getDurabilityStatus())) {
                TemporalAlertDto alert = new TemporalAlertDto();
                alert.setModelLineageId(modelLineageId);
                alert.setRunId(latestRunId);
                alert.setAlertType("REMEDIATION_NOT_SUSTAINED");
                alert.setPriority("HIGH");
                alert.setTargetType("FEATURE");
                alert.setTargetKey(dur.getTargetKey());
                alert.setMetricName(dur.getMetricName());
                alert.setTriggerDescription("Remediation intervention on " + dur.getTargetKey() +
                        " (Experiment " + dur.getExperimentId() + ") was not sustained in subsequent baseline monitoring runs.");
                alert.setConfidence("HIGH");
                alert.setRunIds(dur.getFollowUpRunIds());
                alert.setCreatedAt(Instant.now());
                alerts.add(alert);
            }
        }

        // Sort alerts by priority (CRITICAL -> HIGH -> MEDIUM -> LOW) then createdAt DESC
        alerts.sort((a, b) -> {
            int pA = priorityRank(a.getPriority());
            int pB = priorityRank(b.getPriority());
            if (pA != pB) return Integer.compare(pB, pA);
            return b.getCreatedAt().compareTo(a.getCreatedAt());
        });

        return alerts;
    }

    private int priorityRank(String priority) {
        if (priority == null) return 0;
        return switch (priority.toUpperCase()) {
            case "CRITICAL" -> 3;
            case "HIGH" -> 2;
            case "MEDIUM" -> 1;
            default -> 0; // LOW
        };
    }
}
