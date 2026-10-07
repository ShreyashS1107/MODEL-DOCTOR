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
public class BiasPerformanceInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "BIAS_PERFORMANCE_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Subgroup Fairness Disparity & Performance Degradation";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.BIAS, DiagnosticModule.PERFORMANCE);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.BiasSummary bias = norm.getBiasSummary();
        NormalizedModuleData.PerformanceSummary perf = norm.getPerformanceSummary();
        if (bias == null || perf == null) return findings;

        boolean hasFairnessDisparity = bias.worstDisparateImpactRatio < 0.85 || bias.demographicParityGap > 0.05
                || bias.equalOpportunityGap > 0.04 || !bias.passed;
        boolean hasPerformanceIssues = perf.f1 < 0.65 || perf.expectedCalibrationError > 0.05 || !perf.passed;

        if (hasFairnessDisparity && hasPerformanceIssues) {
            SeverityLevel severity = (bias.worstDisparateImpactRatio < 0.80 || bias.demographicParityGap > 0.10)
                    ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
            InvestigationPriority priority = InvestigationPriority.HIGH;
            EvidenceConfidence confidence = (bias.worstDisparateImpactRatio < 0.80 && perf.expectedCalibrationError > 0.05)
                    ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("protectedAttribute", bias.protectedAttribute);
            evidence.put("worstDisparateImpactRatio", bias.worstDisparateImpactRatio);
            evidence.put("demographicParityGap", bias.demographicParityGap);
            evidence.put("equalOpportunityGap", bias.equalOpportunityGap);
            evidence.put("overallF1Score", perf.f1);
            evidence.put("overallECE", perf.expectedCalibrationError);

            String summary = String.format("Protected attribute '%s' shows fairness disparity (Worst Disparate Impact: %.4f, Parity Gap: %.4f) alongside general model performance degradation.",
                    bias.protectedAttribute, bias.worstDisparateImpactRatio, bias.demographicParityGap);

            String whyItMatters = "Subgroup performance disparities often reveal that general model optimization is heavily biased toward majority groups at the expense of under-represented protected slices.";

            String investigationDirection = "1. Compare per-group calibration curves and threshold curves across slices of '" + bias.protectedAttribute + "'.\n2. Explore group-stratified sample balancing or threshold tuning.";

            findings.add(RuleEvaluationHelper.createCorrelation(
                    norm,
                    getRuleId(),
                    bias.protectedAttribute,
                    "SUBGROUP_PERFORMANCE_DISPARITY",
                    severity,
                    priority,
                    confidence,
                    bias.protectedAttribute,
                    "Subgroup Performance Disparity on Protected Slice: " + bias.protectedAttribute,
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
