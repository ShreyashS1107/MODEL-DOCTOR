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
public class LeakageExplainabilityInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "LEAKAGE_EXPLAINABILITY_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Potential Target-Proxy Feature with Elevated Attribution";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.LEAKAGE, DiagnosticModule.EXPLAINABILITY);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        for (Map.Entry<String, NormalizedModuleData.FeatureLeakageData> entry : norm.getLeakageByFeature().entrySet()) {
            String feature = entry.getKey();
            NormalizedModuleData.FeatureLeakageData leak = entry.getValue();
            NormalizedModuleData.FeatureImportanceData imp = norm.getImportanceByFeature().get(feature);

            if (imp == null) continue;

            boolean hasLeakageSignal = leak.isSuspicious || leak.mutualInfo >= 0.25 || Math.abs(leak.correlation) >= 0.35 || leak.leakageScore >= 0.60;
            boolean hasHighAttribution = imp.rank <= 3 || imp.attributionShare >= 0.15 || imp.meanAbsShap >= 0.10;

            if (hasLeakageSignal && hasHighAttribution) {
                SeverityLevel severity = (leak.mutualInfo >= 0.40 || Math.abs(leak.correlation) >= 0.60) ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
                InvestigationPriority priority = InvestigationPriority.CRITICAL;
                EvidenceConfidence confidence = (leak.mutualInfo >= 0.30 && imp.rank <= 2) ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("mutualInfo", leak.mutualInfo);
                evidence.put("correlation", leak.correlation);
                evidence.put("leakageScore", leak.leakageScore);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);

                String summary = String.format("Feature '%s' shows suspicious target association (Mutual Info: %.4f, Corr: %.4f) while dominating model predictions (Importance Rank #%d).",
                        feature, leak.mutualInfo, leak.correlation, imp.rank);

                String whyItMatters = "Features that strongly mirror the target label can cause artificial inflation of test metrics while causing severe failure when deployed on genuine unlabelled data.";

                String investigationDirection = "1. Confirm whether '" + feature + "' is recorded before or after the event being predicted.\n2. Retrain model without '" + feature + "' to observe true baseline generalization.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "POTENTIAL_TARGET_PROXY",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Potential Target-Proxy Feature with Elevated Attribution: " + feature,
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
