"use client";

import React, { useState } from "react";
import { CALIBRATION_BINS } from "@/lib/mockData";

export interface PerformanceViewProps {
  result?: Record<string, any> | null;
  status?: string;
  statusMessage?: string;
}

export const PerformanceView: React.FC<PerformanceViewProps> = ({ result, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">MODEL PERFORMANCE &amp; CALIBRATION AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during model performance and calibration evaluation."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid performance metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">PERFORMANCE ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = Boolean(result && result.summary && result.confusionMatrix);

  // Extract real summary metrics if available, otherwise use default demo fallback
  const summary = isRealData
    ? result!.summary
    : {
        sampleCount: 50000,
        positiveCount: 3000,
        negativeCount: 47000,
        positiveRate: 0.06,
        threshold: 0.50,
        accuracy: 0.962,
        precision: 0.741,
        recall: 0.685,
        specificity: 0.982,
        f1: 0.712,
        falsePositiveRate: 0.018,
        falseNegativeRate: 0.315,
        rocAuc: 0.892,
        prAuc: 0.735,
        logLoss: 0.142,
        brierScore: 0.038,
        expectedCalibrationError: 0.038,
        maximumCalibrationError: 0.075,
        calibrationTendency: "WELL_CALIBRATED",
        bestF1Threshold: 0.45,
        bestF1Value: 0.728,
        bestBalancedAccuracyThreshold: 0.50,
        healthScore: 84.0,
        passed: true,
      };

  const cm = isRealData
    ? result!.confusionMatrix
    : {
        threshold: 0.50,
        trueNegative: 46154,
        falsePositive: 846,
        falseNegative: 945,
        truePositive: 2055,
      };

  const thresholdTable: any[] = isRealData && Array.isArray(result!.thresholdAnalysis)
    ? result!.thresholdAnalysis
    : [
        { threshold: 0.10, precision: 0.28, recall: 0.96, specificity: 0.82, f1: 0.43, false_positive_rate: 0.18, false_negative_rate: 0.04 },
        { threshold: 0.30, precision: 0.55, recall: 0.84, specificity: 0.94, f1: 0.66, false_positive_rate: 0.06, false_negative_rate: 0.16 },
        { threshold: 0.50, precision: 0.74, recall: 0.69, specificity: 0.98, f1: 0.71, false_positive_rate: 0.02, false_negative_rate: 0.31 },
        { threshold: 0.70, precision: 0.88, recall: 0.48, specificity: 0.99, f1: 0.62, false_positive_rate: 0.01, false_negative_rate: 0.52 },
        { threshold: 0.90, precision: 0.96, recall: 0.18, specificity: 1.00, f1: 0.30, false_positive_rate: 0.00, false_negative_rate: 0.82 },
      ];

  const calibration = isRealData && result!.calibration ? result!.calibration : null;
  const baselineComp = isRealData && result!.baselineComparison ? result!.baselineComparison : null;
  const findings: any[] = isRealData && Array.isArray(result!.findings) ? result!.findings : [];

  const [selectedThreshold, setSelectedThreshold] = useState<number>(0.50);

  const activeThresholdMetrics = thresholdTable.find(
    (t) => Math.abs(t.threshold - selectedThreshold) < 0.001
  ) || {
    threshold: selectedThreshold,
    precision: summary.precision,
    recall: summary.recall,
    specificity: summary.specificity,
    f1: summary.f1,
    false_positive_rate: summary.falsePositiveRate,
    false_negative_rate: summary.falseNegativeRate,
  };

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Module Header Banner */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#94a3b8] uppercase font-bold">
              05 MODEL PERFORMANCE &amp; PROBABILITY CALIBRATION AUDIT
            </span>
            {isRealData ? (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#10b981]/20 text-[#10b981] border border-[#10b981]/40">
                REAL STATISTICAL EVIDENCE
              </span>
            ) : (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#f59e0b]/20 text-[#f59e0b] border border-[#f59e0b]/40">
                DEMO / SYNTHETIC FIXTURE FALLBACK
              </span>
            )}
          </div>
          <div className="text-[11px] text-[#64748b] mt-0.5">
            Confusion matrix, ROC-AUC, PR-AUC, Brier score, Binary Log Loss, and 10-bin Expected Calibration Error (ECE).
          </div>
        </div>

        <div
          className={`px-2.5 py-1 text-xs font-bold shrink-0 ${
            summary.passed
              ? "bg-[#10b981] text-black"
              : "bg-[#ef4444] text-black"
          }`}
        >
          {summary.passed
            ? `STATUS: PASS (HEALTH: ${summary.healthScore?.toFixed(0)}/100)`
            : `STATUS: DEGRADED (HEALTH: ${summary.healthScore?.toFixed(0)}/100)`}
        </div>
      </div>

      {/* Primary KPI Metric Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">ROC-AUC DISCRIMINATION</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              summary.rocAuc !== null && summary.rocAuc >= 0.75
                ? "text-[#10b981]"
                : summary.rocAuc !== null && summary.rocAuc >= 0.60
                ? "text-[#f59e0b]"
                : "text-[#ef4444]"
            }`}
          >
            {summary.rocAuc !== null && summary.rocAuc !== undefined ? summary.rocAuc.toFixed(4) : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b]">&gt;= 0.70 acceptable baseline</div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">PR-AUC (AVG PRECISION)</div>
          <div className="text-xl font-bold text-white tabular-nums mt-0.5">
            {summary.prAuc !== null && summary.prAuc !== undefined ? summary.prAuc.toFixed(4) : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Baseline prevalence: {(summary.positiveRate * 100).toFixed(2)}%
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">F1 SCORE (THRESHOLD {summary.threshold})</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.f1 || 0) >= 0.70
                ? "text-[#10b981]"
                : (summary.f1 || 0) >= 0.50
                ? "text-[#f59e0b]"
                : "text-[#ef4444]"
            }`}
          >
            {summary.f1 !== undefined ? summary.f1.toFixed(4) : "0.0000"}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Optimal F1: {summary.bestF1Value?.toFixed(3)} @ {summary.bestF1Threshold}
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">CALIBRATION ERROR (ECE)</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.expectedCalibrationError || 0) < 0.05
                ? "text-[#10b981]"
                : (summary.expectedCalibrationError || 0) < 0.10
                ? "text-[#f59e0b]"
                : "text-[#ef4444]"
            }`}
          >
            {summary.expectedCalibrationError !== undefined
              ? summary.expectedCalibrationError.toFixed(4)
              : "0.0000"}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Tendency: <span className="text-[#3b82f6] font-bold">{summary.calibrationTendency || "N/A"}</span>
          </div>
        </div>
      </div>

      {/* Confusion Matrix & Classification Rates Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
        {/* Confusion Matrix 2x2 Box */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>CONFUSION MATRIX (THRESHOLD: {summary.threshold})</span>
            <span className="text-[10px] text-[#64748b]">
              EVAL TOTAL: {summary.sampleCount?.toLocaleString()} SAMPLES
            </span>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1">
            <div className="p-3 border border-[#1f2533] bg-[#141822]">
              <div className="text-[10px] text-[#64748b] uppercase">TRUE NEGATIVES (TN)</div>
              <div className="text-xl font-bold text-[#10b981] tabular-nums mt-0.5">
                {cm.trueNegative?.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#64748b]">Actual: Negative | Pred: Negative</div>
            </div>

            <div className="p-3 border border-[#f59e0b]/40 bg-[#17130b]">
              <div className="text-[10px] text-[#f59e0b] uppercase">FALSE POSITIVES (FP)</div>
              <div className="text-xl font-bold text-[#f59e0b] tabular-nums mt-0.5">
                {cm.falsePositive?.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#64748b]">Type I Error (FPR: {(summary.falsePositiveRate * 100).toFixed(2)}%)</div>
            </div>

            <div className="p-3 border border-[#ef4444]/40 bg-[#170e10]">
              <div className="text-[10px] text-[#ef4444] uppercase">FALSE NEGATIVES (FN)</div>
              <div className="text-xl font-bold text-[#ef4444] tabular-nums mt-0.5">
                {cm.falseNegative?.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#64748b]">Type II Error (FNR: {(summary.falseNegativeRate * 100).toFixed(2)}%)</div>
            </div>

            <div className="p-3 border border-[#1f2533] bg-[#141822]">
              <div className="text-[10px] text-[#64748b] uppercase">TRUE POSITIVES (TP)</div>
              <div className="text-xl font-bold text-[#3b82f6] tabular-nums mt-0.5">
                {cm.truePositive?.toLocaleString()}
              </div>
              <div className="text-[10px] text-[#64748b]">Actual: Positive | Pred: Positive</div>
            </div>
          </div>
        </div>

        {/* Operating Rates Summary Box */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>DIAGNOSTIC CLASSIFICATION RATES</span>
            <span className="text-[10px] text-[#64748b]">OPERATING METRICS</span>
          </div>

          <div className="grid grid-cols-2 gap-2 pt-1 text-[11px]">
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Accuracy:</span>
              <span className="text-white font-bold tabular-nums">{(summary.accuracy * 100).toFixed(2)}%</span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Precision (PPV):</span>
              <span className="text-white font-bold tabular-nums">{(summary.precision * 100).toFixed(2)}%</span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Recall (Sensitivity / TPR):</span>
              <span className="text-white font-bold tabular-nums">{(summary.recall * 100).toFixed(2)}%</span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Specificity (TNR):</span>
              <span className="text-white font-bold tabular-nums">{(summary.specificity * 100).toFixed(2)}%</span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Log Loss:</span>
              <span className="text-[#3b82f6] font-bold tabular-nums">{summary.logLoss !== null && summary.logLoss !== undefined ? summary.logLoss.toFixed(4) : "N/A"}</span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Brier Score:</span>
              <span className="text-[#3b82f6] font-bold tabular-nums">{summary.brierScore !== null && summary.brierScore !== undefined ? summary.brierScore.toFixed(4) : "N/A"}</span>
            </div>
          </div>
        </div>
      </div>

      {/* Structured Findings Section */}
      {findings.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>PERFORMANCE &amp; CALIBRATION FINDINGS ({findings.length})</span>
            <span className="text-[10px] text-[#64748b]">SORTED BY SEVERITY</span>
          </div>

          <div className="space-y-1.5">
            {findings.map((f: any, idx: number) => {
              const isCrit = f.severity === "CRITICAL";
              const isWarn = f.severity === "WARNING" || f.severity === "HIGH";
              const borderColor = isCrit
                ? "border-[#ef4444]"
                : isWarn
                ? "border-[#f59e0b]"
                : "border-[#1f2533]";
              const bgColor = isCrit ? "bg-[#170e10]" : isWarn ? "bg-[#16120b]" : "bg-[#141822]";

              return (
                <div key={f.id || idx} className={`p-2.5 border ${borderColor} ${bgColor} space-y-1`}>
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span
                        className={`px-1.5 py-0.2 text-[9px] font-bold ${
                          isCrit
                            ? "bg-[#ef4444] text-black"
                            : isWarn
                            ? "bg-[#f59e0b] text-black"
                            : "bg-[#3b82f6] text-white"
                        }`}
                      >
                        {f.severity}
                      </span>
                      <span className="text-white font-bold">{f.title || f.id}</span>
                    </div>
                  </div>
                  <div className="text-[11px] text-[#94a3b8]">{f.description}</div>
                  {f.recommendation && (
                    <div className="text-[11px] text-[#10b981] pt-1">
                      <strong>Remediation:</strong> {f.recommendation}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* 10-Bin Calibration / Reliability Curve Inspector */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
        <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
          <span>PROBABILITY CALIBRATION CURVE &amp; RELIABILITY BINS</span>
          <span className="text-[10px] text-[#64748b]">
            ECE: {summary.expectedCalibrationError?.toFixed(4)} | MCE: {summary.maximumCalibrationError?.toFixed(4)}
          </span>
        </div>

        {isRealData && calibration?.bins?.length > 0 ? (
          <div className="space-y-2">
            {calibration.bins.map((bin: any) => {
              const predPct = bin.meanPredictedProbability * 100;
              const obsPct = bin.observedPositiveRate * 100;
              const err = bin.absoluteCalibrationError;
              const isHighErr = err >= 0.10;

              return (
                <div key={bin.binIndex} className="text-xs">
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-[#f1f3f8] w-24 truncate font-bold">{bin.binRange}</span>
                    <span className="text-[#64748b]">
                      N={bin.sampleCount?.toLocaleString()} | Mean Pred: {predPct.toFixed(1)}% | Observed Positive: {obsPct.toFixed(1)}%
                    </span>
                    <span className={isHighErr ? "text-[#ef4444] font-bold" : "text-[#64748b]"}>
                      Calibration Error: {(err * 100).toFixed(2)}%
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-1 h-3 bg-[#141822] p-0.5 border border-[#1f2533]">
                    <div
                      className="bg-[#3b82f6] h-full"
                      style={{ width: `${Math.min(100, predPct)}%` }}
                    />
                    <div
                      className={isHighErr ? "bg-[#ef4444] h-full" : "bg-[#10b981] h-full"}
                      style={{ width: `${Math.min(100, obsPct)}%` }}
                    />
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          /* Demo Fallback Calibration Bins */
          <div className="space-y-2">
            {CALIBRATION_BINS.map((bin) => {
              const calibErr = Math.abs(bin.predictedProbability - bin.observedFrequency);
              const rangeStr = `[${(bin.binIndex * 0.1).toFixed(1)}, ${((bin.binIndex + 1) * 0.1).toFixed(1)}]`;
              return (
                <div key={bin.binIndex} className="text-xs">
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className="text-[#f1f3f8] w-24">{rangeStr}</span>
                    <span className="text-[#64748b]">
                      N={bin.sampleCount?.toLocaleString()} | Pred: {(bin.predictedProbability * 100).toFixed(1)}% | Observed: {(bin.observedFrequency * 100).toFixed(1)}%
                    </span>
                    <span className={calibErr > 0.05 ? "text-[#ef4444] font-bold" : "text-[#64748b]"}>
                      Error: {(calibErr * 100).toFixed(2)}%
                    </span>
                  </div>
                  <div className="grid grid-cols-2 gap-1 h-3 bg-[#141822] p-0.5 border border-[#1f2533]">
                    <div className="bg-[#3b82f6] h-full" style={{ width: `${bin.predictedProbability * 100}%` }} />
                    <div className="bg-[#10b981] h-full" style={{ width: `${bin.observedFrequency * 100}%` }} />
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Interactive Threshold Sensitivity Grid Table */}
      {isRealData && thresholdTable.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>THRESHOLD SENSITIVITY MATRIX ({thresholdTable.length} OPERATING POINTS)</span>
            <span className="text-[10px] text-[#64748b]">
              BEST F1: {summary.bestF1Threshold} | BALANCED ACCURACY: {summary.bestBalancedAccuracyThreshold}
            </span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                  <th className="py-1.5 px-2">Threshold</th>
                  <th className="py-1.5 px-2">Precision</th>
                  <th className="py-1.5 px-2">Recall (TPR)</th>
                  <th className="py-1.5 px-2">Specificity (TNR)</th>
                  <th className="py-1.5 px-2">F1 Score</th>
                  <th className="py-1.5 px-2">FPR</th>
                  <th className="py-1.5 px-2">FNR</th>
                  <th className="py-1.5 px-2">Predicted Pos %</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {thresholdTable.map((t: any) => {
                  const isBestF1 = Math.abs(t.threshold - summary.bestF1Threshold) < 0.001;
                  const isDefault = Math.abs(t.threshold - 0.50) < 0.001;

                  return (
                    <tr
                      key={t.threshold}
                      className={`hover:bg-[#12161f] transition-colors ${
                        isBestF1 ? "bg-[#141c2e] border-l-2 border-[#3b82f6]" : isDefault ? "bg-[#0e1420]" : ""
                      }`}
                    >
                      <td className="py-1.5 px-2 text-[#f1f3f8] font-bold">
                        {t.threshold.toFixed(2)}
                        {isBestF1 && (
                          <span className="ml-1.5 px-1 py-0.2 text-[8px] font-bold bg-[#3b82f6] text-white">
                            BEST F1
                          </span>
                        )}
                        {isDefault && (
                          <span className="ml-1.5 px-1 py-0.2 text-[8px] font-bold bg-[#1f2533] text-[#94a3b8]">
                            DEFAULT
                          </span>
                        )}
                      </td>
                      <td className="py-1.5 px-2 text-white">{(t.precision * 100).toFixed(1)}%</td>
                      <td className="py-1.5 px-2 text-white">{(t.recall * 100).toFixed(1)}%</td>
                      <td className="py-1.5 px-2 text-[#94a3b8]">{(t.specificity * 100).toFixed(1)}%</td>
                      <td className="py-1.5 px-2 font-bold text-[#10b981]">{t.f1.toFixed(4)}</td>
                      <td className="py-1.5 px-2 text-[#64748b]">{(t.false_positive_rate * 100).toFixed(1)}%</td>
                      <td className="py-1.5 px-2 text-[#64748b]">{(t.false_negative_rate * 100).toFixed(1)}%</td>
                      <td className="py-1.5 px-2 text-[#94a3b8]">
                        {t.predicted_positive_rate !== undefined ? `${(t.predicted_positive_rate * 100).toFixed(1)}%` : "-"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Prevalence Baseline Comparison Card */}
      {baselineComp && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>PREVALENCE BASELINE COMPARISON</span>
            <span
              className={`px-1.5 py-0.2 text-[9px] font-bold ${
                baselineComp.beatsPrevalenceBaseline
                  ? "bg-[#10b981]/20 text-[#10b981] border border-[#10b981]/40"
                  : "bg-[#ef4444]/20 text-[#ef4444] border border-[#ef4444]/40"
              }`}
            >
              {baselineComp.beatsPrevalenceBaseline ? "BEATS NAIVE PRIOR BASELINE" : "FAILS NAIVE PRIOR BASELINE"}
            </span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 text-[11px] pt-1">
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Log Loss (Model vs Baseline):</span>
              <span className="text-white font-bold tabular-nums">
                {baselineComp.modelLogLoss?.toFixed(4)} vs {baselineComp.baselineLogLoss?.toFixed(4)}
              </span>
            </div>
            <div className="p-2 border border-[#1f2533] bg-[#12161f] flex justify-between items-center">
              <span className="text-[#64748b]">Brier Score (Model vs Baseline):</span>
              <span className="text-white font-bold tabular-nums">
                {baselineComp.modelBrierScore?.toFixed(4)} vs {baselineComp.baselineBrierScore?.toFixed(4)}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
