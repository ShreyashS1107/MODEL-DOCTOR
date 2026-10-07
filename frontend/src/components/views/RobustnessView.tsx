"use client";

import React, { useState } from "react";

export interface RobustnessViewProps {
  robustnessResult?: any;
  status?: string;
  statusMessage?: string;
}

export const RobustnessView: React.FC<RobustnessViewProps> = ({ robustnessResult, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">ROBUSTNESS &amp; ADVERSARIAL STABILITY AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during adversarial perturbation and stability analysis."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid stability metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">ROBUSTNESS ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = !!robustnessResult && robustnessResult.module === "ROBUSTNESS";

  // Fallback synthetic mock fixture for demo viewing
  const fallbackSummary = {
    modelName: "fraud_classifier_v17",
    modelFramework: "xgboost",
    samplesEvaluated: 1000,
    numericFeaturesEvaluated: 11,
    isSampled: true,
    topSensitiveFeature: "auth_attempts_24h",
    gaussianJitter5PctFlipRate: 0.048,
    meanProbabilityShift5Pct: 0.034,
    boundaryFlipRate: 0.145,
    medianBoundaryDistance: 0.285,
    subgroupRobustnessGap: 0.021,
    findingCount: 1,
    healthScore: 88.0,
    passed: true,
  };

  const fallbackBaseline = {
    sampleCount: 1000,
    positiveRate: 0.052,
    accuracy: 0.961,
    f1Score: 0.742,
    rocAuc: 0.895,
  };

  const fallbackNoiseLevels = [
    {
      level: 0.01,
      label: "1% Gaussian Noise",
      observationsEvaluated: 1000,
      flipCount: 12,
      flipRate: 0.012,
      meanProbabilityShift: 0.009,
      medianProbabilityShift: 0.005,
      maxProbabilityShift: 0.084,
      accuracy: 0.959,
      f1Score: 0.738,
      rocAuc: 0.893,
    },
    {
      level: 0.05,
      label: "5% Gaussian Noise",
      observationsEvaluated: 1000,
      flipCount: 48,
      flipRate: 0.048,
      meanProbabilityShift: 0.034,
      medianProbabilityShift: 0.021,
      maxProbabilityShift: 0.241,
      accuracy: 0.948,
      f1Score: 0.712,
      rocAuc: 0.881,
    },
    {
      level: 0.10,
      label: "10% Gaussian Noise",
      observationsEvaluated: 1000,
      flipCount: 94,
      flipRate: 0.094,
      meanProbabilityShift: 0.068,
      medianProbabilityShift: 0.046,
      maxProbabilityShift: 0.382,
      accuracy: 0.925,
      f1Score: 0.671,
      rocAuc: 0.862,
    },
  ];

  const fallbackFeatureSensitivity = [
    { feature: "auth_attempts_24h", perturbationScale: 0.124, observationsEvaluated: 1000, flipCount: 38, flipRate: 0.038, meanProbabilityShift: 0.029, medianProbabilityShift: 0.018, maxProbabilityShift: 0.214 },
    { feature: "tx_amount", perturbationScale: 14.82, observationsEvaluated: 1000, flipCount: 26, flipRate: 0.026, meanProbabilityShift: 0.021, medianProbabilityShift: 0.012, maxProbabilityShift: 0.185 },
    { feature: "distance_from_home_km", perturbationScale: 8.41, observationsEvaluated: 1000, flipCount: 18, flipRate: 0.018, meanProbabilityShift: 0.016, medianProbabilityShift: 0.009, maxProbabilityShift: 0.142 },
    { feature: "post_decision_risk_score", perturbationScale: 0.082, observationsEvaluated: 1000, flipCount: 15, flipRate: 0.015, meanProbabilityShift: 0.014, medianProbabilityShift: 0.008, maxProbabilityShift: 0.129 },
  ];

  const fallbackBoundarySearch = {
    method: "TREE_BOUNDARY_SEARCH",
    samplesTested: 200,
    successfulFlips: 29,
    unflippedCount: 171,
    flipRate: 0.145,
    medianNormalizedDistance: 0.285,
    minNormalizedDistance: 0.05,
    maxNormalizedDistance: 0.75,
    features: [
      { feature: "auth_attempts_24h", flipCount: 14, medianNormalizedDistance: 0.20 },
      { feature: "tx_amount", flipCount: 9, medianNormalizedDistance: 0.35 },
      { feature: "distance_from_home_km", flipCount: 6, medianNormalizedDistance: 0.50 },
    ],
  };

  const fallbackFgsm = {
    method: "FGSM",
    status: "NOT_APPLICABLE",
    reason: "Model adapter does not expose differentiable gradients. Bounded tree boundary search used instead.",
  };

  const fallbackMissingness = {
    status: "COMPLETED",
    levels: [
      { dropoutFraction: 0.05, label: "5% Missingness", flipCount: 18, flipRate: 0.018, meanProbabilityShift: 0.014, retainedAccuracy: 0.954 },
      { dropoutFraction: 0.10, label: "10% Missingness", flipCount: 39, flipRate: 0.039, meanProbabilityShift: 0.028, retainedAccuracy: 0.946 },
      { dropoutFraction: 0.20, label: "20% Missingness", flipCount: 78, flipRate: 0.078, meanProbabilityShift: 0.054, retainedAccuracy: 0.931 },
    ],
  };

  const fallbackSubgroup = {
    protectedAttribute: "is_foreign_ip",
    groups: [
      { group: "0 (Domestic)", sampleCount: 880, baselinePositiveRate: 0.051, noiseFlipRate5Pct: 0.045, meanProbabilityShift: 0.032 },
      { group: "1 (Foreign)", sampleCount: 120, baselinePositiveRate: 0.058, noiseFlipRate5Pct: 0.066, meanProbabilityShift: 0.045 },
    ],
    robustnessGap: 0.021,
  };

  const fallbackFindings = [
    {
      id: "ROBUSTNESS_SHALLOW_DECISION_MARGIN",
      category: "robustness",
      severity: "WARNING",
      title: "Narrow Decision Margin (Median Normalized Δ = 0.285)",
      description: "Evaluated samples cross the decision boundary within 28.5% normalized feature distance.",
      affected_features: ["auth_attempts_24h"],
      evidence: { medianNormalizedDistance: 0.285 },
      recommendation: "Audit features near the decision threshold for precision margin requirements.",
    },
  ];

  const summary = isRealData ? robustnessResult.summary : fallbackSummary;
  const baseline = isRealData ? robustnessResult.baseline || {} : fallbackBaseline;
  const noiseLevels = isRealData ? robustnessResult.noiseSensitivity?.levels || [] : fallbackNoiseLevels;
  const featureSensitivity = isRealData ? robustnessResult.featureSensitivity?.features || [] : fallbackFeatureSensitivity;
  const boundarySearch = isRealData ? robustnessResult.boundarySearch || {} : fallbackBoundarySearch;
  const fgsm = isRealData ? robustnessResult.fgsm || {} : fallbackFgsm;
  const missingness = isRealData ? robustnessResult.missingnessStress || {} : fallbackMissingness;
  const subgroup = isRealData ? robustnessResult.subgroupRobustness || {} : fallbackSubgroup;
  const findings = isRealData ? robustnessResult.findings || [] : fallbackFindings;

  const [activeTab, setActiveTab] = useState<"NOISE" | "FEATURES" | "BOUNDARY" | "STRESS" | "SUBGROUPS" | "FINDINGS">("NOISE");

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Header & Mode Badge */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center gap-2">
            <span>08 ADVERSARIAL &amp; PERTURBATION ROBUSTNESS</span>
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
            Empirical stress testing: Gaussian jitter sensitivity, feature instability ranking, tree boundary search, and missingness tolerance.
          </div>
        </div>

        <div className="flex items-center gap-2 text-[10px]">
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            MODEL: <span className="text-white font-bold">{summary?.modelName || "xgboost"}</span> ({summary?.modelFramework || "xgboost"})
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            SAMPLES: <span className="text-[#3b82f6] font-bold">{summary?.samplesEvaluated?.toLocaleString() || "0"}</span> {summary?.isSampled && "(sampled)"}
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            NUMERIC FEATS: <span className="text-white font-bold">{summary?.numericFeaturesEvaluated || featureSensitivity.length}</span>
          </div>
        </div>
      </div>

      {/* Forensic Disclaimer Banner */}
      <div className="p-2.5 bg-[#0e121a] border border-[#1e293b] text-[10px] text-[#94a3b8] flex items-start gap-2">
        <span className="text-[#3b82f6] font-bold shrink-0">[METHODOLOGY NOTICE]</span>
        <span>
          Tree-based models (such as XGBoost) utilize non-differentiable step-function decision boundaries.
          Local adversarial sensitivity is measured via bounded coordinate boundary search (TREE_BOUNDARY_SEARCH) and empirical Gaussian jitter rather than fabricated gradients.
        </span>
      </div>

      {/* KPI Summary Strip */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
        {/* 5% Gaussian Jitter Flip Rate */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">5% JITTER FLIP RATE</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.gaussianJitter5PctFlipRate !== undefined && summary.gaussianJitter5PctFlipRate >= 0.10
                ? "text-[#ef4444]"
                : summary?.gaussianJitter5PctFlipRate !== undefined && summary.gaussianJitter5PctFlipRate >= 0.05
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.gaussianJitter5PctFlipRate !== undefined
              ? `${(summary.gaussianJitter5PctFlipRate * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {summary?.gaussianJitter5PctFlipRate !== undefined && summary.gaussianJitter5PctFlipRate >= 0.05 ? "Unstable boundary" : "Resilient boundary"}
          </div>
        </div>

        {/* 5% Noise Mean Prob Shift */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">MEAN PROB SHIFT (5% NOISE)</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.meanProbabilityShift5Pct !== undefined && summary.meanProbabilityShift5Pct >= 0.10
                ? "text-[#ef4444]"
                : summary?.meanProbabilityShift5Pct !== undefined && summary.meanProbabilityShift5Pct >= 0.05
                ? "text-[#f59e0b]"
                : "text-white"
            }`}
          >
            {summary?.meanProbabilityShift5Pct !== undefined
              ? `${(summary.meanProbabilityShift5Pct * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Average Output Drift
          </div>
        </div>

        {/* Boundary Flip Rate */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">BOUNDARY FLIP RATE</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.boundaryFlipRate !== undefined && summary.boundaryFlipRate >= 0.30
                ? "text-[#ef4444]"
                : summary?.boundaryFlipRate !== undefined && summary.boundaryFlipRate >= 0.15
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.boundaryFlipRate !== undefined
              ? `${(summary.boundaryFlipRate * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Med Δ: {summary?.medianBoundaryDistance !== null && summary?.medianBoundaryDistance !== undefined ? `${(summary.medianBoundaryDistance * 100).toFixed(1)}% std` : "N/A"}
          </div>
        </div>

        {/* Top Sensitive Feature */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">TOP SENSITIVE FEATURE</div>
          <div className="text-sm font-bold text-[#3b82f6] mt-1 truncate" title={summary?.topSensitiveFeature || "none"}>
            {summary?.topSensitiveFeature || "none"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            Highest Flip Volatility
          </div>
        </div>

        {/* Subgroup Robustness Gap */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">SUBGROUP ROBUSTNESS GAP</div>
          <div
            className={`text-base font-bold mt-1 ${
              summary?.subgroupRobustnessGap !== null && summary?.subgroupRobustnessGap !== undefined && summary.subgroupRobustnessGap >= 0.10
                ? "text-[#ef4444]"
                : summary?.subgroupRobustnessGap !== null && summary?.subgroupRobustnessGap !== undefined && summary.subgroupRobustnessGap >= 0.05
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary?.subgroupRobustnessGap !== null && summary?.subgroupRobustnessGap !== undefined
              ? `${(summary.subgroupRobustnessGap * 100).toFixed(1)}%`
              : "N/A"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {subgroup.protectedAttribute ? `Slice: ${subgroup.protectedAttribute}` : "No Protected Attribute"}
          </div>
        </div>

        {/* Health Score */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">ROBUSTNESS HEALTH SCORE</div>
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
            {findings.length} Issue(s) Logged
          </div>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex border-b border-[#1f2533] gap-1 bg-[#0c0e14] p-1">
        <button
          onClick={() => setActiveTab("NOISE")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "NOISE"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          NOISE SENSITIVITY ({noiseLevels.length} LEVELS)
        </button>
        <button
          onClick={() => setActiveTab("FEATURES")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "FEATURES"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          FEATURE INSTABILITY RANKING ({featureSensitivity.length})
        </button>
        <button
          onClick={() => setActiveTab("BOUNDARY")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "BOUNDARY"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          DECISION BOUNDARY SEARCH
        </button>
        <button
          onClick={() => setActiveTab("STRESS")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "STRESS"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          FGSM &amp; MISSINGNESS STRESS
        </button>
        <button
          onClick={() => setActiveTab("SUBGROUPS")}
          className={`px-3 py-1.5 text-xs font-semibold cursor-pointer border ${
            activeTab === "SUBGROUPS"
              ? "bg-[#141822] text-[#3b82f6] border-[#3b82f6]"
              : "text-[#64748b] border-transparent hover:text-[#94a3b8]"
          }`}
        >
          SUBGROUP ROBUSTNESS
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

      {/* Tab 1: Noise Sensitivity */}
      {activeTab === "NOISE" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="flex justify-between items-center">
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              GAUSSIAN JITTER DEGRADATION PROFILE
            </div>
            <div className="text-[10px] text-[#64748b]">
              Baseline: Accuracy = {baseline.accuracy ? (baseline.accuracy * 100).toFixed(1) + "%" : "N/A"}, Positive Rate = {baseline.positiveRate ? (baseline.positiveRate * 100).toFixed(1) + "%" : "N/A"}
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase bg-[#090b0e]">
                  <th className="py-2 px-2.5">PERTURBATION LEVEL</th>
                  <th className="py-2 px-2.5 text-right">EVALUATED (N)</th>
                  <th className="py-2 px-2.5 text-right">FLIP COUNT</th>
                  <th className="py-2 px-2.5 text-right">PREDICTION FLIP RATE</th>
                  <th className="py-2 px-2.5 text-right">MEAN PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">MEDIAN PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">MAX PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">ACCURACY</th>
                  <th className="py-2 px-2.5 text-right">F1 SCORE</th>
                  <th className="py-2 px-2.5 text-right">ROC-AUC</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {noiseLevels.map((lvl: any, idx: number) => {
                  return (
                    <tr key={idx} className="hover:bg-[#12161f]">
                      <td className="py-2 px-2.5 font-bold text-white">
                        {lvl.label || `${lvl.level * 100}% Noise`}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {lvl.observationsEvaluated?.toLocaleString()}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#f1f3f8]">
                        {lvl.flipCount}
                      </td>
                      <td className="py-2 px-2.5 text-right font-bold">
                        <span className={lvl.flipRate >= 0.10 ? "text-[#ef4444]" : lvl.flipRate >= 0.05 ? "text-[#f59e0b]" : "text-[#10b981]"}>
                          {(lvl.flipRate * 100).toFixed(2)}%
                        </span>
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(lvl.meanProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#64748b]">
                        {(lvl.medianProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(lvl.maxProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-white">
                        {lvl.accuracy !== null && lvl.accuracy !== undefined ? (lvl.accuracy * 100).toFixed(1) + "%" : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {lvl.f1Score !== null && lvl.f1Score !== undefined ? lvl.f1Score.toFixed(3) : "N/A"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {lvl.rocAuc !== null && lvl.rocAuc !== undefined ? lvl.rocAuc.toFixed(3) : "N/A"}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Tab 2: Feature Sensitivity */}
      {activeTab === "FEATURES" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="flex justify-between items-center">
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              INDIVIDUAL FEATURE PERTURBATION SENSITIVITY (5% FEATURE JITTER)
            </div>
            <div className="text-[10px] text-[#64748b]">
              Ranked descending by flip rate and mean probability shift
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase bg-[#090b0e]">
                  <th className="py-2 px-2.5">FEATURE NAME</th>
                  <th className="py-2 px-2.5 text-right">SCALE (STD / IQR)</th>
                  <th className="py-2 px-2.5 text-right">FLIP COUNT</th>
                  <th className="py-2 px-2.5 text-right">PREDICTION FLIP RATE</th>
                  <th className="py-2 px-2.5 text-right">MEAN PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">MEDIAN PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">MAX PROB SHIFT</th>
                  <th className="py-2 px-2.5 text-right">INSTABILITY STATUS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {featureSensitivity.map((f: any, idx: number) => {
                  return (
                    <tr key={idx} className="hover:bg-[#12161f]">
                      <td className="py-2 px-2.5 font-bold text-white">
                        <span className="text-[#64748b] mr-2">#{idx + 1}</span>
                        {f.feature}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {f.perturbationScale ? f.perturbationScale.toFixed(4) : "1.0000"}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#f1f3f8]">
                        {f.flipCount}
                      </td>
                      <td className="py-2 px-2.5 text-right font-bold">
                        <span className={f.flipRate >= 0.05 ? "text-[#ef4444]" : f.flipRate >= 0.02 ? "text-[#f59e0b]" : "text-[#10b981]"}>
                          {(f.flipRate * 100).toFixed(2)}%
                        </span>
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(f.meanProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#64748b]">
                        {(f.medianProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#94a3b8]">
                        {(f.maxProbabilityShift * 100).toFixed(2)}%
                      </td>
                      <td className="py-2 px-2.5 text-right">
                        {f.flipRate >= 0.05 ? (
                          <span className="px-1.5 py-0.5 text-[8px] font-bold bg-[#7f1d1d] text-[#fca5a5] border border-[#ef4444]">
                            HIGH VOLATILITY
                          </span>
                        ) : f.flipRate >= 0.02 ? (
                          <span className="px-1.5 py-0.5 text-[8px] font-bold bg-[#78350f] text-[#fde047] border border-[#f59e0b]">
                            MODERATE
                          </span>
                        ) : (
                          <span className="px-1.5 py-0.5 text-[8px] font-bold bg-[#064e3b] text-[#34d399] border border-[#059669]">
                            STABLE
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

      {/* Tab 3: Decision Boundary Search */}
      {activeTab === "BOUNDARY" && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {/* Boundary Search Summary */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>LOCAL DECISION BOUNDARY PROXIMITY</span>
              <span className="text-[10px] text-[#3b82f6] font-bold">{boundarySearch.method || "TREE_BOUNDARY_SEARCH"}</span>
            </div>
            <div className="text-[11px] text-[#64748b]">
              Measures the minimal normalized feature perturbation <span className="text-white">Δ / scale</span> required to flip the predicted class.
            </div>

            <div className="p-3 bg-[#090b0e] border border-[#1f2533] space-y-2">
              <div className="flex justify-between items-center text-xs">
                <span className="text-[#94a3b8]">SAMPLES EVALUATED:</span>
                <span className="font-bold text-white">{boundarySearch.samplesTested}</span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-[#94a3b8]">SUCCESSFULLY FLIPPED:</span>
                <span className={`font-bold tabular-nums ${boundarySearch.flipRate >= 0.20 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                  {boundarySearch.successfulFlips} ({((boundarySearch.flipRate || 0) * 100).toFixed(1)}%)
                </span>
              </div>
              <div className="flex justify-between items-center text-xs">
                <span className="text-[#94a3b8]">UNFLIPPED WITHIN BOUNDS:</span>
                <span className="font-bold text-[#64748b]">{boundarySearch.unflippedCount}</span>
              </div>
              <div className="flex justify-between items-center text-xs pt-1 border-t border-[#1f2533]">
                <span className="text-white font-bold">MEDIAN NORMALIZED DISTANCE:</span>
                <span className={`font-bold tabular-nums ${boundarySearch.medianNormalizedDistance !== null && boundarySearch.medianNormalizedDistance <= 0.30 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
                  {boundarySearch.medianNormalizedDistance !== null && boundarySearch.medianNormalizedDistance !== undefined
                    ? `${(boundarySearch.medianNormalizedDistance * 100).toFixed(1)}% std`
                    : "N/A"}
                </span>
              </div>
            </div>
          </div>

          {/* Per-Feature Boundary Sensitivity */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              TOP BOUNDARY CROSSING CHANNELS
            </div>
            <div className="text-[11px] text-[#64748b]">
              Features that most frequently cross the decision threshold with minimal perturbation.
            </div>

            <div className="space-y-1.5">
              {(boundarySearch.features || []).map((bf: any, idx: number) => {
                return (
                  <div key={idx} className="p-2 bg-[#090b0e] border border-[#1f2533] flex justify-between items-center">
                    <div>
                      <div className="text-xs font-bold text-white">{bf.feature}</div>
                      <div className="text-[10px] text-[#64748b]">
                        Median Δ: {bf.medianNormalizedDistance !== null ? `${(bf.medianNormalizedDistance * 100).toFixed(1)}% std` : "N/A"}
                      </div>
                    </div>
                    <div className="text-right">
                      <span className="px-2 py-0.5 text-[9px] font-bold bg-[#141822] text-[#3b82f6] border border-[#222633]">
                        {bf.flipCount} Flips
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      )}

      {/* Tab 4: FGSM & Missingness Stress */}
      {activeTab === "STRESS" && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {/* FGSM Panel */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>FAST GRADIENT SIGN METHOD (FGSM)</span>
              <span className={`text-[9px] font-bold px-1.5 py-0.5 ${fgsm.status === "COMPLETED" ? "bg-[#064e3b] text-[#34d399]" : "bg-[#1e293b] text-[#94a3b8]"}`}>
                {fgsm.status || "NOT_APPLICABLE"}
              </span>
            </div>
            <div className="p-3 bg-[#090b0e] border border-[#1f2533] space-y-2">
              <div className="text-xs text-[#94a3b8]">
                {fgsm.reason || "Gradient interface is unavailable for tree-based models. Boundary sensitivity is evaluated via coordinate search."}
              </div>
            </div>
          </div>

          {/* Missingness Stress Panel */}
          <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
            <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
              <span>FEATURE DROPOUT / MISSINGNESS RESILIENCE</span>
              <span className={`text-[9px] font-bold px-1.5 py-0.5 ${missingness.status === "COMPLETED" ? "bg-[#064e3b] text-[#34d399]" : "bg-[#1e293b] text-[#94a3b8]"}`}>
                {missingness.status || "NOT_APPLICABLE"}
              </span>
            </div>

            {missingness.status === "COMPLETED" && missingness.levels ? (
              <div className="space-y-1.5">
                {missingness.levels.map((ml: any, idx: number) => {
                  return (
                    <div key={idx} className="p-2 bg-[#090b0e] border border-[#1f2533] flex justify-between items-center text-xs">
                      <div>
                        <span className="font-bold text-white">{ml.label}</span>
                        <span className="text-[10px] text-[#64748b] ml-2">
                          Mean Prob Shift: {(ml.meanProbabilityShift * 100).toFixed(1)}%
                        </span>
                      </div>
                      <div className="text-right">
                        <span className="text-[#10b981] font-bold">
                          {ml.retainedAccuracy ? `${(ml.retainedAccuracy * 100).toFixed(1)}% Acc` : `Flip Rate: ${(ml.flipRate * 100).toFixed(1)}%`}
                        </span>
                      </div>
                    </div>
                  );
                })}
              </div>
            ) : (
              <div className="p-3 bg-[#090b0e] border border-[#1f2533] text-xs text-[#94a3b8]">
                {missingness.reason || "Model architecture does not natively support missing feature values at inference time."}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Tab 5: Subgroup Robustness */}
      {activeTab === "SUBGROUPS" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
            <span>SUBGROUP NOISE STABILITY COMPARISON</span>
            <span className="text-[10px] text-[#64748b]">
              {subgroup.protectedAttribute ? `Slice: ${subgroup.protectedAttribute}` : "No Protected Attribute Configured"}
            </span>
          </div>

          {subgroup.groups && subgroup.groups.length > 0 ? (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-xs border-collapse">
                <thead>
                  <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase bg-[#090b0e]">
                    <th className="py-2 px-2.5">SUBGROUP</th>
                    <th className="py-2 px-2.5 text-right">SAMPLE COUNT (N)</th>
                    <th className="py-2 px-2.5 text-right">BASELINE POS RATE</th>
                    <th className="py-2 px-2.5 text-right">5% NOISE FLIP RATE</th>
                    <th className="py-2 px-2.5 text-right">MEAN PROB SHIFT</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-[#1f2533] tabular-nums">
                  {subgroup.groups.map((sg: any, idx: number) => {
                    return (
                      <tr key={idx} className="hover:bg-[#12161f]">
                        <td className="py-2 px-2.5 font-bold text-white">{sg.group}</td>
                        <td className="py-2 px-2.5 text-right text-[#94a3b8]">{sg.sampleCount?.toLocaleString()}</td>
                        <td className="py-2 px-2.5 text-right text-[#94a3b8]">{(sg.baselinePositiveRate * 100).toFixed(1)}%</td>
                        <td className="py-2 px-2.5 text-right font-bold text-white">{(sg.noiseFlipRate5Pct * 100).toFixed(2)}%</td>
                        <td className="py-2 px-2.5 text-right text-[#94a3b8]">{(sg.meanProbabilityShift * 100).toFixed(2)}%</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          ) : (
            <div className="p-4 bg-[#090b0e] border border-[#1f2533] text-center text-[#64748b]">
              NO PROTECTED ATTRIBUTE CONFIGURED FOR SUBGROUP ROBUSTNESS ANALYSIS
            </div>
          )}
        </div>
      )}

      {/* Tab 6: Forensic Findings */}
      {activeTab === "FINDINGS" && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex justify-between items-center">
            <span>STRUCTURED ROBUSTNESS &amp; SENSITIVITY FINDINGS</span>
            <span className="text-[10px] text-[#64748b]">{findings.length} Issue(s) Logged</span>
          </div>

          {findings.length === 0 ? (
            <div className="p-4 bg-[#090b0e] border border-[#1f2533] text-center text-[#10b981]">
              ✓ No critical perturbation vulnerabilities or decision margin anomalies detected.
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
