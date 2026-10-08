package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ThresholdReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public ThresholdReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_THRESHOLD_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.THRESHOLD_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null) return list;

        boolean hasErrorForensics = norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS);
        boolean hasPerformance = norm.getPerformanceSummary() != null;

        if (!hasErrorForensics && !hasPerformance) return list;

        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(run.getId());
        rem.setTargetType("GLOBAL");
        rem.setTargetKey("GLOBAL::THRESHOLD_TUNING");
        rem.setRemediationType(RemediationType.THRESHOLD_REVIEW);
        rem.setTitle("Decision Threshold Optimization & Operating Point Calibration");
        rem.setDescription("Reassess operational decision threshold (default 0.50) using the 21-point cost-sensitive threshold grid to balance False Positives vs False Negatives.");

        rem.setPriorityScore(72.0);
        rem.setPriority(InvestigationPriority.HIGH);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("MEDIUM");

        rem.setHypothesis(
                "The default 0.50 decision threshold may not align with domain utility costs. " +
                        "The forensic threshold sweep demonstrates operating points where False Negative Rate can be reduced with bounded False Positive inflation."
        );

        rem.setExpectedEffect("Optimize the precision/recall tradeoff for domain business utility without retraining model parameters.");
        rem.setValidationStrategy("Select candidate threshold on validation sweep, evaluate business utility on holdout set, and re-run PERFORMANCE and ERROR_FORENSICS.");

        List<String> acceptanceCriteria = List.of(
                "Operating point satisfies domain false-positive budget",
                "Target recall/F1 objectives achieved at new decision boundary",
                "Threshold decision is documented in model operational card"
        );

        List<String> requiredModules = List.of("PERFORMANCE", "ERROR_FORENSICS");
        List<String> regressionGuards = List.of("rocAuc", "prAuc");

        List<ExpectedImpactDto> expectedImpacts = List.of(
                new ExpectedImpactDto("falseNegativeRate", "DECREASE", "Lowering threshold directly catches additional positive cases", "HIGH", "UNKNOWN"),
                new ExpectedImpactDto("f1Score", "INCREASE", "Operating at peak F1 threshold improves overall harmonic precision-recall balance", "MEDIUM", "UNKNOWN")
        );

        rem.setSourceInvestigationTarget("GLOBAL::THRESHOLD_TUNING");

        try {
            rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
            rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
            rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
            rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

            Map<String, Long> resultIds = new LinkedHashMap<>();
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.PERFORMANCE)) {
                resultIds.put("PERFORMANCE", norm.getModuleResultIds().get(DiagnosticModule.PERFORMANCE));
            }
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS)) {
                resultIds.put("ERROR_FORENSICS", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
            }
            rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
        } catch (Exception ignored) {}

        list.add(rem);
        return list;
    }
}
