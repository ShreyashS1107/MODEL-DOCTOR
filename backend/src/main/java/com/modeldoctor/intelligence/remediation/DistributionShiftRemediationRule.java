package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class DistributionShiftRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public DistributionShiftRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_DISTRIBUTION_SHIFT";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.DISTRIBUTION_SHIFT_INVESTIGATION.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null || norm.getDriftByFeature().isEmpty()) return list;

        for (var entry : norm.getDriftByFeature().entrySet()) {
            String feature = entry.getKey();
            var drift = entry.getValue();

            if (drift.psi < 0.25) continue;

            boolean hasExplain = norm.getImportanceByFeature().containsKey(feature);
            boolean hasError = norm.getErrorByFeature().containsKey(feature);

            if (!hasExplain && !hasError) continue;

            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.DISTRIBUTION_SHIFT_INVESTIGATION);
            rem.setTitle(String.format("Investigate Feature Distribution Shift for '%s'", feature));
            rem.setDescription(String.format("Feature '%s' exhibits severe distribution shift (PSI = %.3f) between baseline and evaluation sets concurrent with model influence / error association.", feature, drift.psi));

            double priorityScore = 85.0 + Math.min(drift.psi * 4.0, 10.0);
            if (hasError && norm.getErrorByFeature().get(feature).absoluteAssociation >= 0.20) priorityScore += 4.0;
            rem.setPriorityScore(Math.min(priorityScore, 99.5));
            rem.setPriority(InvestigationPriority.CRITICAL);
            rem.setConfidence(EvidenceConfidence.HIGH);
            rem.setEvidenceStrength("HIGH");

            rem.setHypothesis(String.format(
                    "Feature '%s' exhibits severe distribution shift (PSI = %.3f, severity = %s) concurrent with %s. " +
                            "This evidence indicates that upstream pipeline differences, schema changes, or population shifts may be driving distribution divergence.",
                    feature, drift.psi, drift.severity,
                    hasError ? String.format("error association (r = %.3f)", norm.getErrorByFeature().get(feature).correlation)
                            : String.format("top SHAP importance (rank #%d)", norm.getImportanceByFeature().get(feature).rank)
            ));

            rem.setExpectedEffect("Stabilize evaluation feature distribution relative to baseline and resolve divergence across feature quantiles.");
            rem.setValidationStrategy("Verify data ingestion consistency, compare baseline vs evaluation feature distributions, and re-execute DRIFT, PERFORMANCE, and ERROR_FORENSICS.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Feature '%s' PSI decreases below 0.10 warning threshold", feature),
                    "Overall model F1 / ROC-AUC does not regress by > 0.01",
                    "Feature error correlation does not increase"
            );

            List<String> requiredModules = List.of("DRIFT", "PERFORMANCE", "ERROR_FORENSICS", "ROBUSTNESS");
            List<String> regressionGuards = List.of("f1Score", "rocAuc", "expectedCalibrationError");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("psi", "DECREASE", "Upstream pipeline correction aligns evaluation distribution with training baseline", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("errorRate", "DECREASE", "Reducing feature shift is expected to mitigate shift-associated misclassifications", "MEDIUM", "UNKNOWN")
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
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.DRIFT)) {
                    resultIds.put("DRIFT", norm.getModuleResultIds().get(DiagnosticModule.DRIFT));
                }
                if (hasError && norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS)) {
                    resultIds.put("ERROR_FORENSICS", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
                }
                if (hasExplain && norm.getModuleResultIds().containsKey(DiagnosticModule.EXPLAINABILITY)) {
                    resultIds.put("EXPLAINABILITY", norm.getModuleResultIds().get(DiagnosticModule.EXPLAINABILITY));
                }
                rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
            } catch (Exception ignored) {}

            list.add(rem);
        }

        return list;
    }
}
