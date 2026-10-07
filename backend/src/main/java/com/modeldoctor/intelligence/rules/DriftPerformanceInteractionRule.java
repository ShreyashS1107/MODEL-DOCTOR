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
public class DriftPerformanceInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "DRIFT_PERFORMANCE_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Distribution Shift / Performance Interaction";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.DRIFT, DiagnosticModule.PERFORMANCE);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.PerformanceSummary perf = norm.getPerformanceSummary();
        if (perf == null) return findings;

        boolean hasPerformanceDegradation = perf.f1 < 0.60 || perf.expectedCalibrationError > 0.05
                || perf.falseNegativeRate > 0.40 || perf.healthScore < 85.0 || !perf.passed;

        if (!hasPerformanceDegradation) return findings;

        for (Map.Entry<String, NormalizedModuleData.FeatureDriftData> entry : norm.getDriftByFeature().entrySet()) {
            String feature = entry.getKey();
            NormalizedModuleData.FeatureDriftData drift = entry.getValue();

            if (drift.psi >= 0.08 || drift.driftDetected) {
                SeverityLevel severity = (drift.psi >= 0.25 || perf.f1 < 0.45) ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
                InvestigationPriority priority = InvestigationPriority.HIGH;
                EvidenceConfidence confidence = (drift.psi >= 0.15 && perf.expectedCalibrationError > 0.05) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("psi", drift.psi);
                evidence.put("ksPValue", drift.ksPValue);
                evidence.put("rocAuc", perf.rocAuc);
                evidence.put("f1Score", perf.f1);
                evidence.put("expectedCalibrationError", perf.expectedCalibrationError);
                evidence.put("falseNegativeRate", perf.falseNegativeRate);

                String summary = String.format("Distribution shift in '%s' (PSI: %.4f) coincides with degraded performance metrics (F1: %.4f, ECE: %.4f, FNR: %.4f).",
                        feature, drift.psi, perf.f1, perf.expectedCalibrationError, perf.falseNegativeRate);

                String whyItMatters = "Population covariate shift frequently deteriorates model decision boundaries and predictive calibration on drifted segments.";

                String investigationDirection = "1. Evaluate model confusion matrix specifically on high vs low values of '" + feature + "'.\n2. Assess if retraining with recent baseline data mitigates calibration error.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "DISTRIBUTION_PERFORMANCE_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Potential Distribution-Shift / Performance Interaction: " + feature,
                        summary,
                        whyItMatters,
                        investigationDirection,
                        evidence,
                        getRequiredModules(),
                        objectMapper
                ));
            }
        }

        return findings;
    }
}
