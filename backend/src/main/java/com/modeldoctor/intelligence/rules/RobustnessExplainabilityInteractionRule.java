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
public class RobustnessExplainabilityInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "ROBUSTNESS_EXPLAINABILITY_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "High-Importance Feature with Elevated Sensitivity";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ROBUSTNESS, DiagnosticModule.EXPLAINABILITY);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureImportanceData> entry : norm.getImportanceByFeature().entrySet()) {
            String feature = entry.getKey();
            NormalizedModuleData.FeatureImportanceData imp = entry.getValue();
            NormalizedModuleData.FeatureRobustnessData rob = norm.getRobustnessByFeature().get(feature);

            if (rob == null) continue;

            boolean isHighImportance = imp.rank <= 3 || imp.attributionShare >= 0.15 || imp.meanAbsShap >= 0.10;
            boolean isHighlySensitive = rob.sensitivityRank <= 3 || rob.flipRate >= 0.02 || rob.meanProbabilityShift >= 0.03;

            if (isHighImportance && isHighlySensitive) {
                SeverityLevel severity = (imp.rank == 1 && rob.sensitivityRank == 1) ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
                InvestigationPriority priority = InvestigationPriority.MEDIUM;
                EvidenceConfidence confidence = (imp.rank <= 2 && rob.sensitivityRank <= 2) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);
                evidence.put("sensitivityRank", rob.sensitivityRank);
                evidence.put("flipRate", rob.flipRate);
                evidence.put("meanProbabilityShift", rob.meanProbabilityShift);

                String summary = String.format("Top-ranked feature '%s' (Importance Rank #%d) displays high sensitivity to input jitter (Sensitivity Rank #%d, Flip Rate: %.2f%%).",
                        feature, imp.rank, rob.sensitivityRank, rob.flipRate * 100.0);

                String whyItMatters = "When the model's most decisive feature is brittle to slight numerical perturbations, entire batches of borderline predictions can flip unexpectedly under field conditions.";

                String investigationDirection = "1. Test tree depth constraints or regularization on '" + feature + "'.\n2. Verify sensor / input quantization precision.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "FRAGILE_IMPORTANT_FEATURE",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "High-Importance Feature with Elevated Sensitivity: " + feature,
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
