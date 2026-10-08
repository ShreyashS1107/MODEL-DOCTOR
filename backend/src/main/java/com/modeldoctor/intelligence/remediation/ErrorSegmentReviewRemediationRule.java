package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ErrorSegmentReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public ErrorSegmentReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_ERROR_SEGMENT_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.ERROR_SEGMENT_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null) return list;

        boolean hasHighConfErrors = correlations.stream().anyMatch(c ->
                "CONFIDENCE_CALIBRATION_ERROR".equals(c.getRuleId()) ||
                        c.getSummary().toLowerCase().contains("high-confidence") ||
                        c.getSummary().toLowerCase().contains("confident")
        );

        if (!hasHighConfErrors && !norm.getErrorByFeature().values().stream().anyMatch(e -> e.isErrorEnriched)) {
            return list;
        }

        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(run.getId());
        rem.setTargetType("BEHAVIOR");
        rem.setTargetKey("BEHAVIOR::HIGH_CONFIDENCE_ERRORS");
        rem.setRemediationType(RemediationType.ERROR_SEGMENT_REVIEW);
        rem.setTitle("Audit High-Confidence Error Slices & Hard Negative Samples");
        rem.setDescription("Investigate records where the model assigns high prediction confidence to incorrect outputs, indicating hard negatives, mislabeled ground truth, or localized feature manifold collapse.");

        rem.setPriorityScore(82.0);
        rem.setPriority(InvestigationPriority.HIGH);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("HIGH");

        rem.setHypothesis(
                "A concentration of high-confidence prediction mistakes coincides with specific feature quantiles or subgroup slices. " +
                        "This evidence suggests label noise in ground truth, unobserved confounders, or model overconfidence on out-of-distribution clusters."
        );

        rem.setExpectedEffect("Identify and remediate mislabeled training samples, add hard-negative examples to training data, and improve error slice coverage.");
        rem.setValidationStrategy("Perform manual inspection of top high-confidence error records, audit ground-truth label accuracy, retrain with sample weighting or clean labels, and re-run ERROR_FORENSICS.");

        List<String> acceptanceCriteria = List.of(
                "High-confidence error count decreases by at least 30%",
                "Ground-truth label veracity verified across flagged error records",
                "Model confidence distributions align with actual accuracy"
        );

        List<String> requiredModules = List.of("ERROR_FORENSICS", "PERFORMANCE", "BIAS");
        List<String> regressionGuards = List.of("f1Score", "rocAuc");

        List<ExpectedImpactDto> expectedImpacts = List.of(
                new ExpectedImpactDto("highConfidenceErrorRate", "DECREASE", "Cleaning label noise and hard-negative mining directly reduces egregious high-confidence failures", "HIGH", "UNKNOWN"),
                new ExpectedImpactDto("errorRate", "DECREASE", "Improving boundary resolution on difficult segments reduces overall error rate", "MEDIUM", "UNKNOWN")
        );

        rem.setSourceInvestigationTarget("BEHAVIOR::HIGH_CONFIDENCE_ERRORS");

        try {
            rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
            rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
            rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
            rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

            Map<String, Long> resultIds = new LinkedHashMap<>();
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS)) {
                resultIds.put("ERROR_FORENSICS", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
            }
            rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
        } catch (Exception ignored) {}

        list.add(rem);
        return list;
    }
}
