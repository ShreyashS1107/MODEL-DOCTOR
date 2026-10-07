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
public class MultiFragileFeatureRiskRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "MULTI_FRAGILE_FEATURE_RISK";
    }

    @Override
    public String getRuleName() {
        return "Multi-Module: Fragile Influential Feature with Distribution Shift";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.EXPLAINABILITY, DiagnosticModule.ROBUSTNESS, DiagnosticModule.DRIFT);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureImportanceData> impEntry : norm.getImportanceByFeature().entrySet()) {
            String feature = impEntry.getKey();
            NormalizedModuleData.FeatureImportanceData imp = impEntry.getValue();
            NormalizedModuleData.FeatureRobustnessData rob = norm.getRobustnessByFeature().get(feature);
            NormalizedModuleData.FeatureDriftData drift = norm.getDriftByFeature().get(feature);

            if (rob == null || drift == null) continue;

            boolean isInfluential = imp.rank <= 3 || imp.attributionShare >= 0.15;
            boolean isSensitive = rob.sensitivityRank <= 3 || rob.flipRate >= 0.02;
            boolean hasDrift = drift.psi >= 0.08 || drift.driftDetected;

            if (isInfluential && isSensitive && hasDrift) {
                SeverityLevel severity = (imp.rank == 1 || drift.psi >= 0.20) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = InvestigationPriority.CRITICAL;
                EvidenceConfidence confidence = EvidenceConfidence.HIGH;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);
                evidence.put("sensitivityRank", rob.sensitivityRank);
                evidence.put("flipRate", rob.flipRate);
                evidence.put("psi", drift.psi);
                evidence.put("ksPValue", drift.ksPValue);

                String summary = String.format("CRITICAL: Influential feature '%s' (Importance Rank #%d) is simultaneously brittle to perturbations (Sensitivity Rank #%d, Flip Rate: %.2f%%) and shifting in distribution (PSI: %.4f).",
                        feature, imp.rank, rob.sensitivityRank, rob.flipRate * 100.0, drift.psi);

                String whyItMatters = "Features that are simultaneously influential, sensitive, and actively shifting represent the highest operational risk in a deployed ML pipeline.";

                String investigationDirection = "1. Immediate priority: Profile decision boundary margins for '" + feature + "'.\n2. Introduce robust loss functions or input noise regularization during retraining.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "MULTI_MODULE_FRAGILE_FEATURE_CRITICAL",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Critical Multi-Module Fragile Feature: " + feature,
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
