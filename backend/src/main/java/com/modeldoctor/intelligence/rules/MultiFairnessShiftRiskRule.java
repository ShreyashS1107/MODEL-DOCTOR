package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.EvidenceConfidence;
import com.modeldoctor.domain.InvestigationPriority;
import com.modeldoctor.domain.SeverityLevel;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class MultiFairnessShiftRiskRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "MULTI_FAIRNESS_SHIFT_RISK";
    }

    @Override
    public String getRuleName() {
        return "Multi-Module: Demographic Disparity with Distribution Shift and Degradation";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.BIAS, DiagnosticModule.PERFORMANCE, DiagnosticModule.DRIFT);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.BiasSummary bias = norm.getBiasSummary();
        NormalizedModuleData.PerformanceSummary perf = norm.getPerformanceSummary();
        NormalizedModuleData.DriftSummary drift = norm.getDriftSummary();

        if (bias == null || perf == null || drift == null) return findings;

        boolean hasFairnessDisparity = bias.worstDisparateImpactRatio < 0.85 || bias.demographicParityGap > 0.05;
        boolean hasPerfIssues = perf.f1 < 0.65 || perf.expectedCalibrationError > 0.05;
        boolean hasDriftSignals = drift.maxPsi >= 0.08 || drift.driftedFeatureCount >= 1;

        if (hasFairnessDisparity && hasPerfIssues && hasDriftSignals) {
            SeverityLevel severity = (bias.worstDisparateImpactRatio < 0.80) ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
            InvestigationPriority priority = InvestigationPriority.HIGH;
            EvidenceConfidence confidence = EvidenceConfidence.HIGH;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("protectedAttribute", bias.protectedAttribute);
            evidence.put("worstDisparateImpactRatio", bias.worstDisparateImpactRatio);
            evidence.put("demographicParityGap", bias.demographicParityGap);
            evidence.put("overallF1Score", perf.f1);
            evidence.put("maxDatasetPsi", drift.maxPsi);
            evidence.put("driftedFeatureCount", drift.driftedFeatureCount);

            String summary = String.format("HIGH PRIORITY: Subgroup fairness disparity (Disparate Impact: %.4f) coincides with overall performance issues (F1: %.4f) and measurable dataset drift (Max PSI: %.4f across %d feature(s)).",
                    bias.worstDisparateImpactRatio, perf.f1, drift.maxPsi, drift.driftedFeatureCount);

            String whyItMatters = "When production data distributions shift while subgroup fairness metrics are unbalanced, model bias typically accelerates, harming protected demographic slices disproportionately.";

            String investigationDirection = "1. Cross-tabulate drift statistics across individual protected slices of '" + bias.protectedAttribute + "'.\n2. Perform subgroup fairness recalibration.";

            findings.add(RuleEvaluationHelper.createCorrelation(
                    norm,
                    getRuleId(),
                    bias.protectedAttribute,
                    "MULTI_MODULE_FAIRNESS_DRIFT_RISK",
                    severity,
                    priority,
                    confidence,
                    bias.protectedAttribute,
                    "Multi-Module Fairness & Distribution Shift Risk: " + bias.protectedAttribute,
                    summary,
                    whyItMatters,
                    investigationDirection,
                    evidence,
                    getRequiredModules(),
                    objectMapper
            ));
        }

        return findings;
    }
}
