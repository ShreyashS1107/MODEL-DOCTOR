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
public class ErrorBiasInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "ERROR_BIAS_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Subgroup Error Disparity with Fairness Degradation";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ERROR_FORENSICS, DiagnosticModule.BIAS);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.ErrorForensicsSummary errSum = norm.getErrorForensicsSummary();
        NormalizedModuleData.BiasSummary biasSum = norm.getBiasSummary();

        if (errSum == null || biasSum == null) return findings;

        boolean hasSubgroupErrorDisparity = errSum.worstSubgroupDisparityRatio >= 1.30;
        boolean hasFairnessDisparity = biasSum.worstDisparateImpactRatio < 0.80 || biasSum.demographicParityGap >= 0.10 || biasSum.equalOpportunityGap >= 0.08;

        if (hasSubgroupErrorDisparity && hasFairnessDisparity) {
            SeverityLevel severity = (errSum.worstSubgroupDisparityRatio >= 1.50 || biasSum.worstDisparateImpactRatio < 0.70)
                    ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
            InvestigationPriority priority = severity == SeverityLevel.CRITICAL ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH;
            EvidenceConfidence confidence = EvidenceConfidence.HIGH;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("protectedAttribute", biasSum.protectedAttribute);
            evidence.put("worstSubgroupDisparityRatio", errSum.worstSubgroupDisparityRatio);
            evidence.put("subgroupErrorRates", errSum.subgroupErrorRates);
            evidence.put("worstDisparateImpactRatio", biasSum.worstDisparateImpactRatio);
            evidence.put("demographicParityGap", biasSum.demographicParityGap);
            evidence.put("equalOpportunityGap", biasSum.equalOpportunityGap);

            String summary = String.format("Observed subgroup error rate disparity (Disparity Ratio: %.2fx) coincides with fairness metric degradation (Disparate Impact: %.4f, DP Gap: %.4f).",
                    errSum.worstSubgroupDisparityRatio, biasSum.worstDisparateImpactRatio, biasSum.demographicParityGap);

            String whyItMatters = "When error rates are systematically higher for specific demographic slices while algorithmic fairness metrics also show substantial divergence, predictive inequity is empirically corroborated across both error and bias evaluations.";

            String investigationDirection = "1. Cross-reference False Positive and False Negative rates across protected subgroups.\n" +
                    "2. Examine whether subgroup sample sizes are balanced or if specific groups suffer from underrepresented training data.\n" +
                    "3. Review threshold adjustments or fairness-aware calibration strategies in the BIAS console.";

            findings.add(RuleEvaluationHelper.createCorrelation(
                    norm,
                    getRuleId(),
                    biasSum.protectedAttribute != null && !biasSum.protectedAttribute.isBlank() ? biasSum.protectedAttribute : "GLOBAL",
                    "ERROR_BIAS_INTERACTION",
                    severity,
                    priority,
                    confidence,
                    biasSum.protectedAttribute,
                    "Subgroup Error Disparity & Fairness Disparity",
                    summary,
                    whyItMatters,
                    investigationDirection,
                    evidence,
                    getRequiredModules(),
                    objectMapper
            ));
        }

        return findings;
    }
}
