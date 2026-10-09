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
@SuppressWarnings("null")
public class MultiLeakageProxyRiskRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "MULTI_LEAKAGE_PROXY_RISK";
    }

    @Override
    public String getRuleName() {
        return "Critical Multi-Module: Target Leakage and Dominant Attribution";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.LEAKAGE, DiagnosticModule.EXPLAINABILITY, DiagnosticModule.DATA_QUALITY);
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
            NormalizedModuleData.FeatureQualityData qual = norm.getQualityByFeature().get(feature);

            if (imp == null) continue;

            boolean hasLeakageSignal = leak.isSuspicious || leak.mutualInfo >= 0.25 || Math.abs(leak.correlation) >= 0.35;
            boolean hasHighAttribution = imp.rank <= 3 || imp.attributionShare >= 0.15;
            boolean hasQualityContext = qual != null && (qual.distinctCount <= 5 || qual.distinctCount > 900 || qual.isConstant || qual.nullRate < 0.001);

            if (hasLeakageSignal && hasHighAttribution && hasQualityContext) {
                SeverityLevel severity = SeverityLevel.CRITICAL;
                InvestigationPriority priority = InvestigationPriority.CRITICAL;
                EvidenceConfidence confidence = EvidenceConfidence.HIGH;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("mutualInfo", leak.mutualInfo);
                evidence.put("correlation", leak.correlation);
                evidence.put("leakageScore", leak.leakageScore);
                evidence.put("importanceRank", imp.rank);
                evidence.put("meanAbsShap", imp.meanAbsShap);
                evidence.put("distinctCount", qual.distinctCount);
                evidence.put("isConstant", qual.isConstant);

                String summary = String.format("CRITICAL: Feature '%s' exhibits multiple triangulated indicators of target leakage/proxy behavior: Mutual Info: %.4f, Corr: %.4f, dominating model attribution (Rank #%d).",
                        feature, leak.mutualInfo, leak.correlation, imp.rank);

                String whyItMatters = "Target leakage produces deceptive training results; features that encode future label information or unmasked proxy identifiers will fail completely in real production deployment.";

                String investigationDirection = "1. Immediate action: Verify timestamp and lineage of '" + feature + "'.\n2. Strip feature from training pipeline and re-evaluate baseline model metrics.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "MULTI_MODULE_LEAKAGE_CRITICAL",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Critical Multi-Module Leakage Risk: " + feature,
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
