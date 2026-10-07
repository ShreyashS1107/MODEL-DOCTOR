package com.modeldoctor.intelligence.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticCorrelation;
import com.modeldoctor.domain.DiagnosticModule;
import com.modeldoctor.domain.EvidenceConfidence;
import com.modeldoctor.domain.InvestigationPriority;
import com.modeldoctor.domain.SeverityLevel;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataQualityPerformanceInteractionRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "DATA_QUALITY_PERFORMANCE_INTERACTION";
    }

    @Override
    public String getRuleName() {
        return "Data Quality Degradation / Performance Interaction";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.DATA_QUALITY, DiagnosticModule.PERFORMANCE);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.PerformanceSummary perf = norm.getPerformanceSummary();
        if (perf == null) return findings;

        boolean hasPerformanceDegradation = perf.f1 < 0.60 || perf.expectedCalibrationError > 0.05 || perf.healthScore < 85.0;
        if (!hasPerformanceDegradation) return findings;

        for (Map.Entry<String, NormalizedModuleData.FeatureQualityData> entry : norm.getQualityByFeature().entrySet()) {
            String feature = entry.getKey();
            NormalizedModuleData.FeatureQualityData qual = entry.getValue();

            boolean hasQualityIssue = qual.nullRate >= 0.03 || qual.outlierRate >= 0.08 || qual.isConstant;

            if (hasQualityIssue) {
                SeverityLevel severity = (qual.nullRate >= 0.15 || qual.isConstant) ? SeverityLevel.HIGH : SeverityLevel.MEDIUM;
                InvestigationPriority priority = InvestigationPriority.MEDIUM;
                EvidenceConfidence confidence = qual.nullRate >= 0.05 ? EvidenceConfidence.HIGH : EvidenceConfidence.MEDIUM;

                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("feature", feature);
                evidence.put("nullRate", qual.nullRate);
                evidence.put("outlierRate", qual.outlierRate);
                evidence.put("isConstant", qual.isConstant);
                evidence.put("f1Score", perf.f1);
                evidence.put("expectedCalibrationError", perf.expectedCalibrationError);

                String summary = String.format("Data quality issues in '%s' (Null Rate: %.2f%%, Outlier Rate: %.2f%%) coincide with reduced model performance.",
                        feature, qual.nullRate * 100.0, qual.outlierRate * 100.0);

                String whyItMatters = "Missing or corrupted feature values in production datasets degrade estimation precision and create skewed inference outputs.";

                String investigationDirection = "1. Review upstream ETL data cleaning and imputation pipelines for '" + feature + "'.\n2. Verify missing value handling strategy during model training.";

                findings.add(RuleEvaluationHelper.createCorrelation(
                        norm,
                        getRuleId(),
                        feature,
                        "QUALITY_PERFORMANCE_INTERACTION",
                        severity,
                        priority,
                        confidence,
                        feature,
                        "Potential Data-Quality / Performance Interaction: " + feature,
                        summary,
                        whyItMatters,
                        investigationDirection,
                        evidence,
                        getRequiredModules(),
                        objectMapper
                ));
            }
        }

        return findings;
    }
}
