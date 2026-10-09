package com.modeldoctor.intelligence.incident;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.*;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Deterministic synthesis engine that combines multi-module evidence, detects contradictory
 * signals, calculates transparent priority breakdowns, and produces non-causal decision recommendations.
 */
@Component
public class EvidenceSynthesisEngine {

    /**
     * Counts independent diagnostic modules across alerts and findings.
     * Multiple metrics within the same module (e.g. Drift PSI + Drift KS) count as 1 independent module.
     */
    public int countIndependentModules(List<DiagnosticOperationalAlert> alerts) {
        if (alerts == null || alerts.isEmpty()) return 0;
        Set<String> modules = new HashSet<>();
        for (DiagnosticOperationalAlert a : alerts) {
            String m = normalizeModuleName(a.getSourceModule());
            if (m != null && !m.isBlank()) {
                modules.add(m);
            }
        }
        return Math.max(1, modules.size());
    }

    /**
     * Computes deterministic priority score (0-100) and full transparent breakdown.
     */
    public IncidentPriorityBreakdownDto calculatePriorityBreakdown(
            String highestSeverity,
            int independentModulesCount,
            int maxPersistenceRuns,
            boolean isEscalating,
            ModelHealthState healthState) {

        int severityContrib = switch (highestSeverity != null ? highestSeverity.toUpperCase() : "MEDIUM") {
            case "CRITICAL" -> 40;
            case "HIGH" -> 25;
            case "MEDIUM", "WARNING" -> 15;
            default -> 5;
        };

        // +10 per independent module (capped at 40)
        int independentContrib = Math.min(40, independentModulesCount * 10);

        // Persistence & Escalation contribution (max 15)
        int persistenceContrib = 0;
        if (isEscalating) persistenceContrib += 10;
        if (maxPersistenceRuns >= 2) persistenceContrib += 5;
        persistenceContrib = Math.min(15, persistenceContrib);

        // Overall Model Health Impact contribution (max 10)
        int healthContrib = 0;
        if (healthState == ModelHealthState.CRITICAL) healthContrib = 10;
        else if (healthState == ModelHealthState.DEGRADED) healthContrib = 5;

        int total = Math.min(100, severityContrib + independentContrib + persistenceContrib + healthContrib);

        String tier = "INFO";
        if (total >= 85) tier = "CRITICAL";
        else if (total >= 65) tier = "HIGH";
        else if (total >= 45) tier = "MEDIUM";
        else if (total >= 25) tier = "LOW";

        IncidentPriorityBreakdownDto breakdown = new IncidentPriorityBreakdownDto(
                severityContrib, independentContrib, persistenceContrib, healthContrib, total, tier);

        List<String> items = new ArrayList<>();
        items.add(String.format("Severity Contribution (%s): +%d pts", highestSeverity, severityContrib));
        items.add(String.format("Independent Evidence Modules (%d): +%d pts", independentModulesCount, independentContrib));
        items.add(String.format("Persistence & Escalation (runs=%d, escalating=%s): +%d pts", maxPersistenceRuns, isEscalating, persistenceContrib));
        items.add(String.format("Model Health Impact (%s): +%d pts", healthState, healthContrib));
        breakdown.setExplanationItems(items);

        return breakdown;
    }

    /**
     * Detects contradictory or conflicting evidence patterns.
     * e.g., severe drift without downstream performance impact or stable temporal accuracy.
     */
    public ContradictoryEvidenceDto evaluateContradictoryEvidence(
            List<DiagnosticOperationalAlert> alerts,
            Map<HealthDimension, HealthDimensionEvaluationDto> healthVector,
            boolean hasPerformanceDegradation) {

        boolean hasDrift = alerts.stream().anyMatch(a -> "DRIFT".equalsIgnoreCase(a.getSourceModule()));
        boolean performanceHealthy = healthVector != null && healthVector.containsKey(HealthDimension.PERFORMANCE)
                && (healthVector.get(HealthDimension.PERFORMANCE).getState() == DimensionHealthState.HEALTHY);

        List<String> conflicts = new ArrayList<>();

        if (hasDrift && performanceHealthy && !hasPerformanceDegradation) {
            conflicts.add("Drift degradation is present on input feature(s), but downstream operational model performance remains HEALTHY.");
        }

        boolean hasErrorConcentration = alerts.stream().anyMatch(a -> "ERROR_FORENSICS".equalsIgnoreCase(a.getSourceModule()));
        boolean dataQualityHealthy = healthVector != null && healthVector.containsKey(HealthDimension.DATA_QUALITY)
                && (healthVector.get(HealthDimension.DATA_QUALITY).getState() == DimensionHealthState.HEALTHY);

        if (hasErrorConcentration && dataQualityHealthy && !hasDrift) {
            conflicts.add("High error concentration detected without corresponding input data quality or distribution drift anomalies.");
        }

        boolean hasConflict = !conflicts.isEmpty();
        String summary = hasConflict
                ? "Contradictory diagnostic signals detected across independent evaluation dimensions."
                : "All observed diagnostic signals are mutually consistent.";
        String confidenceImpact = hasConflict ? "Lowers decision confidence from HIGH to MEDIUM due to conflicting downstream impact." : "NOMINAL";
        String recommendedAction = hasConflict ? "MONITOR" : "PROCEED_WITH_RECOMMENDATION";

        return new ContradictoryEvidenceDto(hasConflict, summary, conflicts, confidenceImpact, recommendedAction);
    }

    /**
     * Synthesizes deterministic operator decision recommendation and confidence level.
     */
    public IncidentDecisionDto determineDecision(
            DiagnosticIncident incident,
            boolean hasInvestigationTarget,
            boolean hasRemediationProposal,
            boolean hasValidatedExperiment,
            boolean hasContradictoryEvidence,
            int independentModuleCount,
            boolean isPreviouslyResolvedAndReopened) {

        IncidentDecisionState recommendation;
        DecisionConfidence confidence;
        String rationale;
        String nextAction;

        if (hasContradictoryEvidence && !hasRemediationProposal && !hasValidatedExperiment) {
            recommendation = IncidentDecisionState.MONITOR;
            confidence = DecisionConfidence.MEDIUM;
            rationale = "Contradictory diagnostic signals detected across evaluation dimensions (e.g. drift present without downstream performance degradation).";
            nextAction = "Monitor subsequent operational baseline runs to observe whether downstream performance degrades.";
        } else if (isPreviouslyResolvedAndReopened) {
            recommendation = IncidentDecisionState.REOPEN_INVESTIGATION;
            confidence = DecisionConfidence.HIGH;
            rationale = "Previously resolved incident condition has recurred in active operational baseline monitoring.";
            nextAction = "Reopen root-cause investigation target and inspect regression durability logs.";
        } else if (incident.getPriorityScore() >= 85 && independentModuleCount >= 3 && !hasRemediationProposal) {
            recommendation = IncidentDecisionState.ESCALATE;
            confidence = DecisionConfidence.HIGH;
            rationale = "Critical multi-dimensional degradation detected across " + independentModuleCount + " independent modules without an approved remediation.";
            nextAction = "Escalate to on-call MLOps engineer for immediate root-cause investigation.";
        } else if (hasValidatedExperiment) {
            recommendation = IncidentDecisionState.MONITOR;
            confidence = DecisionConfidence.HIGH;
            rationale = "Candidate remediation has been statistically validated via Phase 8 experiment; observing operational baseline durability.";
            nextAction = "Monitor operational baseline runs to verify sustained post-remediation stabilization.";
        } else if (hasRemediationProposal) {
            recommendation = IncidentDecisionState.VALIDATE_REMEDIATION;
            confidence = hasContradictoryEvidence ? DecisionConfidence.MEDIUM : DecisionConfidence.HIGH;
            rationale = "Remediation hypothesis is proposed for primary target; requires counterfactual experimental validation.";
            nextAction = "Launch Phase 8 experimental validation run to verify expected metric improvements and regression guards.";
        } else if (hasInvestigationTarget) {
            recommendation = IncidentDecisionState.REVIEW_REMEDIATION;
            confidence = hasContradictoryEvidence ? DecisionConfidence.MEDIUM : DecisionConfidence.HIGH;
            rationale = "Investigation target and evidence graph are established for " + incident.getPrimaryTarget() + "; review generated remediation hypotheses.";
            nextAction = "Review Phase 7 remediation candidates and select appropriate intervention strategy.";
        } else if (incident.getRelatedAlertsCount() > 0) {
            recommendation = IncidentDecisionState.INVESTIGATE;
            confidence = independentModuleCount >= 2 ? DecisionConfidence.HIGH : DecisionConfidence.MEDIUM;
            rationale = "Active operational alerts require structured investigation to establish cross-module evidence relationships.";
            nextAction = "Formulate Phase 6 root-cause investigation target for " + incident.getPrimaryTarget() + ".";
        } else {
            recommendation = IncidentDecisionState.NO_ACTION;
            confidence = DecisionConfidence.HIGH;
            rationale = "All diagnostic indicators nominal. No active operational intervention required.";
            nextAction = "Continue continuous baseline monitoring.";
        }

        if (hasContradictoryEvidence && confidence == DecisionConfidence.HIGH) {
            confidence = DecisionConfidence.MEDIUM;
        }

        return new IncidentDecisionDto(recommendation, confidence, rationale, nextAction);
    }

    /**
     * Builds structured Evidence Matrix rows from alerts and telemetry.
     */
    public List<EvidenceMatrixRowDto> buildEvidenceMatrix(List<DiagnosticOperationalAlert> alerts) {
        List<EvidenceMatrixRowDto> rows = new ArrayList<>();
        if (alerts == null) return rows;

        for (DiagnosticOperationalAlert a : alerts) {
            String valStr = a.getCurrentValue() != null ? String.format("%.4f", a.getCurrentValue()) : "ELEVATED";
            String refStr = a.getReferenceValue() != null ? String.format("%.4f", a.getReferenceValue()) : "NOMINAL";

            rows.add(new EvidenceMatrixRowDto(
                    a.getAlertType(),
                    a.getSourceModule() != null ? a.getSourceModule() : "ORCHESTRATOR",
                    a.getMetricName() != null ? a.getMetricName() : a.getAlertType(),
                    valStr,
                    refStr,
                    a.getCurrentSeverity(),
                    a.getLastSeenRunId(),
                    a.getId(),
                    a.getConfidence() != null ? a.getConfidence() : "HIGH",
                    "ASSOCIATIVE_CORRELATION",
                    a.getTriggerDescription()
            ));
        }

        return rows;
    }

    /**
     * Generates a transparent, non-causal technical evidence summary.
     */
    public String generateEvidenceSummary(
            String primaryTarget,
            IncidentCategory category,
            int independentModules,
            List<DiagnosticOperationalAlert> alerts) {

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Target '%s' exhibits %s supported by %d independent diagnostic module(s).\n",
                primaryTarget != null ? primaryTarget : "GLOBAL",
                category != null ? category.name() : "MULTI_MODULE_DEGRADATION",
                independentModules));

        sb.append("Empirical Evidence:\n");
        if (alerts != null) {
            for (DiagnosticOperationalAlert a : alerts) {
                sb.append(String.format(" - %s [%s]: %s\n",
                        a.getSourceModule(),
                        a.getCurrentSeverity(),
                        a.getTriggerDescription()));
            }
        }

        sb.append("Interpretation: Evidence represents empirical statistical co-occurrence and does not claim proven causality.");
        return sb.toString();
    }

    private String normalizeModuleName(String mod) {
        if (mod == null) return null;
        String m = mod.trim().toUpperCase();
        return switch (m) {
            case "ERROR", "ERROR_FORENSICS" -> "ERROR_FORENSICS";
            case "BIAS", "FAIRNESS" -> "FAIRNESS";
            default -> m;
        };
    }
}
