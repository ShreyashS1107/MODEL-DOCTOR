"use client";

import React, { useState } from "react";
import { FEATURE_NODES } from "@/lib/mockData";

export interface ExplainViewProps {
  explainResult?: any;
  status?: string;
  statusMessage?: string;
}

export const ExplainView: React.FC<ExplainViewProps> = ({ explainResult, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">MODEL EXPLAINABILITY &amp; ATTRIBUTION AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during TreeSHAP/Permutation feature attribution."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid attribution metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">EXPLAINABILITY ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = !!explainResult && explainResult.module === "EXPLAINABILITY";

  const summary = explainResult?.summary;
  const context = explainResult?.explanationContext;
  const globalImportance: any[] = explainResult?.globalImportance || [];
  const permutationImportance: any[] = explainResult?.permutationImportance || [];
  const importanceAgreement = explainResult?.importanceAgreement;
  const concentration = explainResult?.concentration;
  const localExplanations: any[] = explainResult?.localExplanations || [];
  const findings: any[] = explainResult?.findings || [];

  const [selectedFeature, setSelectedFeature] = useState<string | null>(
    globalImportance.length > 0 ? globalImportance[0].feature : null
  );
  const [selectedLocalIndex, setSelectedLocalIndex] = useState<number>(0);

  const activeLocalObs = localExplanations.length > 0
    ? localExplanations[Math.min(selectedLocalIndex, localExplanations.length - 1)]
    : null;

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* View Header & Real / Demo Indicator */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center gap-2">
            <span>06 MODEL EXPLAINABILITY &amp; FEATURE ATTRIBUTION</span>
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
            {isRealData
              ? `Real mathematical feature attribution evaluated via ${summary?.method || "TreeSHAP"} for model '${summary?.modelName || "target_model"}'.`
              : "TreeSHAP global attribution, second-order interaction values, and local prediction decomposition."}
          </div>
        </div>
        <div className="flex items-center gap-2 text-[10px]">
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            METHOD: <span className="text-white font-bold">{summary?.method || "TreeSHAP"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            BASE VALUE: <span className="text-white font-bold">{context?.baseValue ?? "-3.61"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#141822] text-[#94a3b8] border border-[#222633]">
            SAMPLES: <span className="text-[#3b82f6] font-bold">{summary?.sampleCount?.toLocaleString() || "2,000"}</span>
          </div>
        </div>
      </div>

      {/* KPI Summary Strip */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">ATTRIBUTION METHOD</div>
          <div className="text-sm font-bold text-white mt-1">{summary?.method || "TREE_SHAP"}</div>
          <div className="text-[10px] text-[#94a3b8] mt-0.5">{context?.modelOutput || "raw"} margin space</div>
        </div>

        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">TOP ATTRIBUTED FEATURE</div>
          <div className="text-sm font-bold text-[#3b82f6] mt-1 truncate" title={summary?.topFeature || "post_decision_risk_score"}>
            {summary?.topFeature || "post_decision_risk_score"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            mean |SHAP| = {summary?.topFeatureMeanAbsShap ? summary.topFeatureMeanAbsShap.toFixed(4) : "1.9142"}
          </div>
        </div>

        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">TOP 3 ATTRIBUTION SHARE</div>
          <div className={`text-sm font-bold mt-1 ${summary?.top3AttributionShare && summary.top3AttributionShare >= 0.8 ? "text-[#ef4444]" : summary?.top3AttributionShare && summary.top3AttributionShare >= 0.65 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
            {summary?.top3AttributionShare ? (summary.top3AttributionShare * 100).toFixed(1) : "92.4"}%
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {summary?.top3AttributionShare && summary.top3AttributionShare >= 0.65 ? "High concentration" : "Balanced dispersion"}
          </div>
        </div>

        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">RANK AGREEMENT (SPEARMAN)</div>
          <div className={`text-sm font-bold mt-1 ${summary?.importanceAgreementSpearman !== null && summary?.importanceAgreementSpearman !== undefined && summary.importanceAgreementSpearman < 0.3 ? "text-[#f59e0b]" : "text-[#10b981]"}`}>
            {summary?.importanceAgreementSpearman !== null && summary?.importanceAgreementSpearman !== undefined
              ? `ρ = ${summary.importanceAgreementSpearman.toFixed(2)}`
              : "ρ = 0.86"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">SHAP vs Permutation</div>
        </div>

        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">ATTRIBUTION ENTROPY</div>
          <div className="text-sm font-bold text-[#f1f3f8] mt-1">
            {summary?.normalizedEntropy ? `H = ${summary.normalizedEntropy.toFixed(2)}` : "H = 0.58"}
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">Normalized [0, 1]</div>
        </div>

        <div className="border border-[#1f2533] bg-[#0c0e14] p-2.5">
          <div className="text-[10px] text-[#64748b] uppercase">EXPLAINABILITY HEALTH</div>
          <div className={`text-sm font-bold mt-1 ${(summary?.healthScore ?? 85) >= 80 ? "text-[#10b981]" : (summary?.healthScore ?? 85) >= 60 ? "text-[#f59e0b]" : "text-[#ef4444]"}`}>
            {summary?.healthScore ? summary.healthScore.toFixed(0) : "85"}/100
          </div>
          <div className="text-[10px] text-[#64748b] mt-0.5">
            {summary?.findingCount ?? findings.length} issue(s) flagged
          </div>
        </div>
      </div>

      {/* Global Feature Attribution Ranking Table */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
        <div className="flex items-center justify-between">
          <div className="text-xs text-[#94a3b8] uppercase font-bold">
            GLOBAL FEATURE ATTRIBUTION RANKING (TREE SHAP)
          </div>
          <div className="text-[10px] text-[#64748b]">
            Ranked by mean absolute attribution |Φ| across {summary?.sampleCount?.toLocaleString() || "2,000"} observations
          </div>
        </div>

        {isRealData && globalImportance.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] text-[10px] text-[#64748b] uppercase bg-[#090b0e]">
                  <th className="py-2 px-2.5 w-12">RANK</th>
                  <th className="py-2 px-2.5">FEATURE NAME</th>
                  <th className="py-2 px-2.5 text-right w-28">MEAN |SHAP|</th>
                  <th className="py-2 px-2.5 text-right w-28">MEAN SIGNED</th>
                  <th className="py-2 px-2.5 text-right w-24">+ CONTRIB %</th>
                  <th className="py-2 px-2.5 text-right w-24">- CONTRIB %</th>
                  <th className="py-2 px-2.5 w-48">ATTRIBUTION SHARE</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#161b26] text-[11px]">
                {globalImportance.map((item) => {
                  const isSelected = selectedFeature === item.feature;
                  const totalImp = globalImportance.reduce((acc, cur) => acc + cur.meanAbsShap, 0);
                  const sharePct = totalImp > 0 ? (item.meanAbsShap / totalImp) * 100 : 0;
                  const isSuspicious = item.feature.includes("id") || item.feature.includes("hash") || sharePct > 40;

                  return (
                    <tr
                      key={item.feature}
                      onClick={() => setSelectedFeature(item.feature)}
                      className={`cursor-pointer transition-colors ${
                        isSelected ? "bg-[#181e2b] text-white" : "hover:bg-[#12161f] text-[#cbd5e1]"
                      }`}
                    >
                      <td className="py-2 px-2.5 font-bold text-[#64748b]">{item.rank}</td>
                      <td className="py-2 px-2.5 font-medium">
                        <div className="flex items-center gap-1.5">
                          <span className={isSuspicious ? "text-[#f59e0b] font-bold" : ""}>{item.feature}</span>
                          {isSuspicious && (
                            <span className="text-[9px] px-1 bg-[#451a03] text-[#f59e0b] border border-[#78350f]">
                              INVESTIGATE
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-2 px-2.5 text-right font-mono font-bold text-white">
                        {item.meanAbsShap.toFixed(4)}
                      </td>
                      <td className={`py-2 px-2.5 text-right font-mono ${item.meanSignedShap > 0 ? "text-[#3b82f6]" : item.meanSignedShap < 0 ? "text-[#ef4444]" : "text-[#64748b]"}`}>
                        {item.meanSignedShap > 0 ? `+${item.meanSignedShap.toFixed(4)}` : item.meanSignedShap.toFixed(4)}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#3b82f6] font-mono">
                        {(item.positiveContributionRate * 100).toFixed(0)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#ef4444] font-mono">
                        {(item.negativeContributionRate * 100).toFixed(0)}%
                      </td>
                      <td className="py-2 px-2.5">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 bg-[#141822] border border-[#1f2533] overflow-hidden">
                            <div
                              className={isSuspicious ? "bg-[#f59e0b] h-full" : "bg-[#3b82f6] h-full"}
                              style={{ width: `${Math.min(100, sharePct)}%` }}
                            />
                          </div>
                          <span className="text-[10px] text-[#64748b] w-10 text-right font-mono">
                            {sharePct.toFixed(1)}%
                          </span>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : (
          /* Demo Fallback Global Importance */
          <div className="space-y-2">
            {FEATURE_NODES.slice()
              .sort((a, b) => b.shapImportance - a.shapImportance)
              .map((f) => (
                <div key={f.id}>
                  <div className="flex justify-between text-[11px] mb-1">
                    <span className={f.severity === "CRITICAL" ? "text-[#ef4444] font-bold" : "text-[#f1f3f8]"}>
                      {f.name} {f.severity === "CRITICAL" && "(SUSPECT LEAKAGE PROXY)"}
                    </span>
                    <span className="text-[#94a3b8] tabular-nums">
                      mean |SHAP| = {(f.shapImportance * 100).toFixed(1)}%
                    </span>
                  </div>
                  <div className="w-full h-2 bg-[#141822] border border-[#1f2533] overflow-hidden">
                    <div
                      className={f.severity === "CRITICAL" ? "bg-[#ef4444] h-full" : "bg-[#3b82f6] h-full"}
                      style={{ width: `${f.shapImportance * 100 * 2.2}%` }}
                    />
                  </div>
                </div>
              ))}
          </div>
        )}
      </div>

      {/* Two-Column Grid: Method Agreement & Concentration Metrics */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {/* Method Agreement: SHAP vs Permutation Importance */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="flex items-center justify-between">
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              METHOD AGREEMENT (SHAP VS PERMUTATION)
            </div>
            <div className="text-[10px] text-[#64748b]">
              Spearman Rank Correlation: <span className="text-[#34d399] font-bold">{importanceAgreement?.spearmanCorrelation !== null && importanceAgreement?.spearmanCorrelation !== undefined ? `ρ = ${importanceAgreement.spearmanCorrelation.toFixed(2)}` : "ρ = 0.86"}</span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-[11px]">
              <thead>
                <tr className="border-b border-[#1f2533] text-[10px] text-[#64748b] uppercase">
                  <th className="py-1.5 px-2">FEATURE</th>
                  <th className="py-1.5 px-2 text-right">SHAP RANK</th>
                  <th className="py-1.5 px-2 text-right">PERM RANK</th>
                  <th className="py-1.5 px-2 text-right">PERM IMPORTANCE (Δ AUC)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#161b26]">
                {(permutationImportance.length > 0 ? permutationImportance.slice(0, 7) : [
                  { feature: "post_decision_risk_score", rank: 1, importanceMean: 0.1842, importanceStd: 0.012 },
                  { feature: "transaction_id_hash", rank: 2, importanceMean: 0.0921, importanceStd: 0.008 },
                  { feature: "transaction_amount", rank: 3, importanceMean: 0.0345, importanceStd: 0.004 },
                  { feature: "user_income", rank: 4, importanceMean: 0.0121, importanceStd: 0.002 },
                  { feature: "device_trust_score", rank: 5, importanceMean: 0.0084, importanceStd: 0.001 },
                ]).map((pItem) => {
                  const shapRank = globalImportance.find((g) => g.feature === pItem.feature)?.rank ?? pItem.rank;
                  const rankDelta = Math.abs(shapRank - pItem.rank);

                  return (
                    <tr key={pItem.feature} className="hover:bg-[#12161f]">
                      <td className="py-1.5 px-2 font-medium text-[#cbd5e1]">{pItem.feature}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-[#3b82f6] font-bold">#{shapRank}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-[#10b981] font-bold">#{pItem.rank}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-white">
                        +{pItem.importanceMean.toFixed(4)} <span className="text-[#64748b] text-[9px]">(±{pItem.importanceStd?.toFixed(3) || "0.005"})</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Attribution Concentration & Entropy */}
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
          <div className="text-xs text-[#94a3b8] uppercase font-bold">
            ATTRIBUTION CONCENTRATION &amp; DECISION ENTROPY
          </div>

          <div className="space-y-2.5">
            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#94a3b8]">TOP 1 FEATURE SHARE</span>
                <span className="font-mono font-bold text-white">
                  {concentration?.top1Share ? (concentration.top1Share * 100).toFixed(1) : "58.2"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#141822] border border-[#1f2533]">
                <div
                  className={concentration?.top1Share && concentration.top1Share >= 0.5 ? "bg-[#ef4444] h-full" : "bg-[#3b82f6] h-full"}
                  style={{ width: `${(concentration?.top1Share || 0.582) * 100}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#94a3b8]">TOP 3 FEATURES SHARE</span>
                <span className="font-mono font-bold text-white">
                  {concentration?.top3Share ? (concentration.top3Share * 100).toFixed(1) : "92.4"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#141822] border border-[#1f2533]">
                <div
                  className={concentration?.top3Share && concentration.top3Share >= 0.8 ? "bg-[#ef4444] h-full" : "bg-[#3b82f6] h-full"}
                  style={{ width: `${(concentration?.top3Share || 0.924) * 100}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#94a3b8]">TOP 5 FEATURES SHARE</span>
                <span className="font-mono font-bold text-white">
                  {concentration?.top5Share ? (concentration.top5Share * 100).toFixed(1) : "98.1"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#141822] border border-[#1f2533]">
                <div
                  className="bg-[#3b82f6] h-full"
                  style={{ width: `${(concentration?.top5Share || 0.981) * 100}%` }}
                />
              </div>
            </div>

            <div className="p-2 border border-[#1f2533] bg-[#090b0e] text-[10px] text-[#64748b]">
              <span className="text-[#f1f3f8] font-bold">INTERPRETATION NOTE:</span> Feature attributions describe
              the mathematical behavior and decision boundaries of the trained model. They do not constitute causal mechanisms.
            </div>
          </div>
        </div>
      </div>

      {/* Local Prediction Explanation Inspector (Waterfall Decomposition) */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <div className="text-xs text-[#94a3b8] uppercase font-bold">
              LOCAL PREDICTION EXPLANATION INSPECTOR
            </div>
            <div className="text-[11px] text-[#64748b]">
              Additive feature decomposition (Base Value + Σ SHAP = Model Output) for 5 representative observations
            </div>
          </div>

          {/* Observation Selector Tabs */}
          {localExplanations.length > 0 && (
            <div className="flex items-center gap-1">
              {localExplanations.map((obs, idx) => {
                const isSelected = selectedLocalIndex === idx;
                const label = obs.observationType === "MAX_PROBABILITY" ? "MAX PROB"
                  : obs.observationType === "MIN_PROBABILITY" ? "MIN PROB"
                  : obs.observationType === "TRUE_POSITIVE" ? "TRUE POS"
                  : obs.observationType === "TRUE_NEGATIVE" ? "TRUE NEG"
                  : "BORDERLINE 0.50";

                return (
                  <button
                    key={idx}
                    onClick={() => setSelectedLocalIndex(idx)}
                    className={`px-2 py-1 text-[10px] font-mono transition-colors ${
                      isSelected
                        ? "bg-[#181d28] text-[#3b82f6] font-bold border border-[#3b82f6]"
                        : "bg-[#12161f] text-[#64748b] hover:text-[#f1f3f8] border border-[#1f2533]"
                    }`}
                  >
                    {label}
                  </button>
                );
              })}
            </div>
          )}
        </div>

        {activeLocalObs ? (
          <div className="space-y-3">
            {/* Observation Header Metrics */}
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 bg-[#090b0e] p-2.5 border border-[#1f2533] text-[11px]">
              <div>
                <span className="text-[#64748b]">OBSERVATION TYPE:</span>{" "}
                <span className="font-bold text-white">{activeLocalObs.observationType}</span>
              </div>
              <div>
                <span className="text-[#64748b]">ROW INDEX:</span>{" "}
                <span className="font-mono text-white">#{activeLocalObs.rowIndex}</span>
              </div>
              <div>
                <span className="text-[#64748b]">MODEL PREDICTION:</span>{" "}
                <span className={`font-mono font-bold ${activeLocalObs.prediction >= 0.5 ? "text-[#ef4444]" : "text-[#10b981]"}`}>
                  {(activeLocalObs.prediction * 100).toFixed(2)}%
                </span>
              </div>
              <div>
                <span className="text-[#64748b]">GROUND TRUTH:</span>{" "}
                <span className="font-bold text-white">Class {activeLocalObs.actual}</span>
              </div>
            </div>

            {/* Waterfall-style Top Contributors */}
            <div className="space-y-1.5">
              <div className="text-[10px] text-[#64748b] uppercase mb-1">
                TOP CONTRIBUTING FEATURES (ADDITIVE SHAP VALUES)
              </div>

              {activeLocalObs.topContributors.map((c: any, cIdx: number) => {
                const isPositive = c.direction === "POSITIVE" || c.shapValue >= 0;
                const absVal = Math.abs(c.shapValue);

                return (
                  <div key={cIdx} className="flex items-center justify-between text-[11px] p-1.5 bg-[#090b0e] border border-[#161b26]">
                    <div className="w-1/3 flex items-center gap-2 truncate">
                      <span className="font-medium text-[#f1f3f8] truncate">{c.feature}</span>
                      <span className="text-[10px] text-[#64748b]">={typeof c.value === "number" ? c.value.toFixed(2) : String(c.value)}</span>
                    </div>

                    <div className="w-1/3 px-2">
                      <div className="flex items-center h-3 bg-[#141822] border border-[#1f2533]">
                        <div
                          className={isPositive ? "bg-[#3b82f6] h-full ml-auto" : "bg-[#ef4444] h-full mr-auto"}
                          style={{ width: `${Math.min(100, absVal * 40)}%` }}
                        />
                      </div>
                    </div>

                    <div className="w-1/4 text-right font-mono font-bold">
                      <span className={isPositive ? "text-[#3b82f6]" : "text-[#ef4444]"}>
                        {isPositive ? `+${c.shapValue.toFixed(4)}` : c.shapValue.toFixed(4)}
                      </span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        ) : (
          /* Demo Fallback Local Explanation */
          <div className="p-3 bg-[#090b0e] border border-[#1f2533] text-[11px] space-y-2">
            <div className="flex justify-between">
              <span className="text-[#64748b]">DEMO OBSERVATION #127 (Prediction: 91.3% | Actual: 1)</span>
              <span className="text-[#94a3b8]">Base Value: -3.61</span>
            </div>
            <div className="space-y-1 text-xs">
              <div className="flex justify-between">
                <span>post_decision_risk_score = 0.94</span>
                <span className="text-[#3b82f6] font-bold font-mono">+1.9142</span>
              </div>
              <div className="flex justify-between">
                <span>transaction_id_hash = 0.88</span>
                <span className="text-[#3b82f6] font-bold font-mono">+1.1152</span>
              </div>
              <div className="flex justify-between">
                <span>transaction_amount = $1,420.00</span>
                <span className="text-[#ef4444] font-bold font-mono">-0.1240</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Structured Forensic Findings */}
      {findings.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2">
          <div className="text-xs text-[#94a3b8] uppercase font-bold">
            EXPLAINABILITY FORENSIC FINDINGS ({findings.length})
          </div>

          <div className="space-y-2">
            {findings.map((f: any, idx: number) => {
              const isCrit = f.severity === "CRITICAL";
              const isHigh = f.severity === "HIGH";
              const isWarn = f.severity === "WARNING";

              return (
                <div
                  key={idx}
                  className={`p-2.5 border text-xs ${
                    isCrit
                      ? "border-[#ef4444] bg-[#450a0a]/30 text-[#fca5a5]"
                      : isHigh
                      ? "border-[#f59e0b] bg-[#451a03]/30 text-[#fcd34d]"
                      : isWarn
                      ? "border-[#eab308] bg-[#422006]/30 text-[#fde047]"
                      : "border-[#1f2533] bg-[#141822] text-[#94a3b8]"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-white flex items-center gap-2">
                      <span className={`px-1 text-[9px] font-bold ${
                        isCrit ? "bg-[#ef4444] text-black" : isHigh ? "bg-[#f59e0b] text-black" : "bg-[#eab308] text-black"
                      }`}>
                        {f.severity}
                      </span>
                      {f.title}
                    </span>
                    <span className="text-[10px] font-mono text-[#64748b]">{f.id}</span>
                  </div>
                  <p className="text-[11px] leading-relaxed text-[#cbd5e1]">{f.description}</p>
                  {f.recommendation && (
                    <div className="mt-1.5 text-[10px] text-[#94a3b8] border-t border-[#1f2533] pt-1">
                      <span className="text-[#3b82f6] font-bold">REMEDIATION:</span> {f.recommendation}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
};
