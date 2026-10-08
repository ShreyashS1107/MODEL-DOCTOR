"use client";

import React, { useState } from "react";
import {
  ErrorForensicsResult,
  HighConfidenceErrorRecordData,
  FeatureAssociationData,
  FeatureRangeData,
  ThresholdForensicPoint,
  SubgroupErrorData,
  CalibrationForensicBinData,
} from "@/lib/api";

export interface ErrorForensicsViewProps {
  result?: ErrorForensicsResult | Record<string, any> | null;
  status?: string;
  statusMessage?: string;
}

export const ErrorForensicsView: React.FC<ErrorForensicsViewProps> = ({
  result,
  status,
  statusMessage,
}) => {
  const [selectedRecord, setSelectedRecord] = useState<HighConfidenceErrorRecordData | null>(null);
  const [selectedFeatureFilter, setSelectedFeatureFilter] = useState<string>("ALL");
  const [selectedThresholdVal, setSelectedThresholdVal] = useState<number>(0.5);
  const [selectedFinding, setSelectedFinding] = useState<any | null>(null);

  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">
              PERFORMANCE &amp; ERROR FORENSICS EVALUATION FAILED
            </span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine encountered a fatal execution failure during record-level error forensics."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Diagnostic execution terminated without producing valid error forensic metrics. Synthetic demo data is suppressed for failed live runs.
          </div>
        </div>
      </div>
    );
  }

  if (status === "NOT_IMPLEMENTED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#f59e0b] bg-[#16120b] text-[#fde047] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#f59e0b] text-black">DEFERRED</span>
            <span className="text-sm font-bold text-white uppercase">ERROR FORENSICS DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for execution in analytical pipeline."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = Boolean(result && (result as any).errorSummary);
  const data = result as ErrorForensicsResult;

  // Fallback demo fixture if viewing in static demo mode
  const errorSummary = isRealData
    ? data.errorSummary
    : {
        totalRecords: 50000,
        totalErrors: 1791,
        errorRate: 0.0358,
        truePositive: { count: 2055, rate: 0.0411, percentageOfAll: 4.11, percentageOfPositives: 68.5 },
        trueNegative: { count: 46154, rate: 0.9231, percentageOfAll: 92.31, percentageOfNegatives: 98.2 },
        falsePositive: { count: 846, rate: 0.0169, percentageOfAll: 1.69, percentageOfNegatives: 1.8 },
        falseNegative: { count: 945, rate: 0.0189, percentageOfAll: 1.89, percentageOfPositives: 31.5 },
      };

  const confAnalysis = isRealData
    ? data.confidenceAnalysis
    : {
        meanIncorrectConfidence: 0.742,
        medianIncorrectConfidence: 0.765,
        p90IncorrectConfidence: 0.912,
        p95IncorrectConfidence: 0.948,
        maxIncorrectConfidence: 0.985,
        highConfidenceErrorCount: 382,
        highConfidenceErrorRate: 0.213,
        highConfidenceErrorShare: 0.0076,
        confidenceBands: [
          { band: "[0.50, 0.60)", lowerBound: 0.5, upperBound: 0.6, totalCount: 2100, errorCount: 650, errorRate: 0.309 },
          { band: "[0.60, 0.75)", lowerBound: 0.6, upperBound: 0.75, totalCount: 5400, errorCount: 759, errorRate: 0.141 },
          { band: "[0.75, 0.90)", lowerBound: 0.75, upperBound: 0.9, totalCount: 18500, errorCount: 295, errorRate: 0.016 },
          { band: "[0.90, 1.00]", lowerBound: 0.9, upperBound: 1.0, totalCount: 24000, errorCount: 87, errorRate: 0.0036 },
        ],
      };

  const featureAssociations: FeatureAssociationData[] = isRealData && Array.isArray(data.featureAssociations)
    ? data.featureAssociations
    : [
        { feature: "transaction_amount", featureType: "numeric", statisticName: "point_biserial_r", statistic: 0.342, effectSize: 0.342, pValue: 0.0001, adjustedPValue: 0.0004, rank: 1, direction: "positive" },
        { feature: "num_failed_logins", featureType: "numeric", statisticName: "point_biserial_r", statistic: 0.285, effectSize: 0.285, pValue: 0.0002, adjustedPValue: 0.0006, rank: 2, direction: "positive" },
        { feature: "account_age_days", featureType: "numeric", statisticName: "point_biserial_r", statistic: -0.214, effectSize: 0.214, pValue: 0.001, adjustedPValue: 0.0025, rank: 3, direction: "negative" },
        { feature: "is_foreign_ip", featureType: "categorical", statisticName: "cramers_v", statistic: 0.298, effectSize: 0.298, pValue: 0.0001, adjustedPValue: 0.0004, rank: 4, direction: "positive" },
      ];

  const fpAnalysis = isRealData && data.falsePositiveAnalysis ? data.falsePositiveAnalysis : null;
  const fnAnalysis = isRealData && data.falseNegativeAnalysis ? data.falseNegativeAnalysis : null;

  const featureRanges: FeatureRangeData[] = isRealData && Array.isArray(data.featureRanges)
    ? data.featureRanges
    : [
        { feature: "transaction_amount", binIndex: 0, binLower: 0, binUpper: 50, recordCount: 10000, errorCount: 120, errorRate: 0.012, falsePositiveRate: 0.008, falseNegativeRate: 0.004, isErrorEnriched: false, isInsufficientSample: false },
        { feature: "transaction_amount", binIndex: 1, binLower: 50, binUpper: 150, recordCount: 10000, errorCount: 180, errorRate: 0.018, falsePositiveRate: 0.011, falseNegativeRate: 0.007, isErrorEnriched: false, isInsufficientSample: false },
        { feature: "transaction_amount", binIndex: 2, binLower: 150, binUpper: 500, recordCount: 10000, errorCount: 310, errorRate: 0.031, falsePositiveRate: 0.019, falseNegativeRate: 0.012, isErrorEnriched: false, isInsufficientSample: false },
        { feature: "transaction_amount", binIndex: 3, binLower: 500, binUpper: 2000, recordCount: 10000, errorCount: 520, errorRate: 0.052, falsePositiveRate: 0.032, falseNegativeRate: 0.020, isErrorEnriched: true, isInsufficientSample: false },
        { feature: "transaction_amount", binIndex: 4, binLower: 2000, binUpper: 15000, recordCount: 10000, errorCount: 661, errorRate: 0.066, falsePositiveRate: 0.041, falseNegativeRate: 0.025, isErrorEnriched: true, isInsufficientSample: false },
      ];

  const highConfErrors: HighConfidenceErrorRecordData[] = isRealData && Array.isArray(data.highConfidenceErrors)
    ? data.highConfidenceErrors
    : [
        { stableRowIndex: 402, actualClass: 0, predictedClass: 1, predictedProbability: 0.942, confidence: 0.942, errorType: "FALSE_POSITIVE", forensicPriority: 0.962, keyAssociatedFeatures: { transaction_amount: 8450.0, num_failed_logins: 4, is_foreign_ip: 1 } },
        { stableRowIndex: 1184, actualClass: 1, predictedClass: 0, predictedProbability: 0.035, confidence: 0.965, errorType: "FALSE_NEGATIVE", forensicPriority: 0.958, keyAssociatedFeatures: { transaction_amount: 45.0, num_failed_logins: 0, is_foreign_ip: 0 } },
        { stableRowIndex: 2891, actualClass: 0, predictedClass: 1, predictedProbability: 0.915, confidence: 0.915, errorType: "FALSE_POSITIVE", forensicPriority: 0.925, keyAssociatedFeatures: { transaction_amount: 6200.0, num_failed_logins: 3, is_foreign_ip: 1 } },
        { stableRowIndex: 3410, actualClass: 1, predictedClass: 0, predictedProbability: 0.078, confidence: 0.922, errorType: "FALSE_NEGATIVE", forensicPriority: 0.918, keyAssociatedFeatures: { transaction_amount: 110.0, num_failed_logins: 0, is_foreign_ip: 0 } },
      ];

  const thresholdGrid: ThresholdForensicPoint[] = isRealData && Array.isArray(data.thresholdAnalysis)
    ? data.thresholdAnalysis
    : [
        { threshold: 0.1, truePositive: 2880, trueNegative: 38500, falsePositive: 8500, falseNegative: 120, precision: 0.253, recall: 0.96, specificity: 0.819, f1: 0.4, falsePositiveRate: 0.181, falseNegativeRate: 0.04 },
        { threshold: 0.3, truePositive: 2520, trueNegative: 44200, falsePositive: 2800, falseNegative: 480, precision: 0.474, recall: 0.84, specificity: 0.94, f1: 0.606, falsePositiveRate: 0.06, falseNegativeRate: 0.16 },
        { threshold: 0.5, truePositive: 2055, trueNegative: 46154, falsePositive: 846, falseNegative: 945, precision: 0.708, recall: 0.685, specificity: 0.982, f1: 0.696, falsePositiveRate: 0.018, falseNegativeRate: 0.315 },
        { threshold: 0.7, truePositive: 1440, trueNegative: 46700, falsePositive: 300, falseNegative: 1560, precision: 0.828, recall: 0.48, specificity: 0.994, f1: 0.608, falsePositiveRate: 0.006, falseNegativeRate: 0.52 },
        { threshold: 0.9, truePositive: 540, trueNegative: 46950, falsePositive: 50, falseNegative: 2460, precision: 0.915, recall: 0.18, specificity: 0.999, f1: 0.301, falsePositiveRate: 0.001, falseNegativeRate: 0.82 },
      ];

  const subgroupAnalysis: SubgroupErrorData[] = isRealData && Array.isArray(data.subgroupAnalysis)
    ? data.subgroupAnalysis
    : [
        { group: "domestic", sampleCount: 42500, errorCount: 1220, errorRate: 0.0287, falsePositiveRate: 0.013, falseNegativeRate: 0.28, highConfidenceErrorRate: 0.004, wilsonCiLower: 0.0271, wilsonCiUpper: 0.0304, disparityRatio: 1.0 },
        { group: "foreign", sampleCount: 7500, errorCount: 571, errorRate: 0.0761, falsePositiveRate: 0.038, falseNegativeRate: 0.45, highConfidenceErrorRate: 0.028, wilsonCiLower: 0.0703, wilsonCiUpper: 0.0824, disparityRatio: 2.65 },
      ];

  const calForensics = isRealData && data.calibrationForensics ? data.calibrationForensics : null;
  const findings: any[] = isRealData && Array.isArray(data.findings) ? data.findings : [];

  // Filter ranges by feature
  const uniqueFeatures = Array.from(new Set(featureRanges.map((r) => r.feature)));
  const filteredRanges = selectedFeatureFilter === "ALL"
    ? featureRanges
    : featureRanges.filter((r) => r.feature === selectedFeatureFilter);

  const activeThresholdPoint = thresholdGrid.find(
    (t) => Math.abs(t.threshold - selectedThresholdVal) < 0.03
  ) || thresholdGrid[Math.floor(thresholdGrid.length / 2)];

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* 1. Header Banner & Strict Associative Phrasing Notice */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#94a3b8] uppercase font-bold">
              05 ERROR FORENSICS &amp; SEGMENT DISSECTION
            </span>
            {isRealData ? (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#10b981]/20 text-[#10b981] border border-[#10b981]/40">
                CANONICAL RECORD-LEVEL EVIDENCE
              </span>
            ) : (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#f59e0b]/20 text-[#f59e0b] border border-[#f59e0b]/40">
                SYNTHETIC FIXTURE
              </span>
            )}
            <span className="px-1.5 py-0.2 text-[9px] font-mono bg-[#3b82f6]/10 text-[#60a5fa] border border-[#3b82f6]/30">
              ASSOCIATIVE ONLY — NOT CAUSAL
            </span>
          </div>
          <div className="text-[11px] text-[#64748b] mt-0.5">
            Record-level error decomposition, false positive/negative feature separation, high-confidence error profiling, and threshold sensitivity.
          </div>
        </div>

        <div className="px-2.5 py-1 text-xs font-bold shrink-0 bg-[#141822] text-[#94a3b8] border border-[#1f2533]">
          TOTAL EVALUATED: <span className="text-white font-bold">{errorSummary.totalRecords.toLocaleString()}</span> RECORDS
        </div>
      </div>

      {/* 2. Technical Summary HUD Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-5 gap-2">
        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">OVERALL ERROR RATE</div>
          <div className={`text-xl font-bold tabular-nums mt-0.5 ${
            errorSummary.errorRate <= 0.05 ? "text-[#10b981]" : errorSummary.errorRate <= 0.15 ? "text-[#f59e0b]" : "text-[#ef4444]"
          }`}>
            {(errorSummary.errorRate * 100).toFixed(2)}%
          </div>
          <div className="text-[10px] text-[#64748b]">
            {errorSummary.totalErrors.toLocaleString()} incorrect predictions
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">FALSE POSITIVES (FP)</div>
          <div className="text-xl font-bold text-[#f59e0b] tabular-nums mt-0.5">
            {errorSummary.falsePositive.count.toLocaleString()}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Rate: {(errorSummary.falsePositive.rate * 100).toFixed(2)}% of dataset
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">FALSE NEGATIVES (FN)</div>
          <div className="text-xl font-bold text-[#ef4444] tabular-nums mt-0.5">
            {errorSummary.falseNegative.count.toLocaleString()}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Rate: {(errorSummary.falseNegative.rate * 100).toFixed(2)}% of dataset
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">HIGH-CONFIDENCE ERRORS</div>
          <div className={`text-xl font-bold tabular-nums mt-0.5 ${
            confAnalysis.highConfidenceErrorRate >= 0.20 ? "text-[#ef4444]" : confAnalysis.highConfidenceErrorRate >= 0.10 ? "text-[#f59e0b]" : "text-[#10b981]"
          }`}>
            {(confAnalysis.highConfidenceErrorRate * 100).toFixed(1)}%
          </div>
          <div className="text-[10px] text-[#64748b]">
            {confAnalysis.highConfidenceErrorCount} mistakes with conf &gt;= 0.75
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">TOP ERROR FEATURE</div>
          <div className="text-base font-bold text-[#38bdf8] truncate mt-0.5">
            {featureAssociations[0]?.feature || "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Stat: {featureAssociations[0]?.statistic?.toFixed(3) || "0.000"} ({featureAssociations[0]?.statisticName || "r_pb"})
          </div>
        </div>
      </div>

      {/* 3. Error Distribution & Confusion Breakdown */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Confusion Matrix Reconciliation */}
        <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
          <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
            <span className="text-xs font-bold text-white uppercase">
              1. RECORD ERROR CLASSIFICATION BREAKDOWN
            </span>
            <span className="text-[10px] text-[#64748b]">RECONCILED AGAINST PREDICTION LOG</span>
          </div>

          <div className="grid grid-cols-2 gap-2">
            <div className="p-2.5 bg-[#0f1d18] border border-[#10b981]/40 rounded-sm">
              <div className="flex justify-between text-[10px] text-[#10b981]">
                <span className="font-bold">TRUE POSITIVES (TP)</span>
                <span>{errorSummary.truePositive.percentageOfAll?.toFixed(2) || (errorSummary.truePositive.rate * 100).toFixed(2)}%</span>
              </div>
              <div className="text-lg font-bold text-white mt-1 tabular-nums">
                {errorSummary.truePositive.count.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#6ee7b7] mt-0.5">
                Actual: 1 | Predicted: 1
              </div>
            </div>

            <div className="p-2.5 bg-[#26160d] border border-[#f59e0b]/40 rounded-sm">
              <div className="flex justify-between text-[10px] text-[#f59e0b]">
                <span className="font-bold">FALSE POSITIVES (FP)</span>
                <span>{errorSummary.falsePositive.percentageOfAll?.toFixed(2) || (errorSummary.falsePositive.rate * 100).toFixed(2)}%</span>
              </div>
              <div className="text-lg font-bold text-[#fbbf24] mt-1 tabular-nums">
                {errorSummary.falsePositive.count.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#fcd34d] mt-0.5">
                Actual: 0 | Predicted: 1 (Type I Error)
              </div>
            </div>

            <div className="p-2.5 bg-[#260f12] border border-[#ef4444]/40 rounded-sm">
              <div className="flex justify-between text-[10px] text-[#ef4444]">
                <span className="font-bold">FALSE NEGATIVES (FN)</span>
                <span>{errorSummary.falseNegative.percentageOfAll?.toFixed(2) || (errorSummary.falseNegative.rate * 100).toFixed(2)}%</span>
              </div>
              <div className="text-lg font-bold text-[#f87171] mt-1 tabular-nums">
                {errorSummary.falseNegative.count.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#fca5a5] mt-0.5">
                Actual: 1 | Predicted: 0 (Type II Error)
              </div>
            </div>

            <div className="p-2.5 bg-[#0f1d18] border border-[#10b981]/40 rounded-sm">
              <div className="flex justify-between text-[10px] text-[#10b981]">
                <span className="font-bold">TRUE NEGATIVES (TN)</span>
                <span>{errorSummary.trueNegative.percentageOfAll?.toFixed(2) || (errorSummary.trueNegative.rate * 100).toFixed(2)}%</span>
              </div>
              <div className="text-lg font-bold text-white mt-1 tabular-nums">
                {errorSummary.trueNegative.count.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#6ee7b7] mt-0.5">
                Actual: 0 | Predicted: 0
              </div>
            </div>
          </div>
        </div>

        {/* Confidence vs Correctness Distribution */}
        <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
          <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
            <span className="text-xs font-bold text-white uppercase">
              2. CONFIDENCE ERROR CONCENTRATION
            </span>
            <span className="text-[10px] text-[#64748b]">CONFIDENCE vs INCORRECT PREDICTIONS</span>
          </div>

          <div className="grid grid-cols-3 gap-2 text-center">
            <div className="p-2 bg-[#12161f] border border-[#1f2533]">
              <div className="text-[9px] text-[#64748b]">MEDIAN ERR CONF</div>
              <div className="text-base font-bold text-white tabular-nums">
                {(confAnalysis.medianIncorrectConfidence * 100).toFixed(1)}%
              </div>
            </div>
            <div className="p-2 bg-[#12161f] border border-[#1f2533]">
              <div className="text-[9px] text-[#64748b]">P95 ERR CONF</div>
              <div className="text-base font-bold text-[#f59e0b] tabular-nums">
                {(confAnalysis.p95IncorrectConfidence * 100).toFixed(1)}%
              </div>
            </div>
            <div className="p-2 bg-[#12161f] border border-[#1f2533]">
              <div className="text-[9px] text-[#64748b]">MAX ERR CONF</div>
              <div className="text-base font-bold text-[#ef4444] tabular-nums">
                {(confAnalysis.maxIncorrectConfidence * 100).toFixed(1)}%
              </div>
            </div>
          </div>

          {/* Confidence Bands */}
          <div className="space-y-1.5 pt-1">
            <div className="text-[10px] text-[#94a3b8] font-bold">ERROR DENSITY BY PREDICTION CONFIDENCE BAND</div>
            {confAnalysis.confidenceBands?.map((b, idx) => (
              <div key={idx} className="space-y-0.5">
                <div className="flex justify-between text-[10px]">
                  <span className="text-white">{b.band}</span>
                  <span className="text-[#94a3b8]">
                    {b.errorCount.toLocaleString()} errors / {b.totalCount.toLocaleString()} ({((b.errorRate || 0) * 100).toFixed(1)}% err rate)
                  </span>
                </div>
                <div className="w-full h-1.5 bg-[#181d28] rounded-full overflow-hidden">
                  <div
                    className={`h-full ${
                      b.errorRate >= 0.25 ? "bg-[#ef4444]" : b.errorRate >= 0.10 ? "bg-[#f59e0b]" : "bg-[#3b82f6]"
                    }`}
                    style={{ width: `${Math.min(100, (b.errorRate || 0) * 100 * 2.5)}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>

      {/* 4. Error-Associated Feature Matrix */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-2">
        <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
          <div>
            <span className="text-xs font-bold text-white uppercase">
              3. ERROR-ASSOCIATED FEATURE MATRIX
            </span>
            <span className="text-[10px] text-[#64748b] ml-2">
              POINT-BISERIAL r_pb &amp; CRAMER&apos;S V WITH BENJAMINI-HOCHBERG FDR CORRECTION
            </span>
          </div>
          <span className="text-[10px] text-[#64748b]">
            {featureAssociations.length} FEATURES RANKED
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-[11px]">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b]">
                <th className="py-1.5 px-2">RANK</th>
                <th className="py-1.5 px-2">FEATURE</th>
                <th className="py-1.5 px-2">TYPE</th>
                <th className="py-1.5 px-2">STATISTIC</th>
                <th className="py-1.5 px-2">EFFECT SIZE</th>
                <th className="py-1.5 px-2">DIRECTION</th>
                <th className="py-1.5 px-2">ADJ P-VALUE (FDR)</th>
                <th className="py-1.5 px-2">STATUS</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#161b26]">
              {featureAssociations.map((fa, i) => (
                <tr key={i} className="hover:bg-[#12161f] transition-colors">
                  <td className="py-1.5 px-2 text-[#64748b] font-bold">#{fa.rank || i + 1}</td>
                  <td className="py-1.5 px-2 text-white font-bold">{fa.feature}</td>
                  <td className="py-1.5 px-2 text-[#94a3b8] uppercase">{fa.featureType}</td>
                  <td className="py-1.5 px-2 tabular-nums">
                    <span className="text-white font-bold">{fa.statistic?.toFixed(4)}</span>
                    <span className="text-[#64748b] text-[9px] ml-1">({fa.statisticName})</span>
                  </td>
                  <td className="py-1.5 px-2 tabular-nums text-white">
                    {fa.effectSize?.toFixed(4)}
                  </td>
                  <td className="py-1.5 px-2">
                    <span className={`px-1.5 py-0.2 text-[9px] rounded ${
                      fa.direction === "positive" ? "bg-[#f59e0b]/20 text-[#fbbf24]" : "bg-[#3b82f6]/20 text-[#60a5fa]"
                    }`}>
                      {fa.direction || "N/A"}
                    </span>
                  </td>
                  <td className="py-1.5 px-2 tabular-nums">
                    <span className={fa.adjustedPValue < 0.01 ? "text-[#10b981] font-bold" : "text-[#94a3b8]"}>
                      {fa.adjustedPValue < 0.0001 ? "< 0.0001" : fa.adjustedPValue?.toFixed(4)}
                    </span>
                  </td>
                  <td className="py-1.5 px-2">
                    {fa.effectSize >= 0.20 && fa.adjustedPValue < 0.05 ? (
                      <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444]/20 text-[#f87171] border border-[#ef4444]/40">
                        ERROR-ENRICHED
                      </span>
                    ) : (
                      <span className="px-1.5 py-0.5 text-[9px] text-[#94a3b8] bg-[#1a202c]">
                        MODERATE
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 5. Quantile Feature Range Error Forensics */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#1f2533] pb-2">
          <div>
            <span className="text-xs font-bold text-white uppercase">
              4. QUANTILE FEATURE RANGE ERROR CONCENTRATION
            </span>
            <span className="text-[10px] text-[#64748b] ml-2">
              5 DETERMINISTIC QUANTILE BINS PER FEATURE (MIN SAMPLE N=30)
            </span>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[10px] text-[#64748b]">FILTER FEATURE:</span>
            <select
              value={selectedFeatureFilter}
              onChange={(e) => setSelectedFeatureFilter(e.target.value)}
              className="bg-[#141822] text-white border border-[#222633] px-2 py-1 text-xs outline-none cursor-pointer"
            >
              <option value="ALL">ALL FEATURES ({uniqueFeatures.length})</option>
              {uniqueFeatures.map((f) => (
                <option key={f} value={f}>{f}</option>
              ))}
            </select>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-[11px]">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b]">
                <th className="py-1.5 px-2">FEATURE</th>
                <th className="py-1.5 px-2">BIN RANGE</th>
                <th className="py-1.5 px-2">RECORDS</th>
                <th className="py-1.5 px-2">ERRORS</th>
                <th className="py-1.5 px-2">ERROR RATE</th>
                <th className="py-1.5 px-2">FP RATE</th>
                <th className="py-1.5 px-2">FN RATE</th>
                <th className="py-1.5 px-2">STATUS</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#161b26]">
              {filteredRanges.map((r, i) => (
                <tr key={i} className="hover:bg-[#12161f] transition-colors">
                  <td className="py-1.5 px-2 text-white font-bold">{r.feature}</td>
                  <td className="py-1.5 px-2 text-[#38bdf8] font-mono">
                    [{r.binLower?.toFixed(2)}, {r.binUpper?.toFixed(2)})
                  </td>
                  <td className="py-1.5 px-2 text-[#94a3b8] tabular-nums">{r.recordCount?.toLocaleString()}</td>
                  <td className="py-1.5 px-2 text-white font-bold tabular-nums">{r.errorCount?.toLocaleString()}</td>
                  <td className="py-1.5 px-2 tabular-nums">
                    <span className={`font-bold ${
                      r.errorRate >= 0.05 ? "text-[#ef4444]" : r.errorRate >= 0.02 ? "text-[#f59e0b]" : "text-[#10b981]"
                    }`}>
                      {(r.errorRate * 100).toFixed(2)}%
                    </span>
                  </td>
                  <td className="py-1.5 px-2 text-[#f59e0b] tabular-nums">
                    {r.falsePositiveRate !== undefined ? `${(r.falsePositiveRate * 100).toFixed(2)}%` : "-"}
                  </td>
                  <td className="py-1.5 px-2 text-[#ef4444] tabular-nums">
                    {r.falseNegativeRate !== undefined ? `${(r.falseNegativeRate * 100).toFixed(2)}%` : "-"}
                  </td>
                  <td className="py-1.5 px-2">
                    {r.isInsufficientSample ? (
                      <span className="px-1.5 py-0.2 text-[9px] bg-[#334155] text-[#94a3b8]">
                        INSUFFICIENT SAMPLE
                      </span>
                    ) : r.isErrorEnriched ? (
                      <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#ef4444]/20 text-[#f87171] border border-[#ef4444]/40">
                        ENRICHED ({">"}1.5x MEAN)
                      </span>
                    ) : (
                      <span className="px-1.5 py-0.2 text-[9px] text-[#10b981] bg-[#10b981]/10">
                        NOMINAL
                      </span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 6. Bounded High-Confidence Error Records Queue */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
        <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
          <div>
            <span className="text-xs font-bold text-white uppercase">
              5. HIGH-CONFIDENCE ERROR RECORDS (TOP {highConfErrors.length} PRIORITIZED)
            </span>
            <span className="text-[10px] text-[#64748b] ml-2">
              DETERMINISTIC RANKING BY CONFIDENCE &amp; THRESHOLD DISTANCE
            </span>
          </div>
          <span className="text-[10px] text-[#64748b]">CLICK ROW FOR DOSSIER</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-[11px]">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b]">
                <th className="py-1.5 px-2">ROW ID</th>
                <th className="py-1.5 px-2">ERROR TYPE</th>
                <th className="py-1.5 px-2">ACTUAL</th>
                <th className="py-1.5 px-2">PREDICTED</th>
                <th className="py-1.5 px-2">PRED PROB</th>
                <th className="py-1.5 px-2">CONFIDENCE</th>
                <th className="py-1.5 px-2">FORENSIC PRIORITY</th>
                <th className="py-1.5 px-2">ACTION</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#161b26]">
              {highConfErrors.map((rec, i) => (
                <tr
                  key={i}
                  onClick={() => setSelectedRecord(rec)}
                  className={`cursor-pointer transition-colors ${
                    selectedRecord?.stableRowIndex === rec.stableRowIndex ? "bg-[#182236] border-l-2 border-[#3b82f6]" : "hover:bg-[#12161f]"
                  }`}
                >
                  <td className="py-1.5 px-2 font-mono text-[#38bdf8] font-bold">#{rec.stableRowIndex}</td>
                  <td className="py-1.5 px-2">
                    <span className={`px-1.5 py-0.2 text-[9px] font-bold rounded ${
                      rec.errorType === "FALSE_POSITIVE" ? "bg-[#f59e0b]/20 text-[#fbbf24] border border-[#f59e0b]/40" : "bg-[#ef4444]/20 text-[#f87171] border border-[#ef4444]/40"
                    }`}>
                      {rec.errorType}
                    </span>
                  </td>
                  <td className="py-1.5 px-2 text-white font-bold">{rec.actualClass}</td>
                  <td className="py-1.5 px-2 text-[#94a3b8]">{rec.predictedClass}</td>
                  <td className="py-1.5 px-2 font-mono tabular-nums text-white">
                    {rec.predictedProbability?.toFixed(4)}
                  </td>
                  <td className="py-1.5 px-2 font-mono tabular-nums text-[#f59e0b] font-bold">
                    {(rec.confidence * 100).toFixed(1)}%
                  </td>
                  <td className="py-1.5 px-2 font-mono tabular-nums text-[#38bdf8] font-bold">
                    {rec.forensicPriority?.toFixed(4)}
                  </td>
                  <td className="py-1.5 px-2">
                    <button
                      onClick={(e) => {
                        e.stopPropagation();
                        setSelectedRecord(rec);
                      }}
                      className="px-2 py-0.5 bg-[#141822] hover:bg-[#1a202c] text-[#38bdf8] border border-[#222633] text-[10px]"
                    >
                      INSPECT
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Selected Record Inspector Slide-over */}
        {selectedRecord && (
          <div className="p-3 bg-[#0e1117] border border-[#3b82f6]/40 space-y-2 mt-2">
            <div className="flex items-center justify-between border-b border-[#1f2533] pb-1.5">
              <span className="text-xs font-bold text-white uppercase">
                RECORD #{selectedRecord.stableRowIndex} EVIDENCE DOSSIER
              </span>
              <button
                onClick={() => setSelectedRecord(null)}
                className="text-[#64748b] hover:text-white text-xs font-bold"
              >
                ✕ CLOSE
              </button>
            </div>

            <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-[10px]">
              <div><span className="text-[#64748b]">ACTUAL CLASS:</span> <span className="text-white font-bold">{selectedRecord.actualClass}</span></div>
              <div><span className="text-[#64748b]">PREDICTED PROB:</span> <span className="text-[#38bdf8] font-bold">{selectedRecord.predictedProbability?.toFixed(4)}</span></div>
              <div><span className="text-[#64748b]">CONFIDENCE:</span> <span className="text-[#f59e0b] font-bold">{(selectedRecord.confidence * 100).toFixed(1)}%</span></div>
              <div><span className="text-[#64748b]">ERROR TYPE:</span> <span className="text-[#f87171] font-bold">{selectedRecord.errorType}</span></div>
            </div>

            {selectedRecord.keyAssociatedFeatures && (
              <div className="pt-2 border-t border-[#1f2533]">
                <div className="text-[10px] text-[#94a3b8] font-bold mb-1">KEY FEATURE VALUES:</div>
                <div className="flex flex-wrap gap-2">
                  {Object.entries(selectedRecord.keyAssociatedFeatures).map(([k, v]) => (
                    <div key={k} className="px-2 py-1 bg-[#141822] border border-[#1f2533] text-[10px]">
                      <span className="text-[#64748b]">{k}: </span>
                      <span className="text-white font-mono font-bold">{String(v)}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </div>
        )}
      </div>

      {/* 7. Threshold Error Tradeoff & Sensitivity Analysis */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 border-b border-[#1f2533] pb-2">
          <div>
            <span className="text-xs font-bold text-white uppercase">
              6. THRESHOLD SENSITIVITY &amp; ERROR BURDEN SHIFT
            </span>
            <span className="text-[10px] text-[#64748b] ml-2">
              EXAMINE FP vs FN TRADEOFFS ACROSS 21 DECISION THRESHOLDS
            </span>
          </div>

          <div className="flex items-center gap-2">
            <span className="text-[10px] text-[#64748b]">THRESHOLD:</span>
            <input
              type="range"
              min="0.05"
              max="0.95"
              step="0.05"
              value={selectedThresholdVal}
              onChange={(e) => setSelectedThresholdVal(parseFloat(e.target.value))}
              className="w-32 accent-[#3b82f6] cursor-pointer"
            />
            <span className="text-xs text-white font-bold font-mono">
              {selectedThresholdVal.toFixed(2)}
            </span>
          </div>
        </div>

        {/* Active Threshold KPI Banner */}
        <div className="p-2.5 bg-[#12161f] border border-[#1f2533] grid grid-cols-2 sm:grid-cols-6 gap-2 text-center">
          <div>
            <div className="text-[9px] text-[#64748b]">PRECISION</div>
            <div className="text-sm font-bold text-white">{activeThresholdPoint?.precision?.toFixed(3)}</div>
          </div>
          <div>
            <div className="text-[9px] text-[#64748b]">RECALL (TPR)</div>
            <div className="text-sm font-bold text-white">{activeThresholdPoint?.recall?.toFixed(3)}</div>
          </div>
          <div>
            <div className="text-[9px] text-[#64748b]">F1 SCORE</div>
            <div className="text-sm font-bold text-[#38bdf8]">{activeThresholdPoint?.f1?.toFixed(3)}</div>
          </div>
          <div>
            <div className="text-[9px] text-[#64748b]">FALSE POSITIVES</div>
            <div className="text-sm font-bold text-[#f59e0b]">{activeThresholdPoint?.falsePositive?.toLocaleString()}</div>
          </div>
          <div>
            <div className="text-[9px] text-[#64748b]">FALSE NEGATIVES</div>
            <div className="text-sm font-bold text-[#ef4444]">{activeThresholdPoint?.falseNegative?.toLocaleString()}</div>
          </div>
          <div>
            <div className="text-[9px] text-[#64748b]">FPR / FNR</div>
            <div className="text-sm font-bold text-[#94a3b8]">
              {(activeThresholdPoint?.falsePositiveRate * 100).toFixed(1)}% / {(activeThresholdPoint?.falseNegativeRate * 100).toFixed(1)}%
            </div>
          </div>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-[10px]">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b]">
                <th className="py-1 px-2">THRESHOLD</th>
                <th className="py-1 px-2">TP</th>
                <th className="py-1 px-2">TN</th>
                <th className="py-1 px-2">FP</th>
                <th className="py-1 px-2">FN</th>
                <th className="py-1 px-2">PRECISION</th>
                <th className="py-1 px-2">RECALL</th>
                <th className="py-1 px-2">SPECIFICITY</th>
                <th className="py-1 px-2">F1</th>
                <th className="py-1 px-2">FPR</th>
                <th className="py-1 px-2">FNR</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#161b26]">
              {thresholdGrid.map((tp, idx) => {
                const isSelected = Math.abs(tp.threshold - selectedThresholdVal) < 0.03;
                return (
                  <tr
                    key={idx}
                    onClick={() => setSelectedThresholdVal(tp.threshold)}
                    className={`cursor-pointer transition-colors ${
                      isSelected ? "bg-[#182236] font-bold text-white" : "hover:bg-[#12161f] text-[#94a3b8]"
                    }`}
                  >
                    <td className="py-1 px-2 font-mono text-[#38bdf8]">{tp.threshold?.toFixed(2)}</td>
                    <td className="py-1 px-2 tabular-nums">{tp.truePositive?.toLocaleString()}</td>
                    <td className="py-1 px-2 tabular-nums">{tp.trueNegative?.toLocaleString()}</td>
                    <td className="py-1 px-2 tabular-nums text-[#f59e0b]">{tp.falsePositive?.toLocaleString()}</td>
                    <td className="py-1 px-2 tabular-nums text-[#ef4444]">{tp.falseNegative?.toLocaleString()}</td>
                    <td className="py-1 px-2 tabular-nums">{tp.precision?.toFixed(3)}</td>
                    <td className="py-1 px-2 tabular-nums">{tp.recall?.toFixed(3)}</td>
                    <td className="py-1 px-2 tabular-nums">{tp.specificity?.toFixed(3)}</td>
                    <td className="py-1 px-2 tabular-nums text-white font-bold">{tp.f1?.toFixed(3)}</td>
                    <td className="py-1 px-2 tabular-nums">{(tp.falsePositiveRate * 100).toFixed(1)}%</td>
                    <td className="py-1 px-2 tabular-nums">{(tp.falseNegativeRate * 100).toFixed(1)}%</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>

      {/* 8. Subgroup Error Disparity & Reliability Gaps */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-3">
        <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
          <div>
            <span className="text-xs font-bold text-white uppercase">
              7. SUBGROUP ERROR DISPARITY FORENSICS
            </span>
            <span className="text-[10px] text-[#64748b] ml-2">
              95% WILSON SCORE CONFIDENCE INTERVALS REUSING BIAS SUBGROUP DEFINITIONS
            </span>
          </div>
          <span className="text-[10px] text-[#64748b]">
            SEE BIAS MODULE FOR FAIRNESS METRICS
          </span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left border-collapse text-[11px]">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b]">
                <th className="py-1.5 px-2">SUBGROUP</th>
                <th className="py-1.5 px-2">SAMPLE SIZE (N)</th>
                <th className="py-1.5 px-2">ERROR COUNT</th>
                <th className="py-1.5 px-2">ERROR RATE</th>
                <th className="py-1.5 px-2">95% WILSON CI</th>
                <th className="py-1.5 px-2">FP RATE</th>
                <th className="py-1.5 px-2">FN RATE</th>
                <th className="py-1.5 px-2">HIGH-CONF ERR RATE</th>
                <th className="py-1.5 px-2">DISPARITY RATIO</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#161b26]">
              {subgroupAnalysis.map((sg, i) => (
                <tr key={i} className="hover:bg-[#12161f] transition-colors">
                  <td className="py-1.5 px-2 text-white font-bold uppercase">{sg.group}</td>
                  <td className="py-1.5 px-2 text-[#94a3b8] tabular-nums">{sg.sampleCount?.toLocaleString()}</td>
                  <td className="py-1.5 px-2 text-white font-bold tabular-nums">{sg.errorCount?.toLocaleString()}</td>
                  <td className="py-1.5 px-2 tabular-nums">
                    <span className={`font-bold ${sg.errorRate >= 0.05 ? "text-[#ef4444]" : "text-[#10b981]"}`}>
                      {(sg.errorRate * 100).toFixed(2)}%
                    </span>
                  </td>
                  <td className="py-1.5 px-2 text-[#64748b] font-mono">
                    [{(sg.wilsonCiLower * 100).toFixed(2)}%, {(sg.wilsonCiUpper * 100).toFixed(2)}%]
                  </td>
                  <td className="py-1.5 px-2 text-[#f59e0b] tabular-nums">{(sg.falsePositiveRate * 100).toFixed(2)}%</td>
                  <td className="py-1.5 px-2 text-[#ef4444] tabular-nums">{(sg.falseNegativeRate * 100).toFixed(2)}%</td>
                  <td className="py-1.5 px-2 text-[#38bdf8] tabular-nums">{(sg.highConfidenceErrorRate * 100).toFixed(2)}%</td>
                  <td className="py-1.5 px-2 tabular-nums">
                    <span className={`px-1.5 py-0.2 text-[9px] font-bold rounded ${
                      sg.disparityRatio >= 1.5 ? "bg-[#ef4444]/20 text-[#f87171] border border-[#ef4444]/40" : "bg-[#10b981]/10 text-[#10b981]"
                    }`}>
                      {sg.disparityRatio?.toFixed(2)}x
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>

      {/* 9. Forensic Findings Dossier */}
      {findings.length > 0 && (
        <div className="p-3 border border-[#1f2533] bg-[#0c0e14] space-y-2">
          <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
            <span className="text-xs font-bold text-white uppercase">
              8. DETERMINISTIC ERROR FORENSIC FINDINGS
            </span>
            <span className="text-[10px] text-[#64748b]">
              {findings.length} FINDINGS DETECTED
            </span>
          </div>

          <div className="space-y-2">
            {findings.map((f, i) => (
              <div
                key={i}
                className="p-2.5 bg-[#12161f] border border-[#1f2533] space-y-1.5"
              >
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <span className={`px-1.5 py-0.2 text-[9px] font-bold ${
                      f.severity === "CRITICAL" ? "bg-[#ef4444] text-black" : f.severity === "HIGH" ? "bg-[#f59e0b] text-black" : "bg-[#3b82f6] text-black"
                    }`}>
                      {f.severity}
                    </span>
                    <span className="text-xs font-bold text-white">{f.title}</span>
                  </div>
                  <span className="text-[10px] text-[#64748b] font-mono">{f.findingId}</span>
                </div>
                <p className="text-xs text-[#cbd5e1]">{f.summary}</p>
                {f.whyItMatters && (
                  <div className="text-[10px] text-[#94a3b8]">
                    <span className="text-[#64748b] font-bold">WHY IT MATTERS: </span>
                    {f.whyItMatters}
                  </div>
                )}
                {f.evidence && (
                  <div className="text-[10px] text-[#64748b] font-mono bg-[#090b0e] p-1.5 rounded border border-[#181d28]">
                    EVIDENCE: {JSON.stringify(f.evidence)}
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
};
