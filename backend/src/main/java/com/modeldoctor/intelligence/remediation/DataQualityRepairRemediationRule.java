package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataQualityRepairRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public DataQualityRepairRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_DATA_QUALITY";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.DATA_QUALITY_REPAIR.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null || norm.getQualityByFeature().isEmpty()) return list;

        for (var entry : norm.getQualityByFeature().entrySet()) {
            String feature = entry.getKey();
            var q = entry.getValue();

            boolean isSevereQuality = q.nullRate >= 0.15 || q.outlierRate >= 0.05 || q.isConstant;
            if (!isSevereQuality) continue;

            DiagnosticRemediation rem = new DiagnosticRemediation();
            rem.setRunId(run.getId());
            rem.setTargetType("FEATURE");
            rem.setTargetKey("FEATURE::" + feature);
            rem.setRemediationType(RemediationType.DATA_QUALITY_REPAIR);
            rem.setTitle(String.format("Repair Data Quality Defect in Feature '%s'", feature));
            rem.setDescription(String.format("Feature '%s' exhibits elevated data quality anomalies (missing rate = %.1f%%, outlier rate = %.1f%%).", feature, q.nullRate * 100.0, q.outlierRate * 100.0));

            double score = 65.0 + Math.min(q.nullRate * 25.0, 15.0) + (q.outlierRate >= 0.05 ? 5.0 : 0.0);
            rem.setPriorityScore(Math.min(score, 88.0));
            rem.setPriority(score >= 75.0 ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH);
            rem.setConfidence(EvidenceConfidence.MEDIUM);
            rem.setEvidenceStrength("MEDIUM");

            rem.setHypothesis(String.format(
                    "Feature '%s' exhibits missingness (%.1f%%) or outlier concentrations (%.1f%%). " +
                            "This evidence indicates pipeline ingestion truncation, improper default imputation, or unhandled sensor/schema anomalies.",
                    feature, q.nullRate * 100.0, q.outlierRate * 100.0
            ));

            rem.setExpectedEffect("Eliminate unhandled nulls and anomalous outliers to restore training/evaluation feature integrity.");
            rem.setValidationStrategy("Implement schema validation at ingestion, apply domain-appropriate imputation/clamping, and re-run DATA_QUALITY and ERROR_FORENSICS.");

            List<String> acceptanceCriteria = List.of(
                    String.format("Feature '%s' missing rate drops below 5.0%%", feature),
                    "Outlier rate is bounded within expected domain thresholds",
                    "Model prediction coverage and reliability improve"
            );

            List<String> requiredModules = List.of("DATA_QUALITY", "PERFORMANCE", "ERROR_FORENSICS");
            List<String> regressionGuards = List.of("f1Score", "rocAuc");

            List<ExpectedImpactDto> expectedImpacts = List.of(
                    new ExpectedImpactDto("nullRate", "DECREASE", "Data pipeline imputation/validation directly reduces missing values", "HIGH", "UNKNOWN"),
                    new ExpectedImpactDto("errorRate", "DECREASE", "Clean input features reduce default-imputation prediction mistakes", "MEDIUM", "UNKNOWN")
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
                if (norm.getModuleResultIds().containsKey(DiagnosticModule.DATA_QUALITY)) {
                    resultIds.put("DATA_QUALITY", norm.getModuleResultIds().get(DiagnosticModule.DATA_QUALITY));
                }
                rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
            } catch (Exception ignored) {}

            list.add(rem);
        }

        return list;
    }
}
