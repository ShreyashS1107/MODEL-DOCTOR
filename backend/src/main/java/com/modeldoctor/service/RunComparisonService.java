package com.modeldoctor.service;

import com.modeldoctor.domain.*;
import com.modeldoctor.dto.DiagnosticComparisonDto;
import com.modeldoctor.dto.MetricComparisonDto;
import com.modeldoctor.exception.ResourceNotFoundException;
import com.modeldoctor.intelligence.normalization.NormalizedModuleData;
import com.modeldoctor.intelligence.normalization.ResultNormalizer;
import com.modeldoctor.repository.DiagnosticResultRepository;
import com.modeldoctor.repository.DiagnosticRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RunComparisonService {

    private static final Logger logger = LoggerFactory.getLogger(RunComparisonService.class);

    private final DiagnosticRunRepository runRepository;
    private final DiagnosticResultRepository resultRepository;
    private final ResultNormalizer normalizer;

    public RunComparisonService(
            DiagnosticRunRepository runRepository,
            DiagnosticResultRepository resultRepository,
            ResultNormalizer normalizer) {
        this.runRepository = runRepository;
        this.resultRepository = resultRepository;
        this.normalizer = normalizer;
    }

    @Transactional(readOnly = true)
    public DiagnosticComparisonDto compareRuns(String baselineRunId, String candidateRunId) {
        DiagnosticRun baselineRun = runRepository.findById(baselineRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Baseline diagnostic run not found: " + baselineRunId));

        DiagnosticRun candidateRun = runRepository.findById(candidateRunId)
                .orElseThrow(() -> new ResourceNotFoundException("Candidate diagnostic run not found: " + candidateRunId));

        List<DiagnosticResult> baseResults = resultRepository.findByRunIdOrderByIdAsc(baselineRunId);
        List<DiagnosticResult> candResults = resultRepository.findByRunIdOrderByIdAsc(candidateRunId);

        NormalizedModuleData baseNorm = normalizer.normalize(baselineRunId, baseResults);
        NormalizedModuleData candNorm = normalizer.normalize(candidateRunId, candResults);

        List<MetricComparisonDto> comparisons = new ArrayList<>();

        // 1. Performance / Classification Metrics
        if (baseNorm.getPerformanceSummary() != null && candNorm.getPerformanceSummary() != null) {
            var b = baseNorm.getPerformanceSummary();
            var c = candNorm.getPerformanceSummary();

            addMetric(comparisons, "ROC-AUC", "PERFORMANCE", b.rocAuc, c.rocAuc, true, "Model discrimination ranking ability");
            addMetric(comparisons, "PR-AUC", "PERFORMANCE", b.prAuc, c.prAuc, true, "Precision-recall area under curve");
            addMetric(comparisons, "F1 Score", "PERFORMANCE", b.f1, c.f1, true, "Harmonic mean of precision and recall");
            addMetric(comparisons, "Precision", "PERFORMANCE", b.precision, c.precision, true, "Positive predictive value");
            addMetric(comparisons, "Recall", "PERFORMANCE", b.recall, c.recall, true, "Sensitivity / True positive rate");
            addMetric(comparisons, "Log Loss", "PERFORMANCE", b.logLoss, c.logLoss, false, "Cross-entropy loss");
            addMetric(comparisons, "Brier Score", "PERFORMANCE", b.brierScore, c.brierScore, false, "Mean squared probability error");
            addMetric(comparisons, "Expected Calibration Error", "PERFORMANCE", b.expectedCalibrationError, c.expectedCalibrationError, false, "Expected confidence calibration error (ECE)");
            addMetric(comparisons, "False Positive Rate", "PERFORMANCE", b.falsePositiveRate, c.falsePositiveRate, false, "Type I error rate");
            addMetric(comparisons, "False Negative Rate", "PERFORMANCE", b.falseNegativeRate, c.falseNegativeRate, false, "Type II error rate");
        }

        // 2. Error Forensics Metrics
        if (baseNorm.getErrorForensicsSummary() != null && candNorm.getErrorForensicsSummary() != null) {
            var b = baseNorm.getErrorForensicsSummary();
            var c = candNorm.getErrorForensicsSummary();

            addMetric(comparisons, "Error Rate", "ERROR_FORENSICS", b.overallErrorRate, c.overallErrorRate, false, "Empirical misclassification rate");
            addMetric(comparisons, "False Positive Count", "ERROR_FORENSICS", (double) b.falsePositiveCount, (double) c.falsePositiveCount, false, "Total false positive predictions");
            addMetric(comparisons, "False Negative Count", "ERROR_FORENSICS", (double) b.falseNegativeCount, (double) c.falseNegativeCount, false, "Total false negative predictions");
            addMetric(comparisons, "High Confidence Error Rate", "ERROR_FORENSICS", b.highConfidenceErrorRate, c.highConfidenceErrorRate, false, "Percentage of incorrect predictions with high confidence");
            addMetric(comparisons, "High Confidence Error Count", "ERROR_FORENSICS", (double) b.highConfidenceErrorCount, (double) c.highConfidenceErrorCount, false, "Total high confidence mistakes");
        }

        // 3. Drift Metrics
        double baseMaxPsi = baseNorm.getDriftByFeature().values().stream().mapToDouble(d -> d.psi).max().orElse(-1.0);
        double candMaxPsi = candNorm.getDriftByFeature().values().stream().mapToDouble(d -> d.psi).max().orElse(-1.0);
        if (baseMaxPsi >= 0 && candMaxPsi >= 0) {
            addMetric(comparisons, "Max Feature PSI", "DRIFT", baseMaxPsi, candMaxPsi, false, "Maximum observed Population Stability Index");
        }

        // 4. Leakage Metrics
        double baseMaxMi = baseNorm.getLeakageByFeature().values().stream().mapToDouble(l -> l.leakageScore).max().orElse(-1.0);
        double candMaxMi = candNorm.getLeakageByFeature().values().stream().mapToDouble(l -> l.leakageScore).max().orElse(-1.0);
        if (baseMaxMi >= 0 && candMaxMi >= 0) {
            addMetric(comparisons, "Max Feature Leakage Score", "LEAKAGE", baseMaxMi, candMaxMi, false, "Maximum target leakage score");
        }

        // 5. Fairness Metrics
        if (baseNorm.getBiasSummary() != null && candNorm.getBiasSummary() != null) {
            var b = baseNorm.getBiasSummary();
            var c = candNorm.getBiasSummary();
            addMetric(comparisons, "Disparate Impact Ratio", "BIAS", b.worstDisparateImpactRatio, c.worstDisparateImpactRatio, true, "Subgroup selection rate parity ratio (target >= 0.80)");
            addMetric(comparisons, "Equal Opportunity Gap", "BIAS", b.equalOpportunityGap, c.equalOpportunityGap, false, "True positive rate gap across demographic groups");
            addMetric(comparisons, "Demographic Parity Gap", "BIAS", b.demographicParityGap, c.demographicParityGap, false, "Selection rate gap across demographic groups");
        }

        // 6. Robustness Metrics
        if (baseNorm.getRobustnessSummary() != null && candNorm.getRobustnessSummary() != null) {
            var b = baseNorm.getRobustnessSummary();
            var c = candNorm.getRobustnessSummary();
            addMetric(comparisons, "Gaussian Jitter Flip Rate", "ROBUSTNESS", b.gaussianJitter5PctFlipRate, c.gaussianJitter5PctFlipRate, false, "Prediction label flip rate under 5% input jitter");
            addMetric(comparisons, "Boundary Flip Rate", "ROBUSTNESS", b.boundaryFlipRate, c.boundaryFlipRate, false, "Boundary decision shift rate");
        }

        // Aggregate assessment
        List<String> improved = new ArrayList<>();
        List<String> regressed = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        // Detect missing modules between runs
        if (baseNorm.getPerformanceSummary() != null && candNorm.getPerformanceSummary() == null) {
            missing.add("PERFORMANCE::ROC-AUC");
            missing.add("PERFORMANCE::F1 Score");
            missing.add("PERFORMANCE::Expected Calibration Error");
        } else if (baseNorm.getPerformanceSummary() == null && candNorm.getPerformanceSummary() != null) {
            missing.add("PERFORMANCE::ROC-AUC (Missing in Baseline)");
        }

        if (baseNorm.getErrorForensicsSummary() != null && candNorm.getErrorForensicsSummary() == null) {
            missing.add("ERROR_FORENSICS::Error Rate");
            missing.add("ERROR_FORENSICS::High Confidence Error Rate");
        } else if (baseNorm.getErrorForensicsSummary() == null && candNorm.getErrorForensicsSummary() != null) {
            missing.add("ERROR_FORENSICS::Error Rate (Missing in Baseline)");
        }

        if (baseNorm.getBiasSummary() != null && candNorm.getBiasSummary() == null) {
            missing.add("BIAS::Disparate Impact Ratio");
        } else if (baseNorm.getBiasSummary() == null && candNorm.getBiasSummary() != null) {
            missing.add("BIAS::Disparate Impact Ratio (Missing in Baseline)");
        }

        if (baseNorm.getRobustnessSummary() != null && candNorm.getRobustnessSummary() == null) {
            missing.add("ROBUSTNESS::Gaussian Jitter Flip Rate");
        } else if (baseNorm.getRobustnessSummary() == null && candNorm.getRobustnessSummary() != null) {
            missing.add("ROBUSTNESS::Gaussian Jitter Flip Rate (Missing in Baseline)");
        }

        if (!baseNorm.getDriftByFeature().isEmpty() && candNorm.getDriftByFeature().isEmpty()) {
            missing.add("DRIFT::Max Feature PSI");
        } else if (baseNorm.getDriftByFeature().isEmpty() && !candNorm.getDriftByFeature().isEmpty()) {
            missing.add("DRIFT::Max Feature PSI (Missing in Baseline)");
        }

        if (!baseNorm.getLeakageByFeature().isEmpty() && candNorm.getLeakageByFeature().isEmpty()) {
            missing.add("LEAKAGE::Max Feature Leakage Score");
        } else if (baseNorm.getLeakageByFeature().isEmpty() && !candNorm.getLeakageByFeature().isEmpty()) {
            missing.add("LEAKAGE::Max Feature Leakage Score (Missing in Baseline)");
        }

        Map<String, List<MetricComparisonDto>> moduleBreakdown = new LinkedHashMap<>();

        for (MetricComparisonDto mc : comparisons) {
            moduleBreakdown.computeIfAbsent(mc.getModule(), k -> new ArrayList<>()).add(mc);

            if ("IMPROVED".equals(mc.getDirection())) {
                improved.add(mc.getMetricName());
            } else if ("REGRESSED".equals(mc.getDirection())) {
                regressed.add(mc.getMetricName());
            } else if ("NO_MATERIAL_CHANGE".equals(mc.getDirection())) {
                unchanged.add(mc.getMetricName());
            } else {
                missing.add(mc.getMetricName());
            }
        }

        String overallAssessment;
        String rationale;

        if (comparisons.isEmpty()) {
            overallAssessment = "INSUFFICIENT_EVIDENCE";
            rationale = "No comparable diagnostic modules exist between baseline and candidate runs.";
        } else if (regressed.isEmpty() && !improved.isEmpty()) {
            overallAssessment = "IMPROVED";
            rationale = String.format("Candidate run demonstrates net positive improvements across %d metrics with zero observed regressions.", improved.size());
        } else if (improved.isEmpty() && !regressed.isEmpty()) {
            overallAssessment = "REGRESSED";
            rationale = String.format("Candidate run exhibits regressions across %d metrics without corresponding performance gains.", regressed.size());
        } else if (!improved.isEmpty() && !regressed.isEmpty()) {
            overallAssessment = "MIXED";
            rationale = String.format("Candidate run presents mixed tradeoffs: %d metric(s) improved, but %d metric(s) regressed.", improved.size(), regressed.size());
        } else {
            overallAssessment = "NO_MATERIAL_CHANGE";
            rationale = "All evaluated metrics remain within standard noise margins (delta < 0.005) between runs.";
        }

        DiagnosticComparisonDto dto = new DiagnosticComparisonDto();
        dto.setBaselineRunId(baselineRunId);
        dto.setCandidateRunId(candidateRunId);
        dto.setBaselineModelName(baselineRun.getModelName() != null ? baselineRun.getModelName() : "Baseline Model");
        dto.setCandidateModelName(candidateRun.getModelName() != null ? candidateRun.getModelName() : "Candidate Model");
        dto.setBaselineDatasetName(baselineRun.getEvaluationDataset() != null ? baselineRun.getEvaluationDataset() : "Baseline Dataset");
        dto.setCandidateDatasetName(candidateRun.getEvaluationDataset() != null ? candidateRun.getEvaluationDataset() : "Candidate Dataset");

        dto.setOverallAssessment(overallAssessment);
        dto.setAssessmentRationale(rationale);
        dto.setImprovedMetricCount(improved.size());
        dto.setRegressedMetricCount(regressed.size());
        dto.setUnchangedMetricCount(unchanged.size());
        dto.setMissingMetricCount(missing.size());

        dto.setMetricComparisons(comparisons);
        dto.setImprovedMetrics(improved);
        dto.setRegressedMetrics(regressed);
        dto.setUnchangedMetrics(unchanged);
        dto.setMissingMetrics(missing);
        dto.setModuleBreakdown(moduleBreakdown);

        dto.setValidationSummary(String.format("Compared %d metrics across %d modules. %d Improved, %d Regressed, %d Unchanged.",
                comparisons.size(), moduleBreakdown.size(), improved.size(), regressed.size(), unchanged.size()));

        return dto;
    }

    private void addMetric(
            List<MetricComparisonDto> comparisons,
            String metricName,
            String module,
            Double baseVal,
            Double candVal,
            boolean isHigherBetter,
            String rationale) {

        if (baseVal == null || candVal == null) {
            comparisons.add(new MetricComparisonDto(
                    metricName, module, baseVal, candVal, null, null,
                    isHigherBetter, "INSUFFICIENT_EVIDENCE", "MISSING", "Metric not available in both runs"
            ));
            return;
        }

        double delta = candVal - baseVal;
        double pct = baseVal != 0.0 ? (delta / Math.abs(baseVal)) * 100.0 : 0.0;

        double noiseThreshold = 0.005;
        String direction;
        String assessment;

        if (Math.abs(delta) < noiseThreshold) {
            direction = "NO_MATERIAL_CHANGE";
            assessment = "UNCHANGED";
        } else if (isHigherBetter) {
            direction = delta > 0 ? "IMPROVED" : "REGRESSED";
            assessment = delta > 0 ? "IMPROVED" : "REGRESSED";
        } else {
            direction = delta < 0 ? "IMPROVED" : "REGRESSED";
            assessment = delta < 0 ? "IMPROVED" : "REGRESSED";
        }

        comparisons.add(new MetricComparisonDto(
                metricName,
                module,
                round(baseVal, 4),
                round(candVal, 4),
                round(delta, 4),
                round(pct, 2),
                isHigherBetter,
                direction,
                assessment,
                rationale
        ));
    }

    private double round(double val, int decimals) {
        double factor = Math.pow(10, decimals);
        return Math.round(val * factor) / factor;
    }
}
