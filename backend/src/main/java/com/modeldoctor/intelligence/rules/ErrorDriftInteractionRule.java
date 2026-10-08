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
public class ErrorDriftInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "ERROR_DRIFT_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Prediction Error Association with Distribution Shift";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ERROR_FORENSICS, DiagnosticModule.DRIFT);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureErrorData> errEntry : norm.getErrorByFeature().entrySet()) {
            String feature = errEntry.getKey();
            NormalizedModuleData.FeatureErrorData errData = errEntry.getValue();
            NormalizedModuleData.FeatureDriftData driftData = norm.getDriftByFeature().get(feature);

            if (driftData == null) continue;

            boolean hasSignificantDrift = driftData.psi >= 0.10 || driftData.driftDetected;
            boolean hasErrorAssoc = errData.absoluteAssociation >= 0.15 || errData.isErrorEnriched || errData.fpSeparation >= 0.30 || errData.fnSeparation >= 0.30;

            if (hasSignificantDrift && hasErrorAssoc) {
                SeverityLevel severity = (driftData.psi >= 0.25 && errData.absoluteAssociation >= 0.25) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = severity == SeverityLevel.CRITICAL ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH;
                EvidenceConfidence confidence = (driftData.psi >= 0.15 && errData.absoluteAssociation >= 0.20) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("errorAssociation", errData.correlation);
                evidence.put("absoluteAssociation", errData.absoluteAssociation);
                evidence.put("adjustedPValue", errData.adjustedPValue);
                evidence.put("fpSeparation", errData.fpSeparation);
                evidence.put("fnSeparation", errData.fnSeparation);
                evidence.put("psi", driftData.psi);
                evidence.put("ksPValue", driftData.ksPValue);
                evidence.put("driftSeverity", driftData.severity);

                String summary = String.format("Feature '%s' exhibits significant prediction error association (r = %.4f) concurrent with measurable distribution drift (PSI: %.4f, KS p-val: %.4f).",
                        feature, errData.correlation, driftData.psi, driftData.ksPValue);

                String whyItMatters = "When a feature is both statistically shifted relative to baseline and correlated with inference errors, the distribution shift may be contributing to localized model failure modes.";

                String investigationDirection = "1. Profile production distributions for '" + feature + "' against training baselines.\n" +
                        "2. Inspect feature error ranges to verify whether errors are concentrated in shifted value intervals.\n" +
                        "3. Evaluate model recalibration or domain adaptation.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "ERROR_DRIFT_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Error Association & Distribution Drift: " + feature,
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
