package com.modeldoctor.intelligence.temporal;

import com.modeldoctor.domain.DiagnosticModule;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@SuppressWarnings("null")
public class TemporalMetricRegistry {

    public static class MetricDefinition {
        private final String metricName;
        private final DiagnosticModule module;
        private final boolean higherIsBetter;
        private final Double warningThreshold;
        private final Double criticalThreshold;
        private final String unit;
        private final int minSampleSize;
        private final boolean trendEligible;

        public MetricDefinition(String metricName, DiagnosticModule module, boolean higherIsBetter,
                                Double warningThreshold, Double criticalThreshold, String unit,
                                int minSampleSize, boolean trendEligible) {
            this.metricName = metricName;
            this.module = module;
            this.higherIsBetter = higherIsBetter;
            this.warningThreshold = warningThreshold;
            this.criticalThreshold = criticalThreshold;
            this.unit = unit;
            this.minSampleSize = minSampleSize;
            this.trendEligible = trendEligible;
        }

        public String getMetricName() { return metricName; }
        public DiagnosticModule getModule() { return module; }
        public boolean isHigherIsBetter() { return higherIsBetter; }
        public Double getWarningThreshold() { return warningThreshold; }
        public Double getCriticalThreshold() { return criticalThreshold; }
        public String getUnit() { return unit; }
        public int getMinSampleSize() { return minSampleSize; }
        public boolean isTrendEligible() { return trendEligible; }
    }

    private final Map<String, MetricDefinition> registry = new LinkedHashMap<>();

    public TemporalMetricRegistry() {
        registerPerformanceMetrics();
        registerDriftMetrics();
        registerErrorForensicsMetrics();
        registerFairnessMetrics();
        registerRobustnessMetrics();
        registerExplainabilityMetrics();
        registerDataQualityMetrics();
        registerLeakageMetrics();
    }

    private void register(MetricDefinition def) {
        registry.put(def.getMetricName().toLowerCase(), def);
    }

    private void registerPerformanceMetrics() {
        register(new MetricDefinition("f1", DiagnosticModule.PERFORMANCE, true, 0.70, 0.50, "score", 3, true));
        register(new MetricDefinition("roc_auc", DiagnosticModule.PERFORMANCE, true, 0.75, 0.60, "auc", 3, true));
        register(new MetricDefinition("pr_auc", DiagnosticModule.PERFORMANCE, true, 0.60, 0.40, "auc", 3, true));
        register(new MetricDefinition("accuracy", DiagnosticModule.PERFORMANCE, true, 0.75, 0.60, "score", 3, true));
        register(new MetricDefinition("precision", DiagnosticModule.PERFORMANCE, true, 0.70, 0.50, "score", 3, true));
        register(new MetricDefinition("recall", DiagnosticModule.PERFORMANCE, true, 0.70, 0.50, "score", 3, true));
        register(new MetricDefinition("log_loss", DiagnosticModule.PERFORMANCE, false, 0.50, 0.80, "loss", 3, true));
        register(new MetricDefinition("brier_score", DiagnosticModule.PERFORMANCE, false, 0.15, 0.25, "score", 3, true));
        register(new MetricDefinition("expected_calibration_error", DiagnosticModule.PERFORMANCE, false, 0.08, 0.15, "ece", 3, true));
        register(new MetricDefinition("false_positive_rate", DiagnosticModule.PERFORMANCE, false, 0.15, 0.30, "rate", 3, true));
        register(new MetricDefinition("false_negative_rate", DiagnosticModule.PERFORMANCE, false, 0.15, 0.30, "rate", 3, true));
    }

    private void registerDriftMetrics() {
        register(new MetricDefinition("psi", DiagnosticModule.DRIFT, false, 0.10, 0.25, "psi", 3, true));
        register(new MetricDefinition("max_psi", DiagnosticModule.DRIFT, false, 0.10, 0.25, "psi", 3, true));
        register(new MetricDefinition("ks_statistic", DiagnosticModule.DRIFT, false, 0.15, 0.30, "stat", 3, true));
        register(new MetricDefinition("wasserstein", DiagnosticModule.DRIFT, false, 0.20, 0.40, "dist", 3, true));
        register(new MetricDefinition("drifted_feature_count", DiagnosticModule.DRIFT, false, 1.0, 3.0, "count", 3, true));
    }

    private void registerErrorForensicsMetrics() {
        register(new MetricDefinition("high_confidence_error_rate", DiagnosticModule.ERROR_FORENSICS, false, 0.05, 0.15, "rate", 3, true));
        register(new MetricDefinition("overall_error_rate", DiagnosticModule.ERROR_FORENSICS, false, 0.15, 0.30, "rate", 3, true));
        register(new MetricDefinition("error_association", DiagnosticModule.ERROR_FORENSICS, false, 0.15, 0.25, "r", 3, true));
        register(new MetricDefinition("subgroup_disparity_ratio", DiagnosticModule.ERROR_FORENSICS, false, 1.30, 1.60, "ratio", 3, true));
    }

    private void registerFairnessMetrics() {
        register(new MetricDefinition("disparate_impact", DiagnosticModule.BIAS, true, 0.80, 0.60, "ratio", 3, true));
        register(new MetricDefinition("worst_disparate_impact_ratio", DiagnosticModule.BIAS, true, 0.80, 0.60, "ratio", 3, true));
        register(new MetricDefinition("demographic_parity_gap", DiagnosticModule.BIAS, false, 0.10, 0.20, "gap", 3, true));
        register(new MetricDefinition("equal_opportunity_gap", DiagnosticModule.BIAS, false, 0.10, 0.20, "gap", 3, true));
    }

    private void registerRobustnessMetrics() {
        register(new MetricDefinition("flip_rate", DiagnosticModule.ROBUSTNESS, false, 0.10, 0.20, "rate", 3, true));
        register(new MetricDefinition("gaussian_jitter_5pct_flip_rate", DiagnosticModule.ROBUSTNESS, false, 0.10, 0.20, "rate", 3, true));
        register(new MetricDefinition("boundary_flip_rate", DiagnosticModule.ROBUSTNESS, false, 0.10, 0.20, "rate", 3, true));
        register(new MetricDefinition("mean_probability_shift", DiagnosticModule.ROBUSTNESS, false, 0.05, 0.10, "shift", 3, true));
    }

    private void registerExplainabilityMetrics() {
        register(new MetricDefinition("top1_attribution_share", DiagnosticModule.EXPLAINABILITY, false, 0.50, 0.70, "share", 3, true));
        register(new MetricDefinition("top3_attribution_share", DiagnosticModule.EXPLAINABILITY, false, 0.80, 0.95, "share", 3, true));
    }

    private void registerDataQualityMetrics() {
        register(new MetricDefinition("null_rate", DiagnosticModule.DATA_QUALITY, false, 0.05, 0.15, "rate", 3, true));
        register(new MetricDefinition("outlier_rate", DiagnosticModule.DATA_QUALITY, false, 0.05, 0.15, "rate", 3, true));
    }

    private void registerLeakageMetrics() {
        register(new MetricDefinition("leakage_score", DiagnosticModule.LEAKAGE, false, 0.50, 0.70, "score", 3, true));
        register(new MetricDefinition("mutual_info", DiagnosticModule.LEAKAGE, false, 0.50, 0.70, "mi", 3, true));
    }

    public Optional<MetricDefinition> getDefinition(String metricName) {
        if (metricName == null) return Optional.empty();
        return Optional.ofNullable(registry.get(metricName.toLowerCase()));
    }

    public boolean isHigherIsBetter(String metricName) {
        Optional<MetricDefinition> def = getDefinition(metricName);
        return def.map(MetricDefinition::isHigherIsBetter).orElse(true);
    }

    public String computeSeverity(String metricName, double value) {
        Optional<MetricDefinition> defOpt = getDefinition(metricName);
        if (defOpt.isEmpty() || defOpt.get().getWarningThreshold() == null) {
            return "LOW";
        }

        MetricDefinition def = defOpt.get();
        double warn = def.getWarningThreshold();
        Double crit = def.getCriticalThreshold();

        if (def.isHigherIsBetter()) {
            // Lower values are worse
            if (crit != null && value <= crit) return "CRITICAL";
            if (value <= warn) return "HIGH";
            return "LOW";
        } else {
            // Higher values are worse
            if (crit != null && value >= crit) return "CRITICAL";
            if (value >= warn) return "HIGH";
            return "LOW";
        }
    }

    public Collection<MetricDefinition> getAllDefinitions() {
        return registry.values();
    }
}
