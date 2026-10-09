package com.modeldoctor.intelligence.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.*;
import com.modeldoctor.dto.ExpectedImpactDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class CalibrationReviewRemediationRule implements DiagnosticRemediationRule {

    private final ObjectMapper objectMapper;

    public CalibrationReviewRemediationRule(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getRuleId() {
        return "REM_CALIBRATION_REVIEW";
    }

    @Override
    public String getRemediationType() {
        return RemediationType.CALIBRATION_REVIEW.name();
    }

    @Override
    public List<DiagnosticRemediation> evaluate(
            NormalizedModuleData norm,
            List<DiagnosticCorrelation> correlations,
            List<DiagnosticInvestigation> investigations,
            DiagnosticRun run) {

        List<DiagnosticRemediation> list = new ArrayList<>();
        if (norm == null) return list;

        double ece = 0.0;
        double brier = 0.0;
        if (norm.getPerformanceSummary() != null) {
            ece = norm.getPerformanceSummary().expectedCalibrationError;
            brier = norm.getPerformanceSummary().brierScore;
        }

        boolean hasCalibrationDefect = ece >= 0.08 || brier >= 0.20 ||
                correlations.stream().anyMatch(c -> "CONFIDENCE_CALIBRATION_ERROR".equals(c.getRuleId()));

        if (!hasCalibrationDefect) return list;

        DiagnosticRemediation rem = new DiagnosticRemediation();
        rem.setRunId(run.getId());
        rem.setTargetType("BEHAVIOR");
        rem.setTargetKey("BEHAVIOR::CALIBRATION_FAILURE");
        rem.setRemediationType(RemediationType.CALIBRATION_REVIEW);
        rem.setTitle("Post-Hoc Probability Calibration Review (Platt Scaling / Isotonic)");
        rem.setDescription(String.format("Model probabilities exhibit systematic miscalibration (ECE = %.3f, Brier = %.3f), distorting confidence-weighted decisions.", ece, brier));

        double score = 75.0 + Math.min(ece * 80.0, 15.0);
        rem.setPriorityScore(Math.min(score, 92.0));
        rem.setPriority(score >= 80.0 ? InvestigationPriority.CRITICAL : InvestigationPriority.HIGH);
        rem.setConfidence(EvidenceConfidence.HIGH);
        rem.setEvidenceStrength("HIGH");

        rem.setHypothesis(String.format(
                "Predicted probabilities systematically diverge from observed empirical event frequencies (ECE = %.3f, Brier Score = %.3f). " +
                        "Applying post-hoc calibration methods (Platt Scaling or Isotonic Regression) on a calibration holdout set is expected to restore reliability.",
                ece, brier
        ));

        rem.setExpectedEffect("Align predicted confidence scores with observed empirical win/positive rates across all 10 probability deciles.");
        rem.setValidationStrategy("Fit post-hoc calibration adapter (Platt Scaling/Isotonic) on separate calibration fold and re-evaluate PERFORMANCE and ERROR_FORENSICS.");

        List<String> acceptanceCriteria = List.of(
                "Expected Calibration Error (ECE) decreases below 0.05",
                "Brier score decreases or remains stable",
                "Model discrimination ranking (ROC-AUC) is strictly preserved"
        );

        List<String> requiredModules = List.of("PERFORMANCE", "ERROR_FORENSICS");
        List<String> regressionGuards = List.of("rocAuc", "prAuc");

        List<ExpectedImpactDto> expectedImpacts = List.of(
                new ExpectedImpactDto("expectedCalibrationError", "DECREASE", "Calibration adapter directly minimizes ECE without altering rank ordering", "HIGH", "UNKNOWN"),
                new ExpectedImpactDto("brierScore", "DECREASE", "Calibrated probabilities improve mean squared probability error", "MEDIUM", "UNKNOWN")
        );

        rem.setSourceInvestigationTarget("BEHAVIOR::CALIBRATION_FAILURE");

        try {
            rem.setAcceptanceCriteriaJson(objectMapper.writeValueAsString(acceptanceCriteria));
            rem.setRequiredModulesJson(objectMapper.writeValueAsString(requiredModules));
            rem.setRegressionGuardsJson(objectMapper.writeValueAsString(regressionGuards));
            rem.setExpectedImpactJson(objectMapper.writeValueAsString(expectedImpacts));

            List<String> corrIds = correlations.stream()
                    .filter(c -> "CONFIDENCE_CALIBRATION_ERROR".equals(c.getRuleId()))
                    .map(DiagnosticCorrelation::getRuleId)
                    .toList();
            rem.setSourceCorrelationIdsJson(objectMapper.writeValueAsString(corrIds));

            Map<String, Long> resultIds = new LinkedHashMap<>();
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.PERFORMANCE)) {
                resultIds.put("PERFORMANCE", norm.getModuleResultIds().get(DiagnosticModule.PERFORMANCE));
            }
            if (norm.getModuleResultIds().containsKey(DiagnosticModule.ERROR_FORENSICS)) {
                resultIds.put("ERROR_FORENSICS", norm.getModuleResultIds().get(DiagnosticModule.ERROR_FORENSICS));
            }
            rem.setSourceResultIdsJson(objectMapper.writeValueAsString(resultIds));
        } catch (Exception ignored) {}

        list.add(rem);
        return list;
    }
}
