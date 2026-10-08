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
public class ConfidenceCalibrationErrorRule implements DiagnosticCorrelationRule {

    @Override
    public String getRuleId() {
        return "CONFIDENCE_CALIBRATION_ERROR";
    }

    @Override
    public String getRuleName() {
        return "High-Confidence Errors with Calibration Misalignment";
    }

    @Override
    public List<DiagnosticModule> getRequiredModules() {
        return List.of(DiagnosticModule.ERROR_FORENSICS, DiagnosticModule.PERFORMANCE);
    }

    @Override
    public List<DiagnosticCorrelation> evaluate(NormalizedModuleData norm, ObjectMapper objectMapper) {
        List<DiagnosticCorrelation> findings = new ArrayList<>();
        if (!norm.getAvailableModules().containsAll(getRequiredModules())) {
            return findings;
        }

        NormalizedModuleData.ErrorForensicsSummary errSum = norm.getErrorForensicsSummary();
        NormalizedModuleData.PerformanceSummary perfSum = norm.getPerformanceSummary();

        if (errSum == null || perfSum == null) return findings;

        boolean hasHighConfErrors = errSum.highConfidenceErrorRate >= 0.10 || errSum.highConfidenceErrorCount >= 5;
        boolean hasCalibrationMisalignment = perfSum.expectedCalibrationError >= 0.08 || !errSum.severeCalibrationBins.isEmpty();

        if (hasHighConfErrors && hasCalibrationMisalignment) {
            SeverityLevel severity = (errSum.highConfidenceErrorRate >= 0.20 || perfSum.expectedCalibrationError >= 0.15)
                    ? SeverityLevel.CRITICAL : SeverityLevel.HIGH;
            InvestigationPriority priority = severity == SeverityLevel.CRITICAL ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH;
            EvidenceConfidence confidence = EvidenceConfidence.HIGH;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("highConfidenceErrorCount", errSum.highConfidenceErrorCount);
            evidence.put("highConfidenceErrorRate", errSum.highConfidenceErrorRate);
            evidence.put("overallErrorRate", errSum.overallErrorRate);
            evidence.put("expectedCalibrationError", perfSum.expectedCalibrationError);
            evidence.put("severeCalibrationBins", errSum.severeCalibrationBins);
            evidence.put("rocAuc", perfSum.rocAuc);
            evidence.put("f1Score", perfSum.f1);

            String summary = String.format("Elevated concentration of high-confidence prediction errors (Rate: %.1f%%, Count: %d) coincides with probability calibration misalignment (Expected Calibration Error: %.4f).",
                    errSum.highConfidenceErrorRate * 100.0, errSum.highConfidenceErrorCount, perfSum.expectedCalibrationError);

            String whyItMatters = "When the model outputs extreme prediction probabilities that diverge systematically from empirical cohort base rates, users may rely on false confidence in misclassified decisions.";

            String investigationDirection = "1. Inspect reliability curves in PERFORMANCE and ERROR_FORENSICS views to locate miscalibrated probability intervals.\n" +
                    "2. Examine whether high-confidence mistakes cluster around specific feature ranges or anomalous inputs.\n" +
                    "3. Apply post-hoc Platt scaling or Isotonic regression calibration before production deployment.";

            findings.add(RuleEvaluationHelper.createCorrelation(
                    norm,
                    getRuleId(),
                    "CALIBRATION_CONFIDENCE",
                    "CONFIDENCE_CALIBRATION_ERROR",
                    severity,
                    priority,
                    confidence,
                    null,
                    "High-Confidence Errors & Calibration Misalignment",
                    summary,
                    whyItMatters,
                    investigationDirection,
                    evidence,
                    getRequiredModules(),
                    objectMapper
            ));
        }

        return findings;
    }
}
