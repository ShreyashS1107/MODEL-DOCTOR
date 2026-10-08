package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class FeatureRemovalReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public FeatureRemovalReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_FEATURE_REMOVAL_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.FEATURE_REMOVAL_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null) return list;

        Set<String> candidateFeatures = new HashSet<>();

        // Candidate 1: High leakage
        norm.getLeakageByFeature().forEach((feat, l) -> {
            if (l.leakageScore >= 0.70 || l.isSuspicious) {
                candidateFeatures.add(feat);
            }
        });

        // Candidate 2: High missingness + high error association + high fragility
        norm.getQualityByFeature().forEach((feat, q) -> {
            if (q.nullRate >= 0.15) {
                boolean hasErr = norm.getErrorByFeature().containsKey(feat) && norm.getErrorByFeature().get(feat).absoluteAssociation >= 0.25;
                boolean hasFragile = norm.getRobustnessByFeature().containsKey(feat) && norm.getRobustnessByFeature().get(feat).flipRate >= 0.10;
                if (hasErr && hasFragile) {
                    candidateFeatures.add(feat);
                }
            }
        });

        for (String feature : candidateFeatures) {
            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.FEATURE_REMOVAL_REVIEW);
            rem.setTitle(String.format("Empirical Feature Ablation & Removal Review for '%s'", feature));
            rem.setDescription(String.format("Multiple compounding risk signals (leakage, data quality, and error concentration) indicate feature '%s' may degrade generalization and warrant ablation testing.", feature));

            rem.setPriorityScore(91.0);
            rem.setPriority(InvestigationPriority.CRITICAL);
            rem.setConfidence(EvidenceConfidence.HIGH);
            rem.setEvidenceStrength("HIGH");

            rem.setHypothesis(String.format(
                    "Feature '%s' exhibits multiple converging risk factors without demonstrated generalization benefit. " +
                            "Feature removal is a candidate hypothesis to validate empirically, not an automatic action.",
                    feature
            ));

            rem.setExpectedEffect("Determine whether removing the feature improves out-of-sample generalization or eliminates artificial metric distortions.");
            rem.setValidationStrategy("Train candidate model without feature, execute full diagnostic comparison against baseline, and verify metrics across holdout sets.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Ablated model achieves comparable or superior holdout F1 without '%s'", feature),
                    "Overall calibration (ECE) and adversarial robustness improve or remain stable",
                    "Elimination of feature verified to have zero downstream operational dependency"
            );

            List<String> requiredModules = List.of("PERFORMANCE", "ERROR_FORENSICS", "ROBUSTNESS", "EXPLAINABILITY");
            List<String> regressionGuards = List.of("f1Score", "rocAuc");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("modelSimplicity", "INCREASE", "Removing uninformative/leaked feature reduces pipeline maintenance and inference latency", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("generalizationError", "DECREASE", "Eliminates overfitting to spurious correlations", "MEDIUM", "UNKNOWN")
            );

            rem.setSourceInvestigationTarget("FEATURE::" + feature);

            try {
                rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
                rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
                rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
                rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

                Map<DiagnosticModule, Long> resultIds = norm.getModuleResultIds();
                rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
            } catch (Exception ignored) {}

            list.add(rem);
        }

        return list;
    }
}
