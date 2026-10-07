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
public class DriftExplainabilityInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "DRIFT_EXPLAINABILITY_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "High-Impact Feature Distribution Shift";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.DRIFT, DiagnosticModule.EXPLAINABILITY);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureDriftData> driftEntry : norm.getDriftByFeature().entrySet()) {
            String feature = driftEntry.getKey();
            NormalizedModuleData.FeatureDriftData drift = driftEntry.getValue();
            NormalizedModuleData.FeatureImportanceData imp = norm.getImportanceByFeature().get(feature);

            if (imp == null) continue;

            boolean hasSignificantDrift = drift.psi >= 0.10 || drift.driftDetected;
            boolean isHighImportance = imp.rank <= 3 || imp.meanAbsShap >= 0.10 || imp.attributionShare >= 0.15;

            if (hasSignificantDrift && isHighImportance) {
                SeverityLevel severity = drift.psi >= 0.25 ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
                InvestigationPriority priority = InvestigationPriority.HIGH;
                EvidenceConfidence confidence = (drift.psi >= 0.10 && imp.rank <= 3) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("psi", drift.psi);
                evidence.put("ksPValue", drift.ksPValue);
                evidence.put("driftSeverity", drift.severity);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);
                evidence.put("attributionShare", imp.attributionShare);

                String summary = String.format("Feature '%s' (Importance Rank #%d, Mean |SHAP| %.4f) exhibits significant distribution drift (PSI: %.4f, KS p-val: %.4f).",
                        feature, imp.rank, imp.meanAbsShap, drift.psi, drift.ksPValue);

                String whyItMatters = "Features with high model attribution drive a large fraction of individual inference predictions. When their underlying distribution changes, model output stability and generalization accuracy may degrade.";

                String investigationDirection = "1. Inspect whether production data collection for '" + feature + "' has changed.\n2. Compare feature distributions across evaluation and baseline slices.\n3. Quantify performance impact on affected prediction subgroups.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "DISTRIBUTION_SHIFT_HIGH_IMPACT",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Distribution Drift in High-Impact Feature: " + feature,
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
