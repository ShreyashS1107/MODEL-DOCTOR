package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FairnessReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public FairnessReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_FAIRNESS_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.FAIRNESS_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        boolean hasFairnessSummaryViolation = norm.getBiasSummary() != null &&
                (norm.getBiasSummary().worstDisparateImpactRatio < 0.80 ||
                        norm.getBiasSummary().equalOpportunityGap >= 0.10 ||
                        norm.getBiasSummary().demographicParityGap >= 0.10 ||
                        !norm.getBiasSummary().passed);

        boolean hasFairnessFinding = hasFairnessSummaryViolation || correlations.stream().anyMatch(c ->
                "ERROR_BIAS_INTERACTION".equals(c.getRuleId()) ||
                        "BIAS_PERFORMANCE_INTERACTION".equals(c.getRuleId()) ||
                        "MULTI_FAIRNESS_SHIFT_RISK".equals(c.getRuleId()) ||
                        "BIAS_DRIFT_INTERACTION".equals(c.getRuleId())
        );

        if (!hasFairnessFinding) return list;

        String protectedAttr = run.getProtectedAttribute() != null ? run.getProtectedAttribute() : "PROTECTED_ATTRIBUTE";

        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(run.getId());
        rem.setTargetType("SUBGROUP");
        rem.setTargetKey("SUBGROUP::" + protectedAttr);
        rem.setRemediationType(RemediationType.FAIRNESS_REVIEW);
        rem.setTitle(String.format("Fairness Mitigation & Subgroup Parity Review for '%s'", protectedAttr));
        rem.setDescription(String.format("Demographic / functional subgroup parity gaps or disparate impact violations detected on protected attribute '%s'.", protectedAttr));

        rem.setPriorityScore(86.0);
        rem.setPriority(InvestigationPriority.CRITICAL);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("HIGH");

        rem.setHypothesis(String.format(
                "Subgroup slices across '%s' exhibit statistically significant error rate disparity or disparate impact ratio below 0.80. " +
                        "This evidence warrants reviewing training sample representation, feature proxy correlations, or post-processing threshold adjustments.",
                protectedAttr
        ));

        rem.setExpectedEffect("Mitigate disparate impact and equalize true positive / error rates across protected attribute slices.");
        rem.setValidationStrategy("Audit training slice representation, apply group-aware sample reweighting or threshold adjustment, and re-run BIAS, PERFORMANCE, and ERROR_FORENSICS.");

        List<String> acceptanceCriteria = List.of(
                "Disparate Impact Ratio satisfies 80% rule (DIR >= 0.80)",
                "Subgroup True Positive Rate (TPR) gap narrows below 0.05",
                "Overall model accuracy remains within domain tolerance"
        );

        List<String> requiredModules = List.of("BIAS", "PERFORMANCE", "ERROR_FORENSICS");
        List<String> regressionGuards = List.of("f1Score", "rocAuc");

        List<ExpectedImpactDto> expectedImpacts = List.of(
                new ExpectedImpactDto("disparateImpactRatio", "INCREASE", "Fairness intervention balances acceptance rates across demographic groups", "HIGH", "UNKNOWN"),
                new ExpectedImpactDto("subgroupErrorDisparity", "DECREASE", "Group-aware reweighting narrows the error rate delta between groups", "MEDIUM", "UNKNOWN")
        );

        rem.setSourceInvestigationTarget("SUBGROUP::" + protectedAttr);

        try {
            rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
            rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
            rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
            rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

            List<String> corrIds = correlations.stream()
                    .filter(c -> c.getRuleId().contains("BIAS") || c.getRuleId().contains("FAIRNESS"))
                    .map(DiagnosticCorrelation::getRuleId)
                    .toList();
            rem.setSourceCorrelationIdsJson(objectMapper.writeValueAsString(corrIds));

            Map<String, Long> resultIds = new LinkedHashMap<>();
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.BIAS)) {
                resultIds.put("BIAS", norm.getModuleResultIds().get(DiagnosticModule.BIAS));
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
