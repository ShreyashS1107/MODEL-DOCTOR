package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class RobustnessReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public RobustnessReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_ROBUSTNESS_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.ROBUSTNESS_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null || norm.getRobustnessByFeature().isEmpty()) return list;

        for (var entry : norm.getRobustnessByFeature().entrySet()) {
            String feature = entry.getKey();
            var rob = entry.getValue();

            if (rob.flipRate < 0.10 && rob.sensitivityRank > 2) continue;

            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.ROBUSTNESS_REVIEW);
            rem.setTitle(String.format("Harden Perturbation Stability & Boundary Sensitivity for '%s'", feature));
            rem.setDescription(String.format("Feature '%s' demonstrates high vulnerability to minor input noise (flip rate = %.1f%%, sensitivity rank #%d).", feature, rob.flipRate * 100.0, rob.sensitivityRank));

            double score = 78.0 + Math.min(rob.flipRate * 30.0, 15.0);
            rem.setPriorityScore(Math.min(score, 94.0));
            rem.setPriority(score >= 85.0 ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH);
            rem.setConfidence(EvidenceConfidence.HIGH);
            rem.setEvidenceStrength("HIGH");

            rem.setHypothesis(String.format(
                    "Feature '%s' exhibits elevated decision boundary sensitivity (%.1f%% label flips under 5%% Gaussian noise). " +
                            "This evidence suggests brittle decision splits, lack of regularization, or steep loss gradients near the decision boundary.",
                    feature, rob.flipRate * 100.0
            ));

            rem.setExpectedEffect("Increase model prediction stability under input measurement noise and adversarial jitter.");
            rem.setValidationStrategy("Incorporate adversarial noise augmentation / regularization (L1/L2, tree depth constraint) during training and re-evaluate ROBUSTNESS and PERFORMANCE.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Feature '%s' perturbation flip rate decreases below 5.0%%", feature),
                    "Mean predicted probability shift under noise drops below 0.02",
                    "Unperturbed test performance (F1 / ROC-AUC) does not degrade"
            );

            List<String> requiredModules = List.of("ROBUSTNESS", "PERFORMANCE", "EXPLAINABILITY");
            List<String> regressionGuards = List.of("f1Score", "rocAuc");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("flipRate", "DECREASE", "Regularization and data jittering smooth the decision boundary", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("meanProbShift", "DECREASE", "Smoother loss surfaces produce stable probability outputs under noise", "MEDIUM", "UNKNOWN")
            );

            rem.setSourceInvestigationTarget("FEATURE::" + feature);

            try {
                rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
                rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
                rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
                rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

                List<String> corrIds = correlations.stream()
                        .filter(c -> feature.equals(c.getFeature()) && c.getRuleId().contains("ROBUSTNESS"))
                        .map(DiagnosticCorrelation::getRuleId)
                        .toList();
                rem.setSourceCorrelationIdsJson(objectMapper.writeValueAsString(corrIds));

                Map<String, Long> resultIds = new LinkedHashMap<>();
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.ROBUSTNESS)) {
                    resultIds.put("ROBUSTNESS", norm.getModuleResultIds().get(DiagnosticModule.ROBUSTNESS));
                }
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.PERFORMANCE)) {
                    resultIds.put("PERFORMANCE", norm.getModuleResultIds().get(DiagnosticModule.PERFORMANCE));
                }
                rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
            } catch (Exception ignored) {}

            list.add(rem);
        }

        return list;
    }
}
