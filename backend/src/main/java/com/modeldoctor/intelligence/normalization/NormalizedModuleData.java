package com.modeldoctor.intelligence.normalization;

import com.modeldoctor.domain.DiagnosticModule;

import java.util.*;

public class NormalizedModuleData {
    private String runId;
    private final Map<DiagnosticModule, Long> moduleResultIds = new EnumMap<>(DiagnosticModule.class);
    private final Set<DiagnosticModule> availableModules = new HashSet<>();

    // Per-feature normalized containers
    private final Map<String, FeatureDriftData> driftByFeature = new HashMap<>();
    private final Map<String, FeatureImportanceData> importanceByFeature = new HashMap<>();
    private final Map<String, FeatureRobustnessData> robustnessByFeature = new HashMap<>();
    private final Map<String, FeatureLeakageData> leakageByFeature = new HashMap<>();
    private final Map<String, FeatureQualityData> qualityByFeature = new HashMap<>();
    private final Map<String, FeatureErrorData> errorByFeature = new HashMap<>();

    // Global module summaries
    private PerformanceSummary performanceSummary;
    private BiasSummary biasSummary;
    private DriftSummary driftSummary;
    private DataQualitySummary qualitySummary;
    private ExplainabilitySummary explainabilitySummary;
    private RobustnessSummary robustnessSummary;
    private ErrorForensicsSummary errorForensicsSummary;

    public NormalizedModuleData(String runId) {
        this.runId = runId;
    }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public Map<DiagnosticModule, Long> getModuleResultIds() { return moduleResultIds; }
    public Set<DiagnosticModule> getAvailableModules() { return availableModules; }

    public Map<String, FeatureDriftData> getDriftByFeature() { return driftByFeature; }
    public Map<String, FeatureImportanceData> getImportanceByFeature() { return importanceByFeature; }
    public Map<String, FeatureRobustnessData> getRobustnessByFeature() { return robustnessByFeature; }
    public Map<String, FeatureLeakageData> getLeakageByFeature() { return leakageByFeature; }
    public Map<String, FeatureQualityData> getQualityByFeature() { return qualityByFeature; }
    public Map<String, FeatureErrorData> getErrorByFeature() { return errorByFeature; }

    public Set<String> getAllKnownFeatures() {
        Set<String> all = new LinkedHashSet<>();
        all.addAll(driftByFeature.keySet());
        all.addAll(importanceByFeature.keySet());
        all.addAll(robustnessByFeature.keySet());
        all.addAll(leakageByFeature.keySet());
        all.addAll(qualityByFeature.keySet());
        all.addAll(errorByFeature.keySet());
        return all;
    }

    public PerformanceSummary getPerformanceSummary() { return performanceSummary; }
    public void setPerformanceSummary(PerformanceSummary performanceSummary) { this.performanceSummary = performanceSummary; }

    public BiasSummary getBiasSummary() { return biasSummary; }
    public void setBiasSummary(BiasSummary biasSummary) { this.biasSummary = biasSummary; }

    public DriftSummary getDriftSummary() { return driftSummary; }
    public void setDriftSummary(DriftSummary driftSummary) { this.driftSummary = driftSummary; }

    public DataQualitySummary getQualitySummary() { return qualitySummary; }
    public void setQualitySummary(DataQualitySummary qualitySummary) { this.qualitySummary = qualitySummary; }

    public ExplainabilitySummary getExplainabilitySummary() { return explainabilitySummary; }
    public void setExplainabilitySummary(ExplainabilitySummary explainabilitySummary) { this.explainabilitySummary = explainabilitySummary; }

    public RobustnessSummary getRobustnessSummary() { return robustnessSummary; }
    public void setRobustnessSummary(RobustnessSummary robustnessSummary) { this.robustnessSummary = robustnessSummary; }

    public ErrorForensicsSummary getErrorForensicsSummary() { return errorForensicsSummary; }
    public void setErrorForensicsSummary(ErrorForensicsSummary errorForensicsSummary) { this.errorForensicsSummary = errorForensicsSummary; }

    // --- Inner Data Classes ---

    public static class FeatureDriftData {
        public String feature;
        public double psi;
        public double ksPValue;
        public double wasserstein;
        public boolean driftDetected;
        public String severity;

        public FeatureDriftData(String feature, double psi, double ksPValue, double wasserstein, boolean driftDetected, String severity) {
            this.feature = feature;
            this.psi = psi;
            this.ksPValue = ksPValue;
            this.wasserstein = wasserstein;
            this.driftDetected = driftDetected;
            this.severity = severity;
        }
    }

    public static class FeatureImportanceData {
        public String feature;
        public int rank;
        public double meanAbsShap;
        public double attributionShare;

        public FeatureImportanceData(String feature, int rank, double meanAbsShap, double attributionShare) {
            this.feature = feature;
            this.rank = rank;
            this.meanAbsShap = meanAbsShap;
            this.attributionShare = attributionShare;
        }
    }

    public static class FeatureRobustnessData {
        public String feature;
        public int sensitivityRank;
        public double flipRate;
        public double meanProbabilityShift;

        public FeatureRobustnessData(String feature, int sensitivityRank, double flipRate, double meanProbabilityShift) {
            this.feature = feature;
            this.sensitivityRank = sensitivityRank;
            this.flipRate = flipRate;
            this.meanProbabilityShift = meanProbabilityShift;
        }
    }

    public static class FeatureLeakageData {
        public String feature;
        public double mutualInfo;
        public double correlation;
        public double leakageScore;
        public boolean isSuspicious;

        public FeatureLeakageData(String feature, double mutualInfo, double correlation, double leakageScore, boolean isSuspicious) {
            this.feature = feature;
            this.mutualInfo = mutualInfo;
            this.correlation = correlation;
            this.leakageScore = leakageScore;
            this.isSuspicious = isSuspicious;
        }
    }

    public static class FeatureQualityData {
        public String feature;
        public double nullRate;
        public double outlierRate;
        public boolean isConstant;
        public long distinctCount;

        public FeatureQualityData(String feature, double nullRate, double outlierRate, boolean isConstant, long distinctCount) {
            this.feature = feature;
            this.nullRate = nullRate;
            this.outlierRate = outlierRate;
            this.isConstant = isConstant;
            this.distinctCount = distinctCount;
        }
    }

    public static class FeatureErrorData {
        public String feature;
        public String type;
        public double correlation;
        public double absoluteAssociation;
        public double adjustedPValue;
        public double fpSeparation;
        public double fnSeparation;
        public boolean isErrorEnriched;

        public FeatureErrorData(String feature, String type, double correlation, double absoluteAssociation, double adjustedPValue, double fpSeparation, double fnSeparation, boolean isErrorEnriched) {
            this.feature = feature;
            this.type = type;
            this.correlation = correlation;
            this.absoluteAssociation = absoluteAssociation;
            this.adjustedPValue = adjustedPValue;
            this.fpSeparation = fpSeparation;
            this.fnSeparation = fnSeparation;
            this.isErrorEnriched = isErrorEnriched;
        }
    }

    public static class PerformanceSummary {
        public double rocAuc;
        public double prAuc;
        public double f1;
        public double precision;
        public double recall;
        public double falseNegativeRate;
        public double falsePositiveRate;
        public double expectedCalibrationError;
        public double logLoss;
        public double brierScore;
        public double healthScore;
        public boolean passed;
    }

    public static class BiasSummary {
        public String protectedAttribute;
        public int groupCount;
        public double demographicParityGap;
        public double worstDisparateImpactRatio;
        public double equalOpportunityGap;
        public double worstCalibrationGap;
        public double healthScore;
        public boolean passed;
    }

    public static class DriftSummary {
        public double maxPsi;
        public int driftedFeatureCount;
        public double meanWasserstein;
        public double healthScore;
        public boolean passed;
    }

    public static class DataQualitySummary {
        public long nullCount;
        public int constantColumnCount;
        public int duplicateRowCount;
        public double healthScore;
        public boolean passed;
    }

    public static class ExplainabilitySummary {
        public String topFeature;
        public double topFeatureMeanAbsShap;
        public double top1AttributionShare;
        public double top3AttributionShare;
        public double healthScore;
        public boolean passed;
    }

    public static class RobustnessSummary {
        public String topSensitiveFeature;
        public double gaussianJitter5PctFlipRate;
        public double boundaryFlipRate;
        public double healthScore;
        public boolean passed;
    }

    public static class ErrorForensicsSummary {
        public long totalErrors;
        public double overallErrorRate;
        public long highConfidenceErrorCount;
        public double highConfidenceErrorRate;
        public double highConfidenceErrorShare;
        public long falsePositiveCount;
        public double falsePositiveRate;
        public long falseNegativeCount;
        public double falseNegativeRate;
        public double expectedCalibrationError;
        public String topErrorFeature;
        public double worstSubgroupDisparityRatio;
        public double healthScore;
        public boolean passed;
        public Map<String, Double> subgroupErrorRates = new HashMap<>();
        public Set<Integer> severeCalibrationBins = new HashSet<>();
    }
}
