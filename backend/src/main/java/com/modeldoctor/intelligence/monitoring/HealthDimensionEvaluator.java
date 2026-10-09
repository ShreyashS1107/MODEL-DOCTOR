package com.modeldoctor.intelligence.monitoring;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.HealthDimensionEvaluationDto;
import com.modeldoctor.dto.HealthDimensionEvaluationDto.MetricEvidenceItemDto;
import com.modeldoctor.dto.IssueTrackDto;
import com.modeldoctor.dto.RemediationDurabilityDto;
import com.modeldoctor.dto.TemporalMetricHistoryDto;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class HealthDimensionEvaluator {

    public Map<HealthDimension, HealthDimensionEvaluationDto> evaluateDimensions(
            NormalizedModuleData norm,
            List<DiagnosticResult> results,
            List<TemporalMetricHistoryDto> metricHistories,
            List<IssueTrackDto> issueTracks,
            List<RemediationDurabilityDto> durabilityList,
            DiagnosticMonitoringPolicy policy) {

        Map<HealthDimension, HealthDimensionEvaluationDto> map = new LinkedHashMap<>();
        for (HealthDimension dim : HealthDimension.values()) {
            map.put(dim, new HealthDimensionEvaluationDto(dim));
        }

        if (norm == null) {
            for (HealthDimension dim : HealthDimension.values()) {
                HealthDimensionEvaluationDto dto = map.get(dim);
                dto.setState(DimensionHealthState.UNKNOWN);
                dto.setEvaluable(false);
                dto.setReason("No normalized diagnostic execution data available for run.");
            }
            return map;
        }

        Set<DiagnosticModule> availableModules = norm.getAvailableModules();
        Map<DiagnosticModule, Long> resIdMap = norm.getModuleResultIds();

        // 1. DATA_QUALITY
        evaluateDataQuality(map.get(HealthDimension.DATA_QUALITY), norm, availableModules, resIdMap.get(DiagnosticModule.DATA_QUALITY));

        // 2. LEAKAGE
        evaluateLeakage(map.get(HealthDimension.LEAKAGE), norm, availableModules, resIdMap.get(DiagnosticModule.LEAKAGE));

        // 3. DRIFT
        evaluateDrift(map.get(HealthDimension.DRIFT), norm, availableModules, resIdMap.get(DiagnosticModule.DRIFT));

        // 4. PERFORMANCE
        evaluatePerformance(map.get(HealthDimension.PERFORMANCE), norm, availableModules, resIdMap.get(DiagnosticModule.PERFORMANCE));

        // 5. CALIBRATION
        evaluateCalibration(map.get(HealthDimension.CALIBRATION), norm, availableModules, resIdMap.get(DiagnosticModule.PERFORMANCE));

        // 6. ERROR (ERROR_FORENSICS)
        evaluateError(map.get(HealthDimension.ERROR), norm, availableModules, resIdMap.get(DiagnosticModule.ERROR_FORENSICS));

        // 7. FAIRNESS (BIAS)
        evaluateFairness(map.get(HealthDimension.FAIRNESS), norm, availableModules, resIdMap.get(DiagnosticModule.BIAS));

        // 8. ROBUSTNESS
        evaluateRobustness(map.get(HealthDimension.ROBUSTNESS), norm, availableModules, resIdMap.get(DiagnosticModule.ROBUSTNESS));

        // 9. TEMPORAL
        evaluateTemporal(map.get(HealthDimension.TEMPORAL), metricHistories, issueTracks, durabilityList);

        return map;
    }

    private void evaluateDataQuality(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                     Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.DATA_QUALITY)) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("DATA_QUALITY module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        for (NormalizedModuleData.FeatureQualityData fq : norm.getQualityByFeature().values()) {
            String targetKey = "FEATURE::" + fq.feature;
            if (fq.nullRate > 0.0) {
                String sev = fq.nullRate >= 0.20 ? "CRITICAL" : (fq.nullRate >= 0.05 ? "WARNING" : "NOMINAL");
                evidence.add(new MetricEvidenceItemDto("null_rate", targetKey, fq.nullRate, 0.05, sev, "rate",
                        String.format("Feature %s null rate = %.2f%%", fq.feature, fq.nullRate * 100), resId));
                worst = elevate(worst, sev);
            }
            if (fq.outlierRate > 0.0) {
                String sev = fq.outlierRate >= 0.10 ? "WARNING" : "NOMINAL";
                evidence.add(new MetricEvidenceItemDto("outlier_rate", targetKey, fq.outlierRate, 0.10, sev, "rate",
                        String.format("Feature %s outlier rate = %.2f%%", fq.feature, fq.outlierRate * 100), resId));
                worst = elevate(worst, sev);
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "All evaluated feature distributions satisfy completeness and schema integrity thresholds."
                : String.format("Observed data quality anomalies in %d feature metrics.", evidence.size()));
    }

    private void evaluateLeakage(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                 Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.LEAKAGE)) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("LEAKAGE module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        for (NormalizedModuleData.FeatureLeakageData fl : norm.getLeakageByFeature().values()) {
            String targetKey = "FEATURE::" + fl.feature;
            if (fl.leakageScore > 0.30) {
                String sev = fl.leakageScore >= 0.80 ? "CRITICAL" : (fl.leakageScore >= 0.50 ? "WARNING" : "NOMINAL");
                evidence.add(new MetricEvidenceItemDto("leakage_score", targetKey, fl.leakageScore, 0.50, sev, "score",
                        String.format("Feature %s exhibits high target leakage association (score=%.3f)", fl.feature, fl.leakageScore), resId));
                worst = elevate(worst, sev);
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "No features exhibit severe target leakage or spurious predictive shortcuts."
                : String.format("Detected potential data leakage in %d features.", evidence.size()));
    }

    private void evaluateDrift(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                               Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.DRIFT)) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("DRIFT module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        if (norm.getDriftSummary() != null) {
            double maxPsi = norm.getDriftSummary().maxPsi;
            String sev = maxPsi >= 0.25 ? "CRITICAL" : (maxPsi >= 0.10 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("max_psi", "GLOBAL", maxPsi, 0.20, sev, "psi",
                    String.format("Global maximum feature PSI = %.3f", maxPsi), resId));
            worst = elevate(worst, sev);
        }

        for (NormalizedModuleData.FeatureDriftData fd : norm.getDriftByFeature().values()) {
            String targetKey = "FEATURE::" + fd.feature;
            if (fd.psi >= 0.10) {
                String sev = fd.psi >= 0.25 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("psi", targetKey, fd.psi, 0.20, sev, "psi",
                        String.format("Feature %s distribution shift PSI = %.3f (Wasserstein=%.4f)", fd.feature, fd.psi, fd.wasserstein), resId));
                worst = elevate(worst, sev);
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Feature distributions remain within expected baseline reference bounds."
                : String.format("Detected significant distribution drift across %d features.", evidence.size()));
    }

    private void evaluatePerformance(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                     Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.PERFORMANCE) || norm.getPerformanceSummary() == null) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("PERFORMANCE module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;
        NormalizedModuleData.PerformanceSummary p = norm.getPerformanceSummary();

        // F1 evaluation (higher is better)
        if (p.f1 > 0.0) {
            String sev = p.f1 < 0.60 ? "CRITICAL" : (p.f1 < 0.75 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("f1", "GLOBAL", p.f1, 0.75, sev, "score",
                    String.format("Model F1 score = %.3f", p.f1), resId));
            worst = elevate(worst, sev);
        }
        if (p.rocAuc > 0.0) {
            String sev = p.rocAuc < 0.65 ? "CRITICAL" : (p.rocAuc < 0.80 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("roc_auc", "GLOBAL", p.rocAuc, 0.80, sev, "auc",
                    String.format("Model ROC AUC = %.3f", p.rocAuc), resId));
            worst = elevate(worst, sev);
        }
        if (p.logLoss > 0.0) {
            String sev = p.logLoss > 0.80 ? "CRITICAL" : (p.logLoss > 0.50 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("log_loss", "GLOBAL", p.logLoss, 0.50, sev, "loss",
                    String.format("Model Log Loss = %.3f", p.logLoss), resId));
            worst = elevate(worst, sev);
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Core discriminative performance metrics meet operational thresholds."
                : String.format("Performance degradation observed (F1=%.3f, ROC_AUC=%.3f).", p.f1, p.rocAuc));
    }

    private void evaluateCalibration(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                     Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.PERFORMANCE) || norm.getPerformanceSummary() == null) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("Calibration metrics unavailable.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;
        NormalizedModuleData.PerformanceSummary p = norm.getPerformanceSummary();

        if (p.expectedCalibrationError > 0.0) {
            String sev = p.expectedCalibrationError > 0.15 ? "CRITICAL" : (p.expectedCalibrationError > 0.08 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("expected_calibration_error", "GLOBAL", p.expectedCalibrationError, 0.08, sev, "ece",
                    String.format("Expected Calibration Error (ECE) = %.3f", p.expectedCalibrationError), resId));
            worst = elevate(worst, sev);
        }
        if (p.brierScore > 0.0) {
            String sev = p.brierScore > 0.25 ? "WARNING" : "NOMINAL";
            evidence.add(new MetricEvidenceItemDto("brier_score", "GLOBAL", p.brierScore, 0.20, sev, "score",
                    String.format("Brier probability score = %.3f", p.brierScore), resId));
            worst = elevate(worst, sev);
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Prediction probabilities are well-calibrated against observed empirical outcomes."
                : "Probability calibration deviation detected.");
    }

    private void evaluateError(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                              Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.ERROR_FORENSICS)) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("ERROR_FORENSICS module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        if (norm.getErrorForensicsSummary() != null) {
            NormalizedModuleData.ErrorForensicsSummary ef = norm.getErrorForensicsSummary();
            if (ef.highConfidenceErrorRate > 0.05) {
                String sev = ef.highConfidenceErrorRate >= 0.15 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("high_confidence_error_rate", "GLOBAL", ef.highConfidenceErrorRate, 0.05, sev, "rate",
                        String.format("High-confidence error rate = %.2f%%", ef.highConfidenceErrorRate * 100), resId));
                worst = elevate(worst, sev);
            }
            if (ef.overallErrorRate > 0.25) {
                String sev = ef.overallErrorRate >= 0.40 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("overall_error_rate", "GLOBAL", ef.overallErrorRate, 0.25, sev, "rate",
                        String.format("Overall diagnostic error rate = %.2f%%", ef.overallErrorRate * 100), resId));
                worst = elevate(worst, sev);
            }
        }

        for (NormalizedModuleData.FeatureErrorData fe : norm.getErrorByFeature().values()) {
            if (fe.absoluteAssociation >= 0.25) {
                String targetKey = "FEATURE::" + fe.feature;
                String sev = fe.absoluteAssociation >= 0.45 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("error_association", targetKey, fe.absoluteAssociation, 0.25, sev, "r",
                        String.format("Feature %s exhibits strong error correlation (r=%.3f)", fe.feature, fe.absoluteAssociation), resId));
                worst = elevate(worst, sev);
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Error distributions are uniformly distributed without high-confidence clustering."
                : String.format("Identified %d localized error concentration areas.", evidence.size()));
    }

    private void evaluateFairness(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                 Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.BIAS) || norm.getBiasSummary() == null) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("FAIRNESS (BIAS) module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;
        NormalizedModuleData.BiasSummary bs = norm.getBiasSummary();

        if (bs.worstDisparateImpactRatio > 0.0) {
            double dir = bs.worstDisparateImpactRatio;
            String sev = dir < 0.65 ? "CRITICAL" : (dir < 0.80 ? "WARNING" : "NOMINAL");
            evidence.add(new MetricEvidenceItemDto("worst_disparate_impact_ratio", "GLOBAL", dir, 0.80, sev, "ratio",
                    String.format("Worst Disparate Impact Ratio = %.3f (80%% rule boundary)", dir), resId));
            worst = elevate(worst, sev);
        }
        if (bs.demographicParityGap > 0.15) {
            String sev = bs.demographicParityGap >= 0.30 ? "CRITICAL" : "WARNING";
            evidence.add(new MetricEvidenceItemDto("demographic_parity_gap", "GLOBAL", bs.demographicParityGap, 0.15, sev, "gap",
                    String.format("Demographic Parity Gap = %.3f", bs.demographicParityGap), resId));
            worst = elevate(worst, sev);
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Protected attribute parity and disparate impact ratios satisfy compliance tolerances."
                : "Protected subgroup disparity exceeds configured threshold limits.");
    }

    private void evaluateRobustness(HealthDimensionEvaluationDto dto, NormalizedModuleData norm,
                                   Set<DiagnosticModule> available, Long resId) {
        if (!available.contains(DiagnosticModule.ROBUSTNESS)) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("ROBUSTNESS module was not executed in this diagnostic run.");
            return;
        }

        dto.setEvaluable(true);
        dto.setSourceResultId(resId);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        if (norm.getRobustnessSummary() != null) {
            NormalizedModuleData.RobustnessSummary rs = norm.getRobustnessSummary();
            if (rs.gaussianJitter5PctFlipRate > 0.05) {
                String sev = rs.gaussianJitter5PctFlipRate >= 0.15 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("gaussian_jitter_5pct_flip_rate", "GLOBAL", rs.gaussianJitter5PctFlipRate, 0.05, sev, "rate",
                        String.format("Gaussian jitter (5%%) prediction flip rate = %.2f%%", rs.gaussianJitter5PctFlipRate * 100), resId));
                worst = elevate(worst, sev);
            }
            if (rs.boundaryFlipRate > 0.10) {
                String sev = rs.boundaryFlipRate >= 0.25 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("boundary_flip_rate", "GLOBAL", rs.boundaryFlipRate, 0.10, sev, "rate",
                        String.format("Decision boundary flip rate = %.2f%%", rs.boundaryFlipRate * 100), resId));
                worst = elevate(worst, sev);
            }
        }

        for (NormalizedModuleData.FeatureRobustnessData fr : norm.getRobustnessByFeature().values()) {
            if (fr.flipRate >= 0.10) {
                String targetKey = "FEATURE::" + fr.feature;
                String sev = fr.flipRate >= 0.20 ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("flip_rate", targetKey, fr.flipRate, 0.10, sev, "rate",
                        String.format("Feature %s stress flip rate = %.2f%%", fr.feature, fr.flipRate * 100), resId));
                worst = elevate(worst, sev);
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Model decision boundary exhibits high perturbation stability under adversarial stress."
                : String.format("Observed decision sensitivity to perturbation across %d features.", evidence.size()));
    }

    private void evaluateTemporal(HealthDimensionEvaluationDto dto,
                                 List<TemporalMetricHistoryDto> metricHistories,
                                 List<IssueTrackDto> issueTracks,
                                 List<RemediationDurabilityDto> durabilityList) {
        if (metricHistories == null || metricHistories.isEmpty()) {
            dto.setState(DimensionHealthState.UNKNOWN);
            dto.setEvaluable(false);
            dto.setReason("Insufficient longitudinal history to compute temporal stability.");
            return;
        }

        dto.setEvaluable(true);
        List<MetricEvidenceItemDto> evidence = new ArrayList<>();
        DimensionHealthState worst = DimensionHealthState.HEALTHY;

        // Check degrading metric trends
        for (TemporalMetricHistoryDto mh : metricHistories) {
            if ("DEGRADING".equalsIgnoreCase(mh.getTrendDirection())) {
                String sev = "CRITICAL".equalsIgnoreCase(mh.getCurrentSeverity()) ? "CRITICAL" : "WARNING";
                evidence.add(new MetricEvidenceItemDto("trend_slope", mh.getTargetKey() != null ? mh.getTargetKey() : "GLOBAL",
                        mh.getSlope() != null ? mh.getSlope() : 0.0, 0.0, sev, "slope",
                        String.format("Longitudinal degrading trend detected for %s (%s)", mh.getMetricName(), mh.getTargetKey()), null));
                worst = elevate(worst, sev);
            }
        }

        // Check persistent or escalating issue tracks
        if (issueTracks != null) {
            for (IssueTrackDto it : issueTracks) {
                if ("PERSISTENT".equalsIgnoreCase(it.getStatus()) || "ESCALATING".equalsIgnoreCase(it.getStatus())) {
                    String sev = "CRITICAL".equalsIgnoreCase(it.getCurrentSeverity()) ? "CRITICAL" : "WARNING";
                    evidence.add(new MetricEvidenceItemDto("issue_persistence", it.getTargetKey(),
                            (double) it.getConsecutiveCount(), 2.0, sev, "runs",
                            String.format("Persistent issue track '%s' active for %d consecutive baseline runs (severity=%s)",
                                    it.getTargetKey(), it.getConsecutiveCount(), it.getCurrentSeverity()), null));
                    worst = elevate(worst, sev);
                }
            }
        }

        // Check durability failures
        if (durabilityList != null) {
            for (RemediationDurabilityDto dur : durabilityList) {
                if ("FAILED_TO_SUSTAIN".equalsIgnoreCase(dur.getDurabilityStatus()) || "TEMPORARY".equalsIgnoreCase(dur.getDurabilityStatus())) {
                    evidence.add(new MetricEvidenceItemDto("remediation_durability", dur.getTargetKey(),
                            0.0, 1.0, "WARNING", "durability",
                            String.format("Remediation for %s failed to sustain gains in operational follow-up runs.",
                                    dur.getTargetKey()), null));
                    worst = elevate(worst, "WARNING");
                }
            }
        }

        dto.setState(worst);
        dto.setMetricEvidence(evidence);
        dto.setSummary(worst == DimensionHealthState.HEALTHY
                ? "Longitudinal trajectories demonstrate stable or improving behavior without persistent regressions."
                : String.format("Longitudinal tracking detected %d active temporal concerns.", evidence.size()));
    }

    private DimensionHealthState elevate(DimensionHealthState current, String severity) {
        DimensionHealthState target = switch (severity.toUpperCase()) {
            case "CRITICAL" -> DimensionHealthState.CRITICAL;
            case "WARNING", "HIGH" -> DimensionHealthState.DEGRADED;
            case "MEDIUM" -> DimensionHealthState.WARNING;
            default -> DimensionHealthState.HEALTHY;
        };

        if (current == DimensionHealthState.CRITICAL || target == DimensionHealthState.CRITICAL) {
            return DimensionHealthState.CRITICAL;
        }
        if (current == DimensionHealthState.DEGRADED || target == DimensionHealthState.DEGRADED) {
            return DimensionHealthState.DEGRADED;
        }
        if (current == DimensionHealthState.WARNING || target == DimensionHealthState.WARNING) {
            return DimensionHealthState.WARNING;
        }
        return DimensionHealthState.HEALTHY;
    }
}
