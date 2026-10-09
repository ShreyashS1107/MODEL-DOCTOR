package com.modeldoctor.intelligence.monitoring;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.HealthDimensionEvaluationDto;
import com.modeldoctor.dto.HealthIndexBreakdownDto;
import com.modeldoctor.dto.HealthPenaltyDto;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ModelHealthDecisionEngine {

    public static class DecisionResult {
        public ModelHealthState overallState;
        public String decisionReason;
        public HealthIndexBreakdownDto indexBreakdown;

        public DecisionResult(ModelHealthState overallState, String decisionReason, HealthIndexBreakdownDto indexBreakdown) {
            this.overallState = overallState;
            this.decisionReason = decisionReason;
            this.indexBreakdown = indexBreakdown;
        }
    }

    public DecisionResult evaluateHealth(
            String modelLineageId,
            int baselineRunsCount,
            Map<HealthDimension, HealthDimensionEvaluationDto> dimensions,
            List<DiagnosticOperationalAlert> activeAlerts,
            DiagnosticMonitoringPolicy policy,
            DiagnosticHealthSnapshot previousSnapshot) {

        List<HealthPenaltyDto> penalties = new ArrayList<>();
        int totalPenalty = 0;

        // 1. Check Data Sufficiency against Monitoring Contract
        int minRuns = policy != null ? policy.getMinBaselineRunsRequired() : 3;
        if (baselineRunsCount < minRuns) {
            String reason = String.format("Insufficient operational history: Model lineage has %d baseline runs (minimum %d required by monitoring policy).",
                    baselineRunsCount, minRuns);
            penalties.add(new HealthPenaltyDto("DATA_SUFFICIENCY",
                    "Insufficient baseline runs count below policy threshold", 40,
                    String.format("Observed runs: %d / %d required", baselineRunsCount, minRuns)));
            HealthIndexBreakdownDto breakdown = new HealthIndexBreakdownDto(60, 40, penalties, false);
            return new DecisionResult(ModelHealthState.UNKNOWN, reason, breakdown);
        }

        // 2. Evaluate Dimension Penalties & States
        int criticalDims = 0;
        int degradedDims = 0;
        int warningDims = 0;
        int unknownDims = 0;

        for (Map.Entry<HealthDimension, HealthDimensionEvaluationDto> entry : dimensions.entrySet()) {
            HealthDimension dim = entry.getKey();
            HealthDimensionEvaluationDto eval = entry.getValue();

            switch (eval.getState()) {
                case CRITICAL -> {
                    criticalDims++;
                    int pts = 25;
                    totalPenalty += pts;
                    penalties.add(new HealthPenaltyDto("DIMENSION_CRITICAL",
                            String.format("Critical degradation detected in %s dimension", dim.name()),
                            pts, eval.getSummary()));
                }
                case DEGRADED -> {
                    degradedDims++;
                    int pts = 15;
                    totalPenalty += pts;
                    penalties.add(new HealthPenaltyDto("DIMENSION_DEGRADED",
                            String.format("Material degradation observed in %s dimension", dim.name()),
                            pts, eval.getSummary()));
                }
                case WARNING -> {
                    warningDims++;
                    int pts = 8;
                    totalPenalty += pts;
                    penalties.add(new HealthPenaltyDto("DIMENSION_WARNING",
                            String.format("Elevated anomaly threshold in %s dimension", dim.name()),
                            pts, eval.getSummary()));
                }
                case UNKNOWN -> {
                    unknownDims++;
                    int pts = 10;
                    totalPenalty += pts;
                    penalties.add(new HealthPenaltyDto("DIMENSION_UNKNOWN",
                            String.format("Missing required diagnostic evidence for %s dimension", dim.name()),
                            pts, eval.getReason() != null ? eval.getReason() : "No evidence"));
                }
                case HEALTHY -> {}
            }
        }

        // 3. Evaluate Active Operational Alert Penalties
        int activeCriticalAlerts = 0;
        int activeHighAlerts = 0;

        if (activeAlerts != null) {
            for (DiagnosticOperationalAlert alert : activeAlerts) {
                if (alert.isCurrentlySuppressed() || alert.getLifecycleState() == AlertLifecycleState.RESOLVED) {
                    continue;
                }

                String sev = alert.getCurrentSeverity() != null ? alert.getCurrentSeverity().toUpperCase() : "MEDIUM";
                switch (sev) {
                    case "CRITICAL" -> {
                        activeCriticalAlerts++;
                        int pts = 10;
                        totalPenalty += pts;
                        penalties.add(new HealthPenaltyDto("ACTIVE_CRITICAL_ALERT",
                                String.format("Active unsuppressed CRITICAL alert (%s): %s", alert.getAlertType(), alert.getTargetKey()),
                                pts, alert.getTriggerDescription()));
                    }
                    case "HIGH", "WARNING" -> {
                        activeHighAlerts++;
                        int pts = 5;
                        totalPenalty += pts;
                        penalties.add(new HealthPenaltyDto("ACTIVE_HIGH_ALERT",
                                String.format("Active HIGH priority alert (%s): %s", alert.getAlertType(), alert.getTargetKey()),
                                pts, alert.getTriggerDescription()));
                    }
                    case "MEDIUM" -> {
                        int pts = 2;
                        totalPenalty += pts;
                        penalties.add(new HealthPenaltyDto("ACTIVE_MEDIUM_ALERT",
                                String.format("Active MEDIUM priority alert (%s): %s", alert.getAlertType(), alert.getTargetKey()),
                                pts, alert.getTriggerDescription()));
                    }
                }
            }
        }

        int score = Math.max(0, Math.min(100, 100 - totalPenalty));
        HealthIndexBreakdownDto breakdown = new HealthIndexBreakdownDto(score, totalPenalty, penalties, true);

        // 4. Deterministic Overall Health State Rules
        // A. UNKNOWN: If critical dimensions have unknown state (e.g. required dimensions missing)
        if (unknownDims >= 3) {
            String reason = String.format("Overall model health is UNKNOWN: %d diagnostic dimensions lack required evidence.", unknownDims);
            return new DecisionResult(ModelHealthState.UNKNOWN, reason, breakdown);
        }

        // B. CRITICAL State
        if (activeCriticalAlerts > 0 || criticalDims > 0) {
            String reason = String.format("CRITICAL health state: %d active CRITICAL alerts, %d critical dimensions detected.",
                    activeCriticalAlerts, criticalDims);
            return new DecisionResult(ModelHealthState.CRITICAL, reason, breakdown);
        }

        // C. DEGRADED State
        if (activeHighAlerts > 0 || degradedDims > 0 || (warningDims >= 3)) {
            String reason = String.format("DEGRADED health state: %d active HIGH alerts, %d degraded dimensions, %d warning dimensions.",
                    activeHighAlerts, degradedDims, warningDims);
            return new DecisionResult(ModelHealthState.DEGRADED, reason, breakdown);
        }

        // D. RECOVERING State
        if (previousSnapshot != null &&
                (previousSnapshot.getOverallState() == ModelHealthState.CRITICAL || previousSnapshot.getOverallState() == ModelHealthState.DEGRADED)) {

            int reqRecovery = policy != null ? policy.getRecoveryConsecutiveRuns() : 2;
            String reason = String.format("RECOVERING health state: Previous operational degradation has cleared; stabilizing (%d clean baseline runs required).", reqRecovery);
            return new DecisionResult(ModelHealthState.RECOVERING, reason, breakdown);
        }

        // E. HEALTHY State
        String reason = String.format("HEALTHY operational state: All %d evaluated dimensions satisfy monitoring policies (Health Index: %d/100).",
                dimensions.size() - unknownDims, score);
        return new DecisionResult(ModelHealthState.HEALTHY, reason, breakdown);
    }
}
