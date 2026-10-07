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
public class DriftRobustnessInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "DRIFT_ROBUSTNESS_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Drifted Feature with Elevated Robustness Sensitivity";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.DRIFT, DiagnosticModule.ROBUSTNESS);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureDriftData> entry : norm.getDriftByFeature().entrySet()) {
            String feature = entry.getKey();
            NormalizedModuleData.FeatureDriftData drift = entry.getValue();
            NormalizedModuleData.FeatureRobustnessData rob = norm.getRobustnessByFeature().get(feature);

            if (rob == null) continue;

            boolean hasDrift = drift.psi >= 0.08 || drift.driftDetected;
            boolean isSensitive = rob.sensitivityRank <= 3 || rob.flipRate >= 0.02 || rob.meanProbabilityShift >= 0.03;

            if (hasDrift && isSensitive) {
                SeverityLevel severity = (drift.psi >= 0.20 && rob.sensitivityRank <= 2) ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
                InvestigationPriority priority = InvestigationPriority.MEDIUM;
                EvidenceConfidence confidence = (drift.psi >= 0.10 && rob.flipRate >= 0.03) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("psi", drift.psi);
                evidence.put("sensitivityRank", rob.sensitivityRank);
                evidence.put("flipRate", rob.flipRate);
                evidence.put("meanProbabilityShift", rob.meanProbabilityShift);

                String summary = String.format("Feature '%s' has drifted (PSI: %.4f) and demonstrates high perturbation sensitivity (Rank #%d, Flip Rate: %.2f%%).",
                        feature, drift.psi, rob.sensitivityRank, rob.flipRate * 100.0);

                String whyItMatters = "Features that are sensitive to noise and also shifting in production represent an unstable operational surface prone to erratic model classifications.";

                String investigationDirection = "1. Test noise threshold boundaries on '" + feature + "'.\n2. Evaluate feature scaling or clipping strategies to improve model stability.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "DRIFT_ROBUSTNESS_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Drifted Feature with Elevated Robustness Sensitivity: " + feature,
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
