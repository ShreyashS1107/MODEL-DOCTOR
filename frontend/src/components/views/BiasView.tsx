"use client";

import React, { useState } from "react";

export interface BiasViewProps {
  biasResult?: any;
  status?: string;
  statusMessage?: string;
}

export const BiasView: React.FC<BiasViewProps> = ({ biasResult, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">SUBGROUP FAIRNESS &amp; BIAS AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during demographic parity and equal opportunity analysis."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid fairness metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">BIAS ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = !!biasResult && (biasResult.module === "BIAS" || biasResult.module === "FAIRNESS");

  // Fallback synthetic mock fixture for demo/standalone viewing
  const fallbackSummary = {
    protectedAttribute: "is_foreign_ip",
    groupCount: 2,
    referenceGroup: "0 (Domestic)",
    referenceSelection: "largest_group",
    totalEvaluatedRows: 250000,
    rawTotalRows: 250000,
    excludedMissingProtectedAttribute: 0,
    excludedMissingRate: 0.0,
    smallGroupsCount: 0,
    demographicParityGap: 0.011,
    worstDisparateImpactRatio: 0.7708,
    equalOpportunityGap: 0.038,
    equalizedOdds: {
      tprGap: 0.038,
      fprGap: 0.012,
      maxGap: 0.038,
    },
    worstCalibrationGap: 0.018,
    hasProbabilities: true,
    findingCount: 1,
    healthScore: 85.0,
    passed: true,
  };

  const fallbackGroups = [
    {
      group: "0 (Domestic)",
      sampleCount: 218400,
      isReference: true,
      isSmallSample: false,
      confusionMatrix: { tp: 8380, tn: 200000, fp: 2100, fn: 7920 },
      positivePredictionCount: 10480,
      positivePredictionRate: 0.048,
      actualPositiveCount: 16300,
      actualPositiveRate: 0.0746,
      tpr: 0.5141,
      fnr: 0.4859,
      tnr: 0.9896,
      fpr: 0.0104,
      ppv: 0.7996,
      npv: 0.9619,
      accuracy: 0.9541,
      meanPredictedProbability: 0.052,
      calibrationGap: -0.0226,
      confidenceIntervals: {
        selectionRate: { lower: 0.0471, upper: 0.0489 },
        tpr: { lower: 0.5064, upper: 0.5218 },
        fpr: { lower: 0.0099, upper: 0.0108 },
      },
    },
    {
      group: "1 (Foreign / Cross-Border)",
      sampleCount: 31600,
      isReference: false,
      isSmallSample: false,
      confusionMatrix: { tp: 890, tn: 29800, fp: 280, fn: 630 },
      positivePredictionCount: 1170,
      positivePredictionRate: 0.037,
      actualPositiveCount: 1520,
      actualPositiveRate: 0.0481,
      tpr: 0.5855,
      fnr: 0.4145,
      tnr: 0.9907,
      fpr: 0.0093,
      ppv: 0.7607,
      npv: 0.9793,
      accuracy: 0.9712,
      meanPredictedProbability: 0.041,
      calibrationGap: -0.0071,
      confidenceIntervals: {
        selectionRate: { lower: 0.035, upper: 0.0391 },
        tpr: { lower: 0.5606, upper: 0.6101 },
        fpr: { lower: 0.0083, upper: 0.0105 },
      },
    },
  ];

  const fallbackDisparity = {
    demographicParity: [
      { group: "0 (Domestic)", selectionRate: 0.048, referenceRate: 0.048, difference: 0.0, absoluteDifference: 0.0 },
      { group: "1 (Foreign / Cross-Border)", selectionRate: 0.037, referenceRate: 0.048, difference: -0.011, absoluteDifference: 0.011 },
    ],
    disparateImpact: [
      { group: "0 (Domestic)", selectionRate: 0.048, referenceRate: 0.048, disparateImpactRatio: 1.0, violates80Rule: false },
      { group: "1 (Foreign / Cross-Border)", selectionRate: 0.037, referenceRate: 0.048, disparateImpactRatio: 0.7708, violates80Rule: true },
    ],
    equalOpportunity: [
      { group: "0 (Domestic)", tpr: 0.5141, referenceTpr: 0.5141, difference: 0.0, absoluteDifference: 0.0 },
      { group: "1 (Foreign / Cross-Border)", tpr: 0.5855, referenceTpr: 0.5141, difference: 0.0714, absoluteDifference: 0.0714 },
    ],
    equalizedOdds: {
      tprGap: 0.0714,
      fprGap: 0.0011,
      maxGap: 0.0714,
    },
    calibration: [
      { group: "0 (Domestic)", meanPredictedProbability: 0.052, actualPositiveRate: 0.0746, calibrationGap: -0.0226, absoluteGap: 0.0226 },
      { group: "1 (Foreign / Cross-Border)", meanPredictedProbability: 0.041, actualPositiveRate: 0.0481, calibrationGap: -0.0071, absoluteGap: 0.0071 },
    ],
  };

  const fallbackFindings = [
    {
      id: "BIAS_DISPARATE_IMPACT_WARNING_1",
      category: "fairness",
      severity: "WARNING",
      title: "Selection Rate Disparity in '1 (Foreign / Cross-Border)' (DIR = 0.771 < 0.80)",
      description: "Positive selection rate for group '1 (Foreign / Cross-Border)' (3.7%) falls below 80% of the reference rate (4.8%).",
      affected_features: ["is_foreign_ip"],
      evidence: { group: "1 (Foreign / Cross-Border)", disparateImpactRatio: 0.7708, threshold: 0.8 },
      recommendation: "Evaluate whether disparate outcomes stem from historical base rate differences or model attribution proxies.",
    },
  ];

  const summary = isRealData ? biasResult.summary : fallbackSummary;
  const groups: any[] = isRealData ? biasResult.groups || [] : fallbackGroups;
  const disparity = isRealData ? biasResult.disparity || {} : fallbackDisparity;
  const findings: any[] = isRealData ? biasResult.findings || [] : fallbackFindings;
  const worstGroups = isRealData ? biasResult.worstGroups || {} : { worstDisparateImpact: "1 (Foreign / Cross-Border)" };
  const configuration = isRealData ? biasResult.configuration || {} : { minimumGroupSize: 30, threshold: 0.5 };

  const [activeTab, setActiveTab] = useState<"MATRIX" | "DISPARITY" | "ODDS" | "CALIBRATION" | "FINDINGS">("MATRIX");

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Header & Mode Badge */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center gap-2">
            <span>07 BIAS / FAIRNESS — GROUP DISPARITY ANALYSIS</span>
            {isRealData ? (
              <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#064e3b] text-[#34d399] border border-[#059669]">
                LIVE RUN AUDIT ACTIVE
              </span>
            ) : (
              <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#1e293b] text-[#94a3b8] border border-[#334155]">
                DEMO / SYNTHETIC FIXTURE FALLBACK
              </span>
            )}
          </div>
          <div className="text-[11px] text-[#64748b]">
            Empirical statistical disparity evaluation across demographic partitions and sensitive feature slices.
          </div>
        </div>

        <div className="flex items-center gap-2 text-[10px]">
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            PROTECTED ATTR: <span className="text-white font-bold">{summary?.protectedAttribute || "is_foreign_ip"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            REF GROUP: <span className="text-[#3b82f6] font-bold">{summary?.referenceGroup || "0"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            GROUPS: <span className="text-white font-bold">{summary?.groupCount || groups.length}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            EVAL ROWS: <span className="text-white font-bold">{summary?.totalEvaluatedRows?.toLocaleString() || "0"}</span>
          </div>
        </div>
      </div>

      {/* Forensic Disclaimer Banner */}
      <div className="p-2.5 bg-[#0e121a] border border-[#1e293b] text-[10px] text-[#94a3b8] flex items-start gap-2">
        <span className="text-[#f59e0b] font-bold shrink-0">[FORENSIC NOTICE]</span>
        <span>
          Fairness heuristics quantify mathematical disparities in positive prediction rates and error distributions across evaluated demographic cohorts.
          Disparities under these heuristics do not establish causality, discriminatory intent, or legal non-compliance.
        </span>
      </div>

      {/* KPI Metric Strip */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
        {/* Disparate Impact Ratio */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">WORST DISPARATE IMPACT (DIR)</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.worstDisparateImpactRatio !== undefined && summary.worstDisparateImpactRatio < 0.70
                ? "text-[#ef4444]"
                : summary?.worstDisparateImpactRatio !== undefined && summary.worstDisparateImpactRatio < 0.80
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.worstDisparateImpactRatio !== undefined
              ? summary.worstDisparateImpactRatio.toFixed(4)
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {summary?.worstDisparateImpactRatio !== undefined && summary.worstDisparateImpactRatio < 0.80
              ? "Violates 80% Rule"
              : "Within 80% Bound"}
          </div>
        </div>

        {/* Equal Opportunity Gap */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">EQUAL OPPORTUNITY (TPR GAP)</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.equalOpportunityGap !== undefined && summary.equalOpportunityGap >= 0.10
                ? "text-[#ef4444]"
                : summary?.equalOpportunityGap !== undefined && summary.equalOpportunityGap >= 0.05
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.equalOpportunityGap !== undefined
              ? `${(summary.equalOpportunityGap * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Max Recall Disparity
          </div>
        </div>

        {/* Equalized Odds Max Gap */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">EQUALIZED ODDS (MAX GAP)</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.equalizedOdds?.maxGap !== undefined && summary.equalizedOdds.maxGap >= 0.10
                ? "text-[#ef4444]"
                : summary?.equalizedOdds?.maxGap !== undefined && summary.equalizedOdds.maxGap >= 0.05
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.equalizedOdds?.maxGap !== undefined
              ? `${(summary.equalizedOdds.maxGap * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            FPR Gap: {summary?.equalizedOdds?.fprGap !== undefined ? `${(summary.equalizedOdds.fprGap * 100).toFixed(1)}%` : "N/A"}
          </div>
        </div>

        {/* Demographic Parity Gap */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">DEMOGRAPHIC PARITY GAP</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.demographicParityGap !== undefined && summary.demographicParityGap >= 0.20
                ? "text-[#ef4444]"
                : summary?.demographicParityGap !== undefined && summary.demographicParityGap >= 0.10
                ? "text-[#f59e0b]"
                : "text-white"
            }`}
          >
            {summary?.demographicParityGap !== undefined
              ? `${(summary.demographicParityGap * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Selection Rate Divergence
          </div>
        </div>

        {/* Calibration Gap */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">WORST CALIBRATION GAP</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.worstCalibrationGap !== undefined && summary.worstCalibrationGap >= 0.10
                ? "text-[#ef4444]"
                : summary?.worstCalibrationGap !== undefined && summary.worstCalibrationGap >= 0.05
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.worstCalibrationGap !== undefined && summary?.worstCalibrationGap !== null
              ? `${(summary.worstCalibrationGap * 100).toFixed(1)}%`
              : "UNAVAILABLE"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Prob vs Base Rate Drift
          </div>
        </div>

        {/* Health Score / Findings */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">FAIRNESS HEALTH SCORE</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.healthScore !== undefined && summary.healthScore >= 80
                ? "text-[#10b981]"
                : summary?.healthScore !== undefined && summary.healthScore >= 60
                ? "text-[#f59e0b]"
                : "text-[#ef4444]"
            }`}
          >
            {summary?.healthScore !== undefined ? summary.healthScore.toFixed(1) : "N/A"}
            <span className="text-xs text-[#64748b] font-normal"> / 100</span>
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {findings.length} Finding(s) Logged
          </div>
        </div>
      </div>

      {/* Navigation Tabs */}
      <div className="flex border-b border-[#1f2533] gap-1 bg-[#0c0e14] p-1">
        <button
          onClick={() => setActiveTab("MATRIX")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "MATRIX"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          GROUP PERFORMANCE MATRIX ({groups.length})
        </button>
        <button
          onClick={() => setActiveTab("DISPARITY")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "DISPARITY"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          DISPARATE IMPACT &amp; PARITY
        </button>
        <button
          onClick={() => setActiveTab("ODDS")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "ODDS"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          EQUAL OPPORTUNITY &amp; ODDS
        </button>
        <button
          onClick={() => setActiveTab("CALIBRATION")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "CALIBRATION"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          GROUP CALIBRATION
        </button>
        <button
          onClick={() => setActiveTab("FINDINGS")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "FINDINGS"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          FORENSIC FINDINGS ({findings.length})
        </button>
      </div>

      {/* Tab 1: Group Performance Matrix */}
      {activeTab === "MATRIX" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="flex justify-between items-center">
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              GROUP-WISE CONFUSION MATRIX &amp; RATE COMPARISON
            </div>
            <div className="text-[10px] text-[#64748b]">
              Excluded Rows: {summary?.excludedMissingProtectedAttribute || 0} ({((summary?.excludedMissingRate || 0) * 100).toFixed(1)}%) | Min Group Size: {configuration?.minimumGroupSize || 30}
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase bg-[#090b0e]">
                  <th className="py-2 px-2.5">SUBGROUP SLICE</th>
                  <th className="py-2 px-2.5 text-right">SAMPLE (N)</th>
                  <th className="py-2 px-2.5 text-right">SELECTION RATE [95% CI]</th>
                  <th className="py-2 px-2.5 text-right">BASE RATE P(Y=1)</th>
                  <th className="py-2 px-2.5 text-right">TPR (RECALL)</th>
                  <th className="py-2 px-2.5 text-right">FPR (FALLOUT)</th>
                  <th className="py-2 px-2.5 text-right">FNR (MISS)</th>
                  <th className="py-2 px-2.5 text-right">TNR (SPECIFICITY)</th>
                  <th className="py-2 px-2.5 text-right">PPV (PRECISION)</th>
                  <th className="py-2 px-2.5 text-right">ACCURACY</th>
                  <th className="py-2 px-2.5 text-right">CALIB GAP</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {groups.map((grp: any, idx: number) => {
                  const ciSel = grp.confidenceIntervals?.selectionRate;
                  return (
                    <tr key={idx} className="hover:bg-[#12161f]">
                      <td className="py-2 px-2.5">
                        <div className="flex items-center gap-1.5">
                          <span className={`font-bold ${grp.isReference ? "text-[#3b82f6]" : "text-white"}`}>
                            {grp.group}
                          </span>
                          {grp.isReference && (
                            <span className="px-1 py-0.2 text-[8px] bg-[#1e3a8a] text-[#93c5fd] font-bold border border-[#3b82f6]">
                              REFERENCE
                            </span>
                          )}
                          {grp.isSmallSample && (
                            <span className="px-1 py-0.2 text-[8px] bg-[#78350f] text-[#fde047] font-bold border border-[#f59e0b]">
                              SMALL SAMPLE (N&lt;30)
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.sampleCount?.toLocaleString()}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#f1f3f8]">
                        {(grp.positivePredictionRate * 100).toFixed(1)}%
                        {ciSel && (
                          <span className="text-[9px] text-[#64748b] ml-1">
                            [{(ciSel.lower * 100).toFixed(1)}-{(ciSel.upper * 100).toFixed(1)}%]
                          </span>
                        )}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(grp.actualPositiveRate * 100).toFixed(1)}%
                      </td>
                      <td className="py-2 px-2.5 text-right font-medium text-white">
                        {grp.tpr !== null && grp.tpr !== undefined ? (grp.tpr * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.fpr !== null && grp.fpr !== undefined ? (grp.fpr * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.fnr !== null && grp.fnr !== undefined ? (grp.fnr * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.tnr !== null && grp.tnr !== undefined ? (grp.tnr * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.ppv !== null && grp.ppv !== undefined ? (grp.ppv * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {grp.accuracy !== null && grp.accuracy !== undefined ? (grp.accuracy * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right">
                        {grp.calibrationGap !== null && grp.calibrationGap !== undefined ? (
                          <span className={Math.abs(grp.calibrationGap) >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}>
                            {(grp.calibrationGap * 100).toFixed(1)}%
                          </span>
                        ) : (
                          <span className="text-[#64748b]">N/A</span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Disparate Impact & Demographic Parity */}
      {activeTab === "DISPARITY" && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {/* Disparate Impact Panel */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>DISPARATE IMPACT RATIO (DIR)</span>
              <span className="text-[10px] text-[#64748b]">80% Four-Fifths Heuristic</span>
            </div>
            <div className="text-[11px] text-[#64748b]">
              Ratio of positive selection rate compared to reference group <span className="text-[#3b82f6] font-bold">'{summary?.referenceGroup}'</span>.
            </div>

            <div className="space-y-2 pt-1">
              {(disparity.disparateImpact || []).map((di: any, idx: number) => {
                const ratio = di.disparateImpactRatio;
                const isViol = di.violates80Rule;
                return (
                  <div key={idx} className="p-2 bg-[#090b0e] border border-[#1f2533] flex items-center justify-between">
                    <div>
                      <div className="text-xs font-bold text-white">{di.group}</div>
                      <div className="text-[10px] text-[#64748b]">
                        Selection: {(di.selectionRate * 100).toFixed(1)}% vs Ref: {(di.referenceRate * 100).toFixed(1)}%
                      </div>
                    </div>
                    <div className="text-right">
                      <div className={`text-sm font-bold tabular-nums ${ratio === null ? "text-[#64748b]" : ratio < 0.70 ? "text-[#ef4444]" : ratio < 0.80 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                        {ratio !== null ? ratio.toFixed(4) : "N/A"}
                      </div>
                      <div className="text-[9px]">
                        {ratio === 1.0 ? (
                          <span className="text-[#3b82f6] font-bold">REFERENCE (1.000)</span>
                        ) : isViol ? (
                          <span className="text-[#f59e0b] font-bold">DISPARITY (&lt;0.80)</span>
                        ) : (
                          <span className="text-[#10b981] font-bold">PASS (&gt;=0.80)</span>
                        )}
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Demographic Parity Difference */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>DEMOGRAPHIC PARITY DIFFERENCE (DPD)</span>
              <span className="text-[10px] text-[#64748b]">Max Gap: {((summary?.demographicParityGap || 0) * 100).toFixed(1)}%</span>
            </div>
            <div className="text-[11px] text-[#64748b]">
              Absolute &amp; signed difference in positive prediction rates: <span className="text-white">P(Ŷ=1|A=g) - P(Ŷ=1|Ref)</span>.
            </div>

            <div className="space-y-2 pt-1">
              {(disparity.demographicParity || []).map((dp: any, idx: number) => {
                const diff = dp.difference;
                return (
                  <div key={idx} className="p-2 bg-[#090b0e] border border-[#1f2533] flex items-center justify-between">
                    <div>
                      <div className="text-xs font-bold text-white">{dp.group}</div>
                      <div className="text-[10px] text-[#64748b]">
                        Rate: {(dp.selectionRate * 100).toFixed(1)}% (Ref: {(dp.referenceRate * 100).toFixed(1)}%)
                      </div>
                    </div>
                    <div className="text-right">
                      <div className={`text-sm font-bold tabular-nums ${Math.abs(diff) >= 0.10 ? "text-[#f59e0b]" : "text-white"}`}>
                        {diff > 0 ? `+${(diff * 100).toFixed(1)}%` : `${(diff * 100).toFixed(1)}%`}
                      </div>
                      <div className="text-[9px] text-[#64748b]">
                        |Δ| = {(dp.absoluteDifference * 100).toFixed(1)}%
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Tab 3: Equal Opportunity & Equalized Odds */}
      {activeTab === "ODDS" && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {/* Equal Opportunity */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>EQUAL OPPORTUNITY (TPR PARITY)</span>
              <span className="text-[10px] text-[#64748b]">Gap: {((summary?.equalOpportunityGap || 0) * 100).toFixed(1)}%</span>
            </div>
            <div className="text-[11px] text-[#64748b]">
              Evaluates true positive rate equality across slices for qualified positive events: <span className="text-white">P(Ŷ=1|Y=1, A=g)</span>.
            </div>

            <div className="space-y-2 pt-1">
              {(disparity.equalOpportunity || []).map((eo: any, idx: number) => {
                const tprVal = eo.tpr;
                const diff = eo.difference;
                return (
                  <div key={idx} className="p-2 bg-[#090b0e] border border-[#1f2533] flex items-center justify-between">
                    <div>
                      <div className="text-xs font-bold text-white">{eo.group}</div>
                      <div className="text-[10px] text-[#64748b]">
                        TPR: {tprVal !== null ? `${(tprVal * 100).toFixed(1)}%` : "Undefined (No positives)"}
                      </div>
                    </div>
                    <div className="text-right">
                      <div className={`text-sm font-bold tabular-nums ${diff !== null && Math.abs(diff) >= 0.05 ? "text-[#f59e0b]" : "text-white"}`}>
                        {diff !== null ? (diff > 0 ? `+${(diff * 100).toFixed(1)}%` : `${(diff * 100).toFixed(1)}%`) : "N/A"}
                      </div>
                      <div className="text-[9px] text-[#64748b]">
                        vs Ref TPR ({eo.referenceTpr !== null ? `${(eo.referenceTpr * 100).toFixed(1)}%` : "N/A"})
                      </div>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Equalized Odds Summary */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2.5">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>EQUALIZED ODDS (TPR &amp; FPR DIVERGENCE)</span>
              <span className="text-[10px] text-[#64748b]">Max Gap: {((summary?.equalizedOdds?.maxGap || 0) * 100).toFixed(1)}%</span>
            </div>
            <div className="text-[11px] text-[#64748b]">
              Requires simultaneous equality of True Positive Rates and False Positive Rates across demographic groups.
            </div>

            <div className="p-3 bg-[#090b0e] border border-[#1f2533] space-y-2">
              <div className="flex justify-between items-center text-xs">
                <span className="text-[#94a3b8]">MAX TPR DISPARITY GAP:</span>
                <span className={`font-bold tabular-nums ${summary?.equalizedOdds?.tprGap >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                  {summary?.equalizedOdds?.tprGap !== undefined ? `${(summary.equalizedOdds.tprGap * 100).toFixed(1)}%` : "N/A"}
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-[#94a3b8]">MAX FPR DISPARITY GAP:</span>
                <span className={`font-bold tabular-nums ${summary?.equalizedOdds?.fprGap >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                  {summary?.equalizedOdds?.fprGap !== undefined ? `${(summary.equalizedOdds.fprGap * 100).toFixed(1)}%` : "N/A"}
                </span>
              </div>
              <div className="flex justify-between items-center text-xs pt-1 border-t border-[#1f2533]">
                <span className="text-white font-bold">OVERALL EQUALIZED ODDS HEURISTIC:</span>
                <span className={`font-bold tabular-nums ${summary?.equalizedOdds?.maxGap >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                  {summary?.equalizedOdds?.maxGap !== undefined ? `${(summary.equalizedOdds.maxGap * 100).toFixed(1)}%` : "N/A"}
                </span>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: Group Calibration */}
      {activeTab === "CALIBRATION" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
            <span>GROUP-WISE PROBABILITY CALIBRATION</span>
            <span className="text-[10px] text-[#64748b]">Worst Gap: {((summary?.worstCalibrationGap || 0) * 100).toFixed(1)}%</span>
          </div>
          <div className="text-[11px] text-[#64748b]">
            Compares average model predicted probability E[p̂|A=g] against empirical positive event frequency P(Y=1|A=g).
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase bg-[#090b0e]">
                  <th className="py-2 px-2.5">SUBGROUP SLICE</th>
                  <th className="py-2 px-2.5 text-right">MEAN PREDICTED PROBABILITY E[p̂]</th>
                  <th className="py-2 px-2.5 text-right">OBSERVED POSITIVE RATE P(Y=1)</th>
                  <th className="py-2 px-2.5 text-right">CALIBRATION GAP (E[p̂] - P(Y=1))</th>
                  <th className="py-2 px-2.5 text-right">STATUS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {(disparity.calibration || []).map((cal: any, idx: number) => {
                  const gap = cal.calibrationGap;
                  return (
                    <tr key={idx} className="hover:bg-[#12161f]">
                      <td className="py-2 px-2.5 font-bold text-white">{cal.group}</td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {cal.meanPredictedProbability !== null ? (cal.meanPredictedProbability * 100).toFixed(2) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(cal.actualPositiveRate * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right font-bold">
                        {gap !== null ? (
                          <span className={Math.abs(gap) >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}>
                            {gap > 0 ? `+${(gap * 100).toFixed(2)}%` : `${(gap * 100).toFixed(2)}%`}
                          </span>
                        ) : (
                          <span className="text-[#64748b]">N/A</span>
                        )}
                      </td>
                      <td className="py-2 px-2.5 text-right">
                        {gap !== null && Math.abs(gap) >= 0.05 ? (
                          <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#78350f] text-[#fde047] border border-[#f59e0b]">
                            CALIBRATION GAP
                          </span>
                        ) : (
                          <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#064e3b] text-[#34d399] border border-[#059669]">
                            CALIBRATED
                          </span>
                        )}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 5: Forensic Findings */}
      {activeTab === "FINDINGS" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
            <span>STRUCTURED FAIRNESS &amp; DISPARITY FINDINGS</span>
            <span className="text-[10px] text-[#64748b]">{findings.length} Issue(s) Logged</span>
          </div>

          {findings.length === 0 ? (
            <div className="p-4 bg-[#090b0e] border border-[#1f2533] text-center text-[#10b981]">
              ✓ No statistical disparity anomalies or fairness heuristic threshold violations detected.
            </div>
          ) : (
            <div className="space-y-2">
              {findings.map((finding: any, idx: number) => {
                const sev = finding.severity;
                const isCrit = sev === "CRITICAL" || sev === "HIGH";
                return (
                  <div
                    key={idx}
                    className={`p-3 border ${
                      isCrit ? "bg-[#1c0d0d] border-[#ef4444]" : "bg-[#16120b] border-[#f59e0b]"
                    } space-y-1.5`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex items-center gap-2">
                        <span
                          className={`px-1.5 py-0.5 text-[9px] font-bold ${
                            isCrit
                              ? "bg-[#ef4444] text-white"
                              : "bg-[#f59e0b] text-black"
                          }`}
                        >
                          {sev}
                        </span>
                        <span className="font-bold text-white text-xs">{finding.title}</span>
                      </div>
                      <span className="text-[10px] text-[#64748b] font-mono">{finding.id}</span>
                    </div>

                    <div className="text-[11px] text-[#cbd5e1]">{finding.description}</div>

                    {finding.recommendation && (
                      <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#1f2533]">
                        <span className="text-[#3b82f6] font-bold">REMEDIATION:</span> {finding.recommendation}
                      </div>
                    )}
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
