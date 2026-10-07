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
public class MultiDriftPerformanceRiskRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "MULTI_DRIFT_PERFORMANCE_RISK";
    }

    @Override
    public String getRuleName() {
        return "Critical Multi-Module: High-Impact Shift with Performance Deterioration";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.DRIFT, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.PERFORMANCE);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.PerformanceSummary perf = norm.getPerformanceSummary();
        if (perf == null) return findings;

        boolean hasPerfDegradation = perf.f1 < 0.60 || perf.expectedCalibrationError > 0.05 || perf.falseNegativeRate > 0.40 || !perf.passed;
        if (!hasPerfDegradation) return findings;

        for (Map.Entry<String, NormalizedModuleData.FeatureImportanceData> impEntry : norm.getImportanceByFeature().entrySet()) {
            String feature = impEntry.getKey();
            NormalizedModuleData.FeatureImportanceData imp = impEntry.getValue();
            NormalizedModuleData.FeatureDriftData drift = norm.getDriftByFeature().get(feature);

            if (drift == null) continue;

            boolean isKeyFeature = imp.rank <= 3 || imp.attributionShare >= 0.15;
            boolean hasDrift = drift.psi >= 0.08 || drift.driftDetected;

            if (isKeyFeature && hasDrift) {
                SeverityLevel severity = (drift.psi >= 0.20 || perf.f1 < 0.40) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = InvestigationPriority.CRITICAL;
                EvidenceConfidence confidence = EvidenceConfidence.HIGH;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);
                evidence.put("psi", drift.psi);
                evidence.put("ksPValue", drift.ksPValue);
                evidence.put("rocAuc", perf.rocAuc);
                evidence.put("f1Score", perf.f1);
                evidence.put("expectedCalibrationError", perf.expectedCalibrationError);

                String summary = String.format("CRITICAL: Top-influencing feature '%s' (Importance Rank #%d) has undergone substantial distribution drift (PSI: %.4f) concurrently with model performance degradation (F1: %.4f, ECE: %.4f).",
                        feature, imp.rank, drift.psi, perf.f1, perf.expectedCalibrationError);

                String whyItMatters = "Triangulated evidence across Drift, Explainability, and Performance establishes that the model's primary decision drivers are experiencing covariate shift while predictive precision is compromised.";

                String investigationDirection = "1. Immediate priority: Profile production data distributions for '" + feature + "' against training baselines.\n2. Verify whether model retraining or domain adaptation restores calibration.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "MULTI_MODULE_DRIFT_PERFORMANCE_CRITICAL",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Multi-Module Finding: Drifted Key Feature with Performance Drop: " + feature,
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
