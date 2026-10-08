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
public class ErrorRobustnessInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "ERROR_ROBUSTNESS_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Error-Associated Feature with Adversarial Sensitivity";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ERROR_FORENSICS, DiagnosticModule.ROBUSTNESS);
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
            NormalizedModuleData.FeatureRobustnessData robData = norm.getRobustnessByFeature().get(feature);

            if (robData == null) continue;

            boolean hasHighSensitivity = robData.flipRate >= 0.15 || robData.sensitivityRank <= 3;
            boolean hasErrorAssoc = errData.absoluteAssociation >= 0.15 || errData.isErrorEnriched || errData.fpSeparation >= 0.30 || errData.fnSeparation >= 0.30;

            if (hasHighSensitivity && hasErrorAssoc) {
                SeverityLevel severity = (robData.flipRate >= 0.25 && errData.absoluteAssociation >= 0.25) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = severity == SeverityLevel.CRITICAL ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH;
                EvidenceConfidence confidence = (robData.flipRate >= 0.20 && errData.absoluteAssociation >= 0.20) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("flipRate", robData.flipRate);
                evidence.put("sensitivityRank", robData.sensitivityRank);
                evidence.put("meanProbabilityShift", robData.meanProbabilityShift);
                evidence.put("errorAssociation", errData.correlation);
                evidence.put("absoluteAssociation", errData.absoluteAssociation);
                evidence.put("adjustedPValue", errData.adjustedPValue);

                String summary = String.format("Feature '%s' exhibits elevated prediction error correlation (r = %.4f) alongside high perturbation sensitivity (Flip Rate: %.1f%%, Sensitivity Rank #%d).",
                        feature, errData.correlation, robData.flipRate * 100.0, robData.sensitivityRank);

                String whyItMatters = "Features that are sensitive to small perturbations and concurrently associated with classification errors indicate fragile decision boundaries that may fail unpredictably on borderline inputs.";

                String investigationDirection = "1. Evaluate boundary margin distances for error records.\n" +
                        "2. Perform targeted jitter stress tests on '" + feature + "' near the classification threshold.\n" +
                        "3. Consider regularization or feature scaling to stabilize model sensitivities.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "ERROR_ROBUSTNESS_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Error Association & Robustness Fragility: " + feature,
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
