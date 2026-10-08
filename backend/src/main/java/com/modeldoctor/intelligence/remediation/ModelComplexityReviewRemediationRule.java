package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class ModelComplexityReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public ModelComplexityReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_MODEL_COMPLEXITY";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.MODEL_COMPLEXITY_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null) return list;

        boolean hasComplexityRisk = correlations.stream().anyMatch(c ->
                "MULTI_FRAGILE_FEATURE_RISK".equals(c.getRuleId()) ||
                        "ROBUSTNESS_EXPLAINABILITY_INTERACTION".equals(c.getRuleId())
        );

        if (!hasComplexityRisk) return list;

        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(run.getId());
        rem.setTargetType("GLOBAL");
        rem.setTargetKey("GLOBAL::MODEL_COMPLEXITY");
        rem.setRemediationType(RemediationType.MODEL_COMPLEXITY_REVIEW);
        rem.setTitle("Model Architecture Complexity & Attribution Concentration Audit");
        rem.setDescription("Observed high attribution concentration on fragile features coupled with perturbation sensitivity justifies reviewing tree depth, feature subsampling, or regularization.");

        rem.setPriorityScore(79.0);
        rem.setPriority(InvestigationPriority.HIGH);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("MEDIUM");

        rem.setHypothesis(
                "A small subset of fragile features dominates model attribution while showing vulnerability to noise. " +
                        "This evidence suggests the model may be over-indexing on narrow decision paths rather than learning distributed predictive signals."
        );

        rem.setExpectedEffect("Distribute feature attribution more evenly and improve resilience across out-of-distribution inputs.");
        rem.setValidationStrategy("Test models with stronger regularization (e.g., lower max_depth, colsample_bytree < 0.8, higher min_child_weight) and re-evaluate EXPLAINABILITY and ROBUSTNESS.");

        List<String> acceptanceCriteria = List.of(
                "Top feature attribution concentration decreases to < 35%",
                "Adversarial flip rate decreases by at least 25%",
                "Holdout validation accuracy remains stable"
        );

        List<String> requiredModules = List.of("EXPLAINABILITY", "ROBUSTNESS", "PERFORMANCE");
        List<String> regressionGuards = List.of("f1Score", "rocAuc");

        List<ExpectedImpactDto> expectedImpacts = List.of(
                new ExpectedImpactDto("attributionGini", "DECREASE", "Ensemble feature subsampling forces distributed predictor utilization", "MEDIUM", "UNKNOWN"),
                new ExpectedImpactDto("gaussianJitterFlipRate", "DECREASE", "Constrained tree depth eliminates overfitted narrow decision leaves", "HIGH", "UNKNOWN")
        );

        rem.setSourceInvestigationTarget("GLOBAL::MODEL_COMPLEXITY");

        try {
            rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
            rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
            rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
            rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

            Map<String, Long> resultIds = new LinkedHashMap<>();
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.EXPLAINABILITY)) {
                resultIds.put("EXPLAINABILITY", norm.getModuleResultIds().get(DiagnosticModule.EXPLAINABILITY));
            }
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.ROBUSTNESS)) {
                resultIds.put("ROBUSTNESS", norm.getModuleResultIds().get(DiagnosticModule.ROBUSTNESS));
            }
            rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
        } catch (Exception ignored) {}

        list.add(rem);
        return list;
    }
}
