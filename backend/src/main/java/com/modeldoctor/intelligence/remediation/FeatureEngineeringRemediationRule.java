package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class FeatureEngineeringRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public FeatureEngineeringRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_FEATURE_ENGINEERING";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.FEATURE_ENGINEERING_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null || norm.getImportanceByFeature().isEmpty() || norm.getErrorByFeature().isEmpty()) return list;

        for (var entry : norm.getImportanceByFeature().entrySet()) {
            String feature = entry.getKey();
            var imp = entry.getValue();

            if (imp.rank > 3) continue;

            if (!norm.getErrorByFeature().containsKey(feature)) continue;
            var err = norm.getErrorByFeature().get(feature);

            if (err.absoluteAssociation < 0.20 && !err.isErrorEnriched) continue;

            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.FEATURE_ENGINEERING_REVIEW);
            rem.setTitle(String.format("Review Feature Representation & Transformations for '%s'", feature));
            rem.setDescription(String.format("Feature '%s' is highly influential in the model (SHAP rank #%d) while exhibiting strong correlation with observed errors (r = %.3f).", feature, imp.rank, err.correlation));

            double score = 80.0 + (4 - imp.rank) * 3.0 + Math.min(err.absoluteAssociation * 20.0, 10.0);
            rem.setPriorityScore(Math.min(score, 98.0));
            rem.setPriority(InvestigationPriority.CRITICAL);
            rem.setConfidence(EvidenceConfidence.HIGH);
            rem.setEvidenceStrength("HIGH");

            rem.setHypothesis(String.format(
                    "Feature '%s' is strongly associated with prediction errors (r = %.3f, p_adj = %.4f) and is simultaneously one of the most influential predictors (SHAP rank #%d). " +
                            "This evidence justifies reviewing feature transformations, non-linear representations, binning schemes, or interaction terms.",
                    feature, err.correlation, err.adjustedPValue, imp.rank
            ));

            rem.setExpectedEffect("Improve model discrimination in feature regions with concentrated error rates.");
            rem.setValidationStrategy("Test candidate feature refinements (e.g. non-linear scaling, quantile bucketing, interaction features) and re-evaluate ERROR_FORENSICS and EXPLAINABILITY.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Error association |r| for '%s' decreases below 0.15", feature),
                    "Model F1 and ROC-AUC do not decrease",
                    "Calibration error ECE does not worsen"
            );

            List<String> requiredModules = List.of("EXPLAINABILITY", "ERROR_FORENSICS", "PERFORMANCE", "ROBUSTNESS");
            List<String> regressionGuards = List.of("f1Score", "rocAuc", "brierScore");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("errorCorrelation", "DECREASE", "Refined feature representation reduces linear and non-linear association with residual errors", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("f1Score", "INCREASE", "Better feature separation should improve decision boundary accuracy", "MEDIUM", "UNKNOWN")
            );

            rem.setSourceInvestigationTarget("FEATURE::" + feature);

            try {
                rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
                rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
                rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
                rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

                List<String> corrIds = correlations.stream()
                        .filter(c -> feature.equals(c.getFeature()))
                        .map(DiagnosticCorrelation::getRuleId)
                        .toList();
                rem.setSourceCorrelationIdsJson(objectMapper.writeValueAsString(corrIds));

                Map<String, Long> resultIds = new LinkedHashMap<>();
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.EXPLAINABILITY)) {
                    resultIds.put("EXPLAINABILITY", norm.getModuleResultIds().get(DiagnosticModule.EXPLAINABILITY));
                }
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS)) {
                    resultIds.put("ERROR_FORENSICS", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
                }
                rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
            } catch (Exception ignored) {}

            list.add(rem);
        }

        return list;
    }
}
