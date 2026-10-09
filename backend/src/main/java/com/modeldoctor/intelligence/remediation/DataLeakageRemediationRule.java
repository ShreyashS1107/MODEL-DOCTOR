package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class DataLeakageRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public DataLeakageRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_DATA_LEAKAGE";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.DATA_LEAKAGE_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null || norm.getLeakageByFeature().isEmpty()) return list;

        for (var entry : norm.getLeakageByFeature().entrySet()) {
            String feature = entry.getKey();
            var l = entry.getValue();

            boolean highLeakage = l.leakageScore >= 0.70 || l.mutualInfo >= 0.50 || Math.abs(l.correlation) >= 0.80;
            if (!highLeakage) continue;

            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.DATA_LEAKAGE_REVIEW);
            rem.setTitle(String.format("Audit Target Leakage Risk for Feature '%s'", feature));
            rem.setDescription(String.format("Feature '%s' exhibits high mutual information / correlation (leakage score = %.2f, MI = %.3f) with the target variable.", feature, l.leakageScore, l.mutualInfo));

            double score = 88.0 + Math.min(l.leakageScore * 10.0, 10.0);
            rem.setPriorityScore(Math.min(score, 99.0));
            rem.setPriority(InvestigationPriority.CRITICAL);
            rem.setConfidence(EvidenceConfidence.HIGH);
            rem.setEvidenceStrength("HIGH");

            rem.setHypothesis(String.format(
                    "Feature '%s' demonstrates high mutual information (%.3f) and correlation (%.3f) with target '%s'. " +
                            "This evidence indicates a potential target proxy or post-event feature created during extraction.",
                    feature, l.mutualInfo, l.correlation, run.getTargetColumn()
            ));

            rem.setExpectedEffect("Establish an authentic evaluation by eliminating target proxy leakage. Note: Removing a leaked feature may reduce apparent offline metrics but improves real generalization.");
            rem.setValidationStrategy("Verify data extraction timestamp constraints, audit feature lineage, retrain candidate without suspicious proxy, and re-evaluate LEAKAGE and PERFORMANCE.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Feature '%s' verified clean of post-event data", feature),
                    "Model evaluation reflects genuine generalization without artificial metric inflation",
                    "Evaluation on holdout partition shows consistent discrimination"
            );

            List<String> requiredModules = List.of("LEAKAGE", "PERFORMANCE", "EXPLAINABILITY");
            List<String> regressionGuards = List.of("brierScore", "expectedCalibrationError");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("maxMutualInfo", "DECREASE", "Removing or correcting target proxy eliminates artificial MI inflation", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("f1Score", "STABLE", "Genuine performance may adjust downward to reflect true production predictability", "MEDIUM", "UNKNOWN")
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
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.LEAKAGE)) {
                    resultIds.put("LEAKAGE", norm.getModuleResultIds().get(DiagnosticModule.LEAKAGE));
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
