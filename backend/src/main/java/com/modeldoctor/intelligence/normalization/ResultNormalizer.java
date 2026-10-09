package com.modeldoctor.intelligence.normalization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.modeldoctor.domain.DiagnosticResult;
import com.modeldoctor.domain.ModuleExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Component
public class ResultNormalizer {

    private static final Logger logger = LoggerFactory.getLogger(ResultNormalizer.class);
    private final ObjectMapper objectMapper;

    public ResultNormalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public NormalizedModuleData normalize(String runId, List<DiagnosticResult> results) {
        NormalizedModuleData norm = new NormalizedModuleData(runId);

        if (results == null || results.isEmpty()) {
            return norm;
        }

        for (DiagnosticResult res : results) {
            if (res.getStatus() != ModuleExecutionStatus.COMPLETED || res.getResultJson() == null || res.getResultJson().isBlank()) {
                continue;
            }

            norm.getAvailableModules().add(res.getModule());
            if (res.getId() != null) {
                norm.getModuleResultIds().put(res.getModule(), res.getId());
            }

            try {
                JsonNode root = objectMapper.readTree(res.getResultJson());
                switch (res.getModule()) {
                    case DATA_QUALITY -> normalizeDataQuality(root, norm);
                    case LEAKAGE -> normalizeLeakage(root, norm);
                    case DRIFT -> normalizeDrift(root, norm);
                    case PERFORMANCE -> normalizePerformance(root, norm);
                    case EXPLAINABILITY -> normalizeExplainability(root, norm);
                    case BIAS -> normalizeBias(root, norm);
                    case ROBUSTNESS -> normalizeRobustness(root, norm);
                    case ERROR_FORENSICS -> normalizeErrorForensics(root, norm);
                    case EXPERIMENTS -> {}
                }
            } catch (Exception e) {
                logger.warn("Failed to normalize raw result for run {} module {}: {}", runId, res.getModule(), e.getMessage());
            }
        }

        return norm;
    }

    private void normalizeDataQuality(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.DataQualitySummary sum = new NormalizedModuleData.DataQualitySummary();
        JsonNode summaryNode = root.path("summary");
        sum.nullCount = summaryNode.path("nullCount").asLong(0);
        sum.constantColumnCount = summaryNode.path("constantColumnCount").asInt(0);
        sum.duplicateRowCount = summaryNode.path("duplicateRowCount").asInt(0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setQualitySummary(sum);

        JsonNode columns = root.path("columns");
        if (columns.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = columns.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String col = entry.getKey();
                JsonNode c = entry.getValue();
                double nullRate = c.path("nullRate").asDouble(0.0);
                double outlierRate = c.path("outlierRate").asDouble(0.0);
                boolean isConstant = c.path("constant").asBoolean(false) || c.path("isConstant").asBoolean(false);
                long distinctCount = c.path("distinctCount").asLong(0);
                norm.getQualityByFeature().put(col, new NormalizedModuleData.FeatureQualityData(col, nullRate, outlierRate, isConstant, distinctCount));
            }
        }
    }

    private void normalizeLeakage(JsonNode root, NormalizedModuleData norm) {
        JsonNode features = root.path("features");
        if (features.isArray()) {
            for (JsonNode f : features) {
                String name = f.path("feature").asText(f.path("name").asText(""));
                if (name.isBlank()) continue;
                double mi = f.path("mutualInfo").asDouble(f.path("mutualInformation").asDouble(0.0));
                double corr = f.path("pearsonCorrelation").asDouble(f.path("correlation").asDouble(0.0));
                double score = f.path("leakageScore").asDouble(0.0);
                boolean isSuspicious = f.path("isSuspicious").asBoolean(false) || score >= 0.70;
                norm.getLeakageByFeature().put(name, new NormalizedModuleData.FeatureLeakageData(name, mi, corr, score, isSuspicious));
            }
        } else if (features.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = features.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode f = entry.getValue();
                double mi = f.path("mutualInfo").asDouble(0.0);
                double corr = f.path("pearsonCorrelation").asDouble(f.path("correlation").asDouble(0.0));
                double score = f.path("leakageScore").asDouble(0.0);
                boolean isSuspicious = f.path("isSuspicious").asBoolean(false);
                norm.getLeakageByFeature().put(name, new NormalizedModuleData.FeatureLeakageData(name, mi, corr, score, isSuspicious));
            }
        }
    }

    private void normalizeDrift(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.DriftSummary sum = new NormalizedModuleData.DriftSummary();
        JsonNode summaryNode = root.path("summary");
        sum.maxPsi = summaryNode.path("maxPsi").asDouble(0.0);
        sum.driftedFeatureCount = summaryNode.path("driftedFeatureCount").asInt(0);
        sum.meanWasserstein = summaryNode.path("meanWasserstein").asDouble(0.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setDriftSummary(sum);

        JsonNode features = root.has("features") ? root.path("features") : root.path("columns");
        if (features.isArray()) {
            for (JsonNode f : features) {
                String name = f.path("feature").asText(f.path("name").asText(""));
                if (name.isBlank()) continue;
                double psi = f.path("psi").asDouble(0.0);
                double ks = f.path("ksPValue").asDouble(1.0);
                double wasserstein = f.path("wasserstein").asDouble(0.0);
                boolean detected = f.path("driftDetected").asBoolean(psi >= 0.10 || ks < 0.05);
                String severity = f.path("severity").asText(psi >= 0.25 ? "HIGH" : psi >= 0.10 ? "MEDIUM" : "LOW");
                norm.getDriftByFeature().put(name, new NormalizedModuleData.FeatureDriftData(name, psi, ks, wasserstein, detected, severity));
            }
        } else if (features.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = features.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode f = entry.getValue();
                double psi = f.path("psi").asDouble(0.0);
                double ks = f.path("ksPValue").asDouble(1.0);
                double wasserstein = f.path("wasserstein").asDouble(0.0);
                boolean detected = f.path("driftDetected").asBoolean(psi >= 0.10 || ks < 0.05);
                String severity = f.path("driftSeverity").asText(f.path("severity").asText("LOW"));
                norm.getDriftByFeature().put(name, new NormalizedModuleData.FeatureDriftData(name, psi, ks, wasserstein, detected, severity));
            }
        }
    }

    private void normalizePerformance(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.PerformanceSummary sum = new NormalizedModuleData.PerformanceSummary();
        JsonNode summaryNode = root.path("summary");
        sum.rocAuc = summaryNode.path("rocAuc").asDouble(0.5);
        sum.prAuc = summaryNode.path("prAuc").asDouble(0.0);
        sum.f1 = summaryNode.path("f1").asDouble(0.0);
        sum.precision = summaryNode.path("precision").asDouble(0.0);
        sum.recall = summaryNode.path("recall").asDouble(0.0);
        sum.falseNegativeRate = summaryNode.path("falseNegativeRate").asDouble(0.0);
        sum.falsePositiveRate = summaryNode.path("falsePositiveRate").asDouble(0.0);
        sum.expectedCalibrationError = summaryNode.path("expectedCalibrationError").asDouble(0.0);
        sum.logLoss = summaryNode.path("logLoss").asDouble(0.0);
        sum.brierScore = summaryNode.path("brierScore").asDouble(0.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setPerformanceSummary(sum);
    }

    private void normalizeExplainability(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.ExplainabilitySummary sum = new NormalizedModuleData.ExplainabilitySummary();
        JsonNode summaryNode = root.path("summary");
        sum.topFeature = summaryNode.path("topFeature").asText("");
        sum.topFeatureMeanAbsShap = summaryNode.path("topFeatureImportance").asDouble(summaryNode.path("topFeatureMeanAbsShap").asDouble(0.0));
        sum.top1AttributionShare = summaryNode.path("top1AttributionShare").asDouble(0.0);
        sum.top3AttributionShare = summaryNode.path("top3AttributionShare").asDouble(0.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setExplainabilitySummary(sum);

        JsonNode globalImp = root.has("globalImportance")
                ? root.path("globalImportance")
                : (root.has("attributions") ? root.path("attributions") : root.path("features"));
        if (globalImp.isArray()) {
            int rankCounter = 1;
            for (JsonNode f : globalImp) {
                String name = f.path("feature").asText("");
                if (name.isBlank()) continue;
                int rank = f.path("rank").asInt(rankCounter);
                double meanAbsShap = f.has("meanAbsoluteShap")
                        ? f.path("meanAbsoluteShap").asDouble(0.0)
                        : f.path("meanAbsShap").asDouble(f.path("importance").asDouble(0.0));
                double share = f.path("attributionShare").asDouble(0.0);
                norm.getImportanceByFeature().put(name, new NormalizedModuleData.FeatureImportanceData(name, rank, meanAbsShap, share));
                rankCounter++;
            }
        }
    }

    private void normalizeBias(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.BiasSummary sum = new NormalizedModuleData.BiasSummary();
        JsonNode summaryNode = root.path("summary");
        sum.protectedAttribute = summaryNode.path("protectedAttribute").asText("");
        sum.groupCount = summaryNode.path("groupCount").asInt(0);
        sum.demographicParityGap = summaryNode.path("demographicParityGap").asDouble(0.0);
        sum.worstDisparateImpactRatio = summaryNode.has("disparateImpactRatio")
                ? summaryNode.path("disparateImpactRatio").asDouble(1.0)
                : summaryNode.path("worstDisparateImpactRatio").asDouble(1.0);
        sum.equalOpportunityGap = summaryNode.path("equalOpportunityGap").asDouble(0.0);
        sum.worstCalibrationGap = summaryNode.path("worstCalibrationGap").asDouble(0.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setBiasSummary(sum);
    }

    private void normalizeRobustness(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.RobustnessSummary sum = new NormalizedModuleData.RobustnessSummary();
        JsonNode summaryNode = root.path("summary");
        sum.topSensitiveFeature = summaryNode.has("mostSensitiveFeature")
                ? summaryNode.path("mostSensitiveFeature").asText("")
                : summaryNode.path("topSensitiveFeature").asText("");
        sum.gaussianJitter5PctFlipRate = summaryNode.path("gaussianJitter5PctFlipRate").asDouble(0.0);
        sum.boundaryFlipRate = summaryNode.path("boundaryFlipRate").asDouble(0.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);
        norm.setRobustnessSummary(sum);

        JsonNode featureSensitivity = root.path("featureSensitivity");
        JsonNode features = featureSensitivity.has("features")
                ? featureSensitivity.path("features")
                : (root.has("featureSensitivities") ? root.path("featureSensitivities") : root.path("features"));
        if (features.isArray()) {
            int rankCounter = 1;
            for (JsonNode f : features) {
                String name = f.path("feature").asText("");
                if (name.isBlank()) continue;
                int rank = f.path("sensitivityRank").asInt(rankCounter);
                double flip = f.has("predictionFlipRate")
                        ? f.path("predictionFlipRate").asDouble(0.0)
                        : f.path("flipRate").asDouble(f.path("perturbationFlipRate").asDouble(0.0));
                double shift = f.path("meanProbabilityShift").asDouble(0.0);
                norm.getRobustnessByFeature().put(name, new NormalizedModuleData.FeatureRobustnessData(name, rank, flip, shift));
                rankCounter++;
            }
        }
    }

    private void normalizeErrorForensics(JsonNode root, NormalizedModuleData norm) {
        NormalizedModuleData.ErrorForensicsSummary sum = new NormalizedModuleData.ErrorForensicsSummary();
        JsonNode summaryNode = root.path("summary");
        JsonNode errSummary = root.path("errorSummary");
        JsonNode confAnalysis = root.path("confidenceAnalysis");

        sum.totalErrors = errSummary.has("totalErrors")
                ? errSummary.path("totalErrors").asLong(0)
                : summaryNode.path("totalErrors").asLong(0);
        sum.overallErrorRate = errSummary.has("errorRate")
                ? errSummary.path("errorRate").asDouble(0.0)
                : summaryNode.path("overallErrorRate").asDouble(0.0);

        sum.highConfidenceErrorCount = confAnalysis.has("highConfidenceErrorCount")
                ? confAnalysis.path("highConfidenceErrorCount").asLong(0)
                : summaryNode.path("highConfidenceErrorCount").asLong(0);
        sum.highConfidenceErrorRate = confAnalysis.has("highConfidenceErrorRate")
                ? confAnalysis.path("highConfidenceErrorRate").asDouble(0.0)
                : summaryNode.path("highConfidenceErrorRate").asDouble(0.0);
        sum.highConfidenceErrorShare = confAnalysis.path("highConfidenceErrorShare").asDouble(0.0);

        sum.falsePositiveCount = errSummary.path("falsePositive").has("count")
                ? errSummary.path("falsePositive").path("count").asLong(0)
                : summaryNode.path("fpCount").asLong(0);
        sum.falseNegativeCount = errSummary.path("falseNegative").has("count")
                ? errSummary.path("falseNegative").path("count").asLong(0)
                : summaryNode.path("fnCount").asLong(0);

        sum.falsePositiveRate = errSummary.path("falsePositive").path("rate")
                .asDouble(errSummary.path("falsePositive").path("falsePositiveRate").asDouble(0.0));
        sum.falseNegativeRate = errSummary.path("falseNegative").path("rate")
                .asDouble(errSummary.path("falseNegative").path("falseNegativeRate").asDouble(0.0));

        sum.topErrorFeature = summaryNode.path("topErrorAssociatedFeature").asText("");
        sum.worstSubgroupDisparityRatio = summaryNode.path("worstSubgroupDisparityRatio").asDouble(1.0);
        sum.healthScore = summaryNode.path("healthScore").asDouble(100.0);
        sum.passed = summaryNode.path("passed").asBoolean(true);

        JsonNode calForensics = root.path("calibrationForensics");
        sum.expectedCalibrationError = calForensics.path("expectedCalibrationError").asDouble(0.0);
        JsonNode calBins = calForensics.path("bins");
        if (calBins.isArray()) {
            for (JsonNode binNode : calBins) {
                if (binNode.path("isHighError").asBoolean(false) || binNode.path("isSevereGap").asBoolean(false)) {
                    sum.severeCalibrationBins.add(binNode.path("binIndex").asInt());
                }
            }
        }

        JsonNode subgroupList = root.path("subgroupAnalysis");
        if (subgroupList.isArray() && subgroupList.size() > 0) {
            double maxRate = -1.0;
            double minRate = Double.MAX_VALUE;
            double maxDisp = 1.0;
            for (JsonNode sg : subgroupList) {
                String name = sg.has("group") ? sg.path("group").asText("") : sg.path("subgroup").asText("");
                double rate = sg.path("errorRate").asDouble(0.0);
                double disp = sg.path("disparityRatio").asDouble(1.0);
                if (disp > maxDisp) maxDisp = disp;
                if (!name.isBlank()) {
                    sum.subgroupErrorRates.put(name, rate);
                    if (rate > maxRate) maxRate = rate;
                    if (rate > 0 && rate < minRate) minRate = rate;
                }
            }
            if (sum.worstSubgroupDisparityRatio <= 1.0 && maxDisp > 1.0) {
                sum.worstSubgroupDisparityRatio = maxDisp;
            } else if (sum.worstSubgroupDisparityRatio <= 1.0 && maxRate > 0 && minRate > 0 && minRate < Double.MAX_VALUE) {
                sum.worstSubgroupDisparityRatio = maxRate / minRate;
            }
        }

        norm.setErrorForensicsSummary(sum);

        // Feature associations & separation
        JsonNode assocs = root.path("featureAssociations");
        if (assocs.isArray()) {
            for (JsonNode a : assocs) {
                String feat = a.path("feature").asText("");
                if (feat.isBlank()) continue;
                String type = a.path("featureType").asText(a.path("type").asText("numeric"));
                double corr = a.has("statistic") ? a.path("statistic").asDouble(0.0) : a.path("value").asDouble(0.0);
                double absAssoc = a.has("effectSize")
                        ? Math.abs(a.path("effectSize").asDouble(0.0))
                        : a.path("absoluteAssociation").asDouble(Math.abs(corr));
                double pVal = a.has("adjustedPValue")
                        ? a.path("adjustedPValue").asDouble(1.0)
                        : a.path("rawPValue").asDouble(1.0);
                boolean enriched = absAssoc >= 0.15 && pVal < 0.05;
                norm.getErrorByFeature().put(feat, new NormalizedModuleData.FeatureErrorData(feat, type, corr, absAssoc, pVal, 0.0, 0.0, enriched));
            }
        }

        // FP feature separation
        JsonNode fpDiffs = root.path("falsePositiveAnalysis").has("topSeparations")
                ? root.path("falsePositiveAnalysis").path("topSeparations")
                : root.path("falsePositiveAnalysis").path("topFeatureDifferences");
        if (fpDiffs.isArray()) {
            for (JsonNode fp : fpDiffs) {
                String feat = fp.path("feature").asText("");
                if (feat.isBlank()) continue;
                double sep = fp.has("standardizedMeanDifference")
                        ? fp.path("standardizedMeanDifference").asDouble(0.0)
                        : fp.path("absoluteSeparation").asDouble(0.0);
                NormalizedModuleData.FeatureErrorData existing = norm.getErrorByFeature().get(feat);
                if (existing != null) {
                    existing.fpSeparation = sep;
                } else {
                    norm.getErrorByFeature().put(feat, new NormalizedModuleData.FeatureErrorData(feat, fp.path("type").asText("numeric"), 0.0, 0.0, 1.0, sep, 0.0, false));
                }
            }
        }

        // FN feature separation
        JsonNode fnDiffs = root.path("falseNegativeAnalysis").has("topSeparations")
                ? root.path("falseNegativeAnalysis").path("topSeparations")
                : root.path("falseNegativeAnalysis").path("topFeatureDifferences");
        if (fnDiffs.isArray()) {
            for (JsonNode fn : fnDiffs) {
                String feat = fn.path("feature").asText("");
                if (feat.isBlank()) continue;
                double sep = fn.has("standardizedMeanDifference")
                        ? fn.path("standardizedMeanDifference").asDouble(0.0)
                        : fn.path("absoluteSeparation").asDouble(0.0);
                NormalizedModuleData.FeatureErrorData existing = norm.getErrorByFeature().get(feat);
                if (existing != null) {
                    existing.fnSeparation = sep;
                } else {
                    norm.getErrorByFeature().put(feat, new NormalizedModuleData.FeatureErrorData(feat, fn.path("type").asText("numeric"), 0.0, 0.0, 1.0, 0.0, sep, false));
                }
            }
        }
    }
}
