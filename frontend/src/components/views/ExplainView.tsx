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
        <div className="p-4 border border-[#F18B78] bg-[#332B3C] text-[#F18B78] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#F18B78] text-[#211D29]">FAILED</span>
            <span className="text-sm font-bold text-[#F4EFF6] uppercase">MODEL EXPLAINABILITY &amp; ATTRIBUTION AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#F18B78]/90">
            {statusMessage || "Diagnostic engine reported an execution failure during TreeSHAP/Permutation feature attribution."}
          </p>
          <div className="text-[10px] text-[#B7ACBD] pt-1 border-t border-[#443849]">
            Engine execution terminated without producing valid attribution metrics. Synthetic demo data is suppressed for failed live runs.
          </div>
        </div>
      </div>
    );
  }

  if (status === "NOT_IMPLEMENTED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#E8B66D] bg-[#332B3C] text-[#E8B66D] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#E8B66D] text-[#211D29]">DEFERRED</span>
            <span className="text-sm font-bold text-[#F4EFF6] uppercase">EXPLAINABILITY ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#E8B66D]/90">
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
      <div className="p-3 border border-[#443849] bg-[#2A2432] flex flex-wrap items-center justify-between gap-2">
        <div>
          <div className="text-xs text-[#B7ACBD] uppercase font-bold flex items-center gap-2">
            <span>06 MODEL EXPLAINABILITY &amp; FEATURE ATTRIBUTION</span>
            {isRealData ? (
              <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#B7D7A8]/20 text-[#B7D7A8] border border-[#B7D7A8]/40">
                LIVE RUN AUDIT ACTIVE
              </span>
            ) : (
              <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#332B3C] text-[#B7ACBD] border border-[#443849]">
                DEMO / SYNTHETIC FIXTURE FALLBACK
              </span>
            )}
          </div>
          <div className="text-[11px] text-[#8E8295]">
            {isRealData
              ? `Real mathematical feature attribution evaluated via ${summary?.method || "TreeSHAP"} for model '${summary?.modelName || "target_model"}'.`
              : "TreeSHAP global attribution, second-order interaction values, and local prediction decomposition."}
          </div>
        </div>
        <div className="flex items-center gap-2 text-[10px]">
          <div className="px-2 py-0.5 bg-[#332B3C] text-[#B7ACBD] border border-[#443849]">
            METHOD: <span className="text-[#F4EFF6] font-bold">{summary?.method || "TreeSHAP"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#332B3C] text-[#B7ACBD] border border-[#443849]">
            BASE VALUE: <span className="text-[#F4EFF6] font-bold">{context?.baseValue ?? "-3.61"}</span>
          </div>
          <div className="px-2 py-0.5 bg-[#332B3C] text-[#B7ACBD] border border-[#443849]">
            SAMPLES: <span className="text-[#F18B78] font-bold">{summary?.sampleCount?.toLocaleString() || "2,000"}</span>
          </div>
        </div>
      </div>

      {/* KPI Summary Strip */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-2">
        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">ATTRIBUTION METHOD</div>
          <div className="text-sm font-bold text-[#F4EFF6] mt-1">{summary?.method || "TREE_SHAP"}</div>
          <div className="text-[10px] text-[#B7ACBD] mt-0.5">{context?.modelOutput || "raw"} margin space</div>
        </div>

        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">TOP ATTRIBUTED FEATURE</div>
          <div className="text-sm font-bold text-[#F18B78] mt-1 truncate" title={summary?.topFeature || "post_decision_risk_score"}>
            {summary?.topFeature || "post_decision_risk_score"}
          </div>
          <div className="text-[10px] text-[#8E8295] mt-0.5">
            mean |SHAP| = {summary?.topFeatureMeanAbsShap ? summary.topFeatureMeanAbsShap.toFixed(4) : "1.9142"}
          </div>
        </div>

        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">TOP 3 ATTRIBUTION SHARE</div>
          <div className={`text-sm font-bold mt-1 ${summary?.top3AttributionShare && summary.top3AttributionShare >= 0.8 ? "text-[#F18B78]" : summary?.top3AttributionShare && summary.top3AttributionShare >= 0.65 ? "text-[#E8B66D]" : "text-[#B7D7A8]"}`}>
            {summary?.top3AttributionShare ? (summary.top3AttributionShare * 100).toFixed(1) : "92.4"}%
          </div>
          <div className="text-[10px] text-[#8E8295] mt-0.5">
            {summary?.top3AttributionShare && summary.top3AttributionShare >= 0.65 ? "High concentration" : "Balanced dispersion"}
          </div>
        </div>

        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">RANK AGREEMENT (SPEARMAN)</div>
          <div className={`text-sm font-bold mt-1 ${summary?.importanceAgreementSpearman !== null && summary?.importanceAgreementSpearman !== undefined && summary.importanceAgreementSpearman < 0.3 ? "text-[#E8B66D]" : "text-[#B7D7A8]"}`}>
            {summary?.importanceAgreementSpearman !== null && summary?.importanceAgreementSpearman !== undefined
              ? `ρ = ${summary.importanceAgreementSpearman.toFixed(2)}`
              : "ρ = 0.86"}
          </div>
          <div className="text-[10px] text-[#8E8295] mt-0.5">SHAP vs Permutation</div>
        </div>

        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">ATTRIBUTION ENTROPY</div>
          <div className="text-sm font-bold text-[#F4EFF6] mt-1">
            {summary?.normalizedEntropy ? `H = ${summary.normalizedEntropy.toFixed(2)}` : "H = 0.58"}
          </div>
          <div className="text-[10px] text-[#8E8295] mt-0.5">Normalized [0, 1]</div>
        </div>

        <div className="border border-[#443849] bg-[#2A2432] p-2.5">
          <div className="text-[10px] text-[#8E8295] uppercase">EXPLAINABILITY HEALTH</div>
          <div className={`text-sm font-bold mt-1 ${(summary?.healthScore ?? 85) >= 80 ? "text-[#B7D7A8]" : (summary?.healthScore ?? 85) >= 60 ? "text-[#E8B66D]" : "text-[#F18B78]"}`}>
            {summary?.healthScore ? summary.healthScore.toFixed(0) : "85"}/100
          </div>
          <div className="text-[10px] text-[#8E8295] mt-0.5">
            {summary?.findingCount ?? findings.length} issue(s) flagged
          </div>
        </div>
      </div>

      {/* Global Feature Attribution Ranking Table */}
      <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-3">
        <div className="flex items-center justify-between">
          <div className="text-xs text-[#B7ACBD] uppercase font-bold">
            GLOBAL FEATURE ATTRIBUTION RANKING (TREE SHAP)
          </div>
          <div className="text-[10px] text-[#8E8295]">
            Ranked by mean absolute attribution |Φ| across {summary?.sampleCount?.toLocaleString() || "2,000"} observations
          </div>
        </div>

        {isRealData && globalImportance.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#443849] text-[10px] text-[#8E8295] uppercase bg-[#1C1824]">
                  <th className="py-2 px-2.5 w-12">RANK</th>
                  <th className="py-2 px-2.5">FEATURE NAME</th>
                  <th className="py-2 px-2.5 text-right w-28">MEAN |SHAP|</th>
                  <th className="py-2 px-2.5 text-right w-28">MEAN SIGNED</th>
                  <th className="py-2 px-2.5 text-right w-24">+ CONTRIB %</th>
                  <th className="py-2 px-2.5 text-right w-24">- CONTRIB %</th>
                  <th className="py-2 px-2.5 w-48">ATTRIBUTION SHARE</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#443849] text-[11px]">
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
                        isSelected ? "bg-[#332B3C] text-[#F4EFF6]" : "hover:bg-[#332B3C] text-[#B7ACBD]"
                      }`}
                    >
                      <td className="py-2 px-2.5 font-bold text-[#8E8295]">{item.rank}</td>
                      <td className="py-2 px-2.5 font-medium">
                        <div className="flex items-center gap-1.5">
                          <span className={isSuspicious ? "text-[#E8B66D] font-bold" : "text-[#F4EFF6]"}>{item.feature}</span>
                          {isSuspicious && (
                            <span className="text-[9px] px-1 bg-[#E8B66D]/20 text-[#E8B66D] border border-[#E8B66D]/40">
                              INVESTIGATE
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="py-2 px-2.5 text-right font-mono font-bold text-[#F4EFF6]">
                        {item.meanAbsShap.toFixed(4)}
                      </td>
                      <td className={`py-2 px-2.5 text-right font-mono ${item.meanSignedShap > 0 ? "text-[#F18B78]" : item.meanSignedShap < 0 ? "text-[#E8B66D]" : "text-[#8E8295]"}`}>
                        {item.meanSignedShap > 0 ? `+${item.meanSignedShap.toFixed(4)}` : item.meanSignedShap.toFixed(4)}
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#F18B78] font-mono">
                        {(item.positiveContributionRate * 100).toFixed(0)}%
                      </td>
                      <td className="py-2 px-2.5 text-right text-[#E8B66D] font-mono">
                        {(item.negativeContributionRate * 100).toFixed(0)}%
                      </td>
                      <td className="py-2 px-2.5">
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 bg-[#1C1824] border border-[#443849] overflow-hidden">
                            <div
                              className={isSuspicious ? "bg-[#E8B66D] h-full" : "bg-[#F18B78] h-full"}
                              style={{ width: `${Math.min(100, sharePct)}%` }}
                            />
                          </div>
                          <span className="text-[10px] text-[#8E8295] w-10 text-right font-mono">
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
                    <span className={f.severity === "CRITICAL" ? "text-[#F18B78] font-bold" : "text-[#F4EFF6]"}>
                      {f.name} {f.severity === "CRITICAL" && "(SUSPECT LEAKAGE PROXY)"}
                    </span>
                    <span className="text-[#B7ACBD] tabular-nums">
                      mean |SHAP| = {(f.shapImportance * 100).toFixed(1)}%
                    </span>
                  </div>
                  <div className="w-full h-2 bg-[#1C1824] border border-[#443849] overflow-hidden">
                    <div
                      className={f.severity === "CRITICAL" ? "bg-[#F18B78] h-full" : "bg-[#B7D7A8] h-full"}
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
        <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-3">
          <div className="flex items-center justify-between">
            <div className="text-xs text-[#B7ACBD] uppercase font-bold">
              METHOD AGREEMENT (SHAP VS PERMUTATION)
            </div>
            <div className="text-[10px] text-[#8E8295]">
              Spearman Rank Correlation: <span className="text-[#B7D7A8] font-bold">{importanceAgreement?.spearmanCorrelation !== null && importanceAgreement?.spearmanCorrelation !== undefined ? `ρ = ${importanceAgreement.spearmanCorrelation.toFixed(2)}` : "ρ = 0.86"}</span>
            </div>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse text-[11px]">
              <thead>
                <tr className="border-b border-[#443849] text-[10px] text-[#8E8295] uppercase">
                  <th className="py-1.5 px-2">FEATURE</th>
                  <th className="py-1.5 px-2 text-right">SHAP RANK</th>
                  <th className="py-1.5 px-2 text-right">PERM RANK</th>
                  <th className="py-1.5 px-2 text-right">PERM IMPORTANCE (Δ AUC)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#443849]">
                {(permutationImportance.length > 0 ? permutationImportance.slice(0, 7) : [
                  { feature: "post_decision_risk_score", rank: 1, importanceMean: 0.1842, importanceStd: 0.012 },
                  { feature: "transaction_id_hash", rank: 2, importanceMean: 0.0921, importanceStd: 0.008 },
                  { feature: "transaction_amount", rank: 3, importanceMean: 0.0345, importanceStd: 0.004 },
                  { feature: "user_income", rank: 4, importanceMean: 0.0121, importanceStd: 0.002 },
                  { feature: "device_trust_score", rank: 5, importanceMean: 0.0084, importanceStd: 0.001 },
                ]).map((pItem) => {
                  const shapRank = globalImportance.find((g) => g.feature === pItem.feature)?.rank ?? pItem.rank;

                  return (
                    <tr key={pItem.feature} className="hover:bg-[#332B3C]">
                      <td className="py-1.5 px-2 font-medium text-[#F4EFF6]">{pItem.feature}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-[#F18B78] font-bold">#{shapRank}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-[#B7D7A8] font-bold">#{pItem.rank}</td>
                      <td className="py-1.5 px-2 text-right font-mono text-[#F4EFF6]">
                        +{pItem.importanceMean.toFixed(4)} <span className="text-[#8E8295] text-[9px]">(±{pItem.importanceStd?.toFixed(3) || "0.005"})</span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>

        {/* Attribution Concentration & Entropy */}
        <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-3">
          <div className="text-xs text-[#B7ACBD] uppercase font-bold">
            ATTRIBUTION CONCENTRATION &amp; DECISION ENTROPY
          </div>

          <div className="space-y-2.5">
            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#B7ACBD]">TOP 1 FEATURE SHARE</span>
                <span className="font-mono font-bold text-[#F4EFF6]">
                  {concentration?.top1Share ? (concentration.top1Share * 100).toFixed(1) : "58.2"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#1C1824] border border-[#443849]">
                <div
                  className={concentration?.top1Share && concentration.top1Share >= 0.5 ? "bg-[#F18B78] h-full" : "bg-[#B7D7A8] h-full"}
                  style={{ width: `${(concentration?.top1Share || 0.582) * 100}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#B7ACBD]">TOP 3 FEATURES SHARE</span>
                <span className="font-mono font-bold text-[#F4EFF6]">
                  {concentration?.top3Share ? (concentration.top3Share * 100).toFixed(1) : "92.4"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#1C1824] border border-[#443849]">
                <div
                  className={concentration?.top3Share && concentration.top3Share >= 0.8 ? "bg-[#F18B78] h-full" : "bg-[#B7D7A8] h-full"}
                  style={{ width: `${(concentration?.top3Share || 0.924) * 100}%` }}
                />
              </div>
            </div>

            <div>
              <div className="flex justify-between text-[11px] mb-1">
                <span className="text-[#B7ACBD]">TOP 5 FEATURES SHARE</span>
                <span className="font-mono font-bold text-[#F4EFF6]">
                  {concentration?.top5Share ? (concentration.top5Share * 100).toFixed(1) : "98.1"}%
                </span>
              </div>
              <div className="w-full h-2 bg-[#1C1824] border border-[#443849]">
                <div
                  className="bg-[#B7D7A8] h-full"
                  style={{ width: `${(concentration?.top5Share || 0.981) * 100}%` }}
                />
              </div>
            </div>

            <div className="p-2 border border-[#443849] bg-[#1C1824] text-[10px] text-[#8E8295]">
              <span className="text-[#F4EFF6] font-bold">INTERPRETATION NOTE:</span> Feature attributions describe
              the mathematical behavior and decision boundaries of the trained model. They do not constitute causal mechanisms.
            </div>
          </div>
        </div>
      </div>

      {/* Local Prediction Explanation Inspector (Waterfall Decomposition) */}
      <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <div className="text-xs text-[#B7ACBD] uppercase font-bold">
              LOCAL PREDICTION EXPLANATION INSPECTOR
            </div>
            <div className="text-[11px] text-[#8E8295]">
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
                        ? "bg-[#332B3C] text-[#F18B78] font-bold border border-[#F18B78]"
                        : "bg-[#2A2432] text-[#8E8295] hover:text-[#F4EFF6] border border-[#443849]"
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
            <div className="grid grid-cols-2 md:grid-cols-4 gap-2 bg-[#1C1824] p-2.5 border border-[#443849] text-[11px]">
              <div>
                <span className="text-[#8E8295]">OBSERVATION TYPE:</span>{" "}
                <span className="font-bold text-[#F4EFF6]">{activeLocalObs.observationType}</span>
              </div>
              <div>
                <span className="text-[#8E8295]">ROW INDEX:</span>{" "}
                <span className="font-mono text-[#F4EFF6]">#{activeLocalObs.rowIndex}</span>
              </div>
              <div>
                <span className="text-[#8E8295]">MODEL PREDICTION:</span>{" "}
                <span className={`font-mono font-bold ${activeLocalObs.prediction >= 0.5 ? "text-[#F18B78]" : "text-[#B7D7A8]"}`}>
                  {(activeLocalObs.prediction * 100).toFixed(2)}%
                </span>
              </div>
              <div>
                <span className="text-[#8E8295]">GROUND TRUTH:</span>{" "}
                <span className="font-bold text-[#F4EFF6]">Class {activeLocalObs.actual}</span>
              </div>
            </div>

            {/* Waterfall-style Top Contributors */}
            <div className="space-y-1.5">
              <div className="text-[10px] text-[#8E8295] uppercase mb-1">
                TOP CONTRIBUTING FEATURES (ADDITIVE SHAP VALUES)
              </div>

              {activeLocalObs.topContributors.map((c: any, cIdx: number) => {
                const isPositive = c.direction === "POSITIVE" || c.shapValue >= 0;
                const absVal = Math.abs(c.shapValue);

                return (
                  <div key={cIdx} className="flex items-center justify-between text-[11px] p-1.5 bg-[#1C1824] border border-[#443849]">
                    <div className="w-1/3 flex items-center gap-2 truncate">
                      <span className="font-medium text-[#F4EFF6] truncate">{c.feature}</span>
                      <span className="text-[10px] text-[#8E8295]">={typeof c.value === "number" ? c.value.toFixed(2) : String(c.value)}</span>
                    </div>

                    <div className="w-1/3 px-2">
                      <div className="flex items-center h-3 bg-[#2A2432] border border-[#443849]">
                        <div
                          className={isPositive ? "bg-[#F18B78] h-full ml-auto" : "bg-[#E8B66D] h-full mr-auto"}
                          style={{ width: `${Math.min(100, absVal * 40)}%` }}
                        />
                      </div>
                    </div>

                    <div className="w-1/4 text-right font-mono font-bold">
                      <span className={isPositive ? "text-[#F18B78]" : "text-[#E8B66D]"}>
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
          <div className="p-3 bg-[#1C1824] border border-[#443849] text-[11px] space-y-2">
            <div className="flex justify-between">
              <span className="text-[#8E8295]">DEMO OBSERVATION #127 (Prediction: 91.3% | Actual: 1)</span>
              <span className="text-[#B7ACBD]">Base Value: -3.61</span>
            </div>
            <div className="space-y-1 text-xs">
              <div className="flex justify-between">
                <span className="text-[#F4EFF6]">post_decision_risk_score = 0.94</span>
                <span className="text-[#F18B78] font-bold font-mono">+1.9142</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4EFF6]">transaction_id_hash = 0.88</span>
                <span className="text-[#F18B78] font-bold font-mono">+1.1152</span>
              </div>
              <div className="flex justify-between">
                <span className="text-[#F4EFF6]">transaction_amount = $1,420.00</span>
                <span className="text-[#E8B66D] font-bold font-mono">-0.1240</span>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Structured Forensic Findings */}
      {findings.length > 0 && (
        <div className="border border-[#443849] bg-[#2A2432] p-3 space-y-2">
          <div className="text-xs text-[#B7ACBD] uppercase font-bold">
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
                      ? "border-[#F18B78] bg-[#332B3C] text-[#F18B78]"
                      : isHigh
                      ? "border-[#E8B66D] bg-[#332B3C] text-[#E8B66D]"
                      : isWarn
                      ? "border-[#E8B66D] bg-[#332B3C] text-[#E8B66D]"
                      : "border-[#443849] bg-[#332B3C] text-[#B7ACBD]"
                  }`}
                >
                  <div className="flex items-center justify-between mb-1">
                    <span className="font-bold text-[#F4EFF6] flex items-center gap-2">
                      <span className={`px-1 text-[9px] font-bold ${
                        isCrit ? "bg-[#F18B78] text-[#211D29]" : isHigh ? "bg-[#E8B66D] text-[#211D29]" : "bg-[#E8B66D] text-[#211D29]"
                      }`}>
                        {f.severity}
                      </span>
                      {f.title}
                    </span>
                    <span className="text-[10px] font-mono text-[#8E8295]">{f.id}</span>
                  </div>
                  <p className="text-[11px] leading-relaxed text-[#B7ACBD]">{f.description}</p>
                  {f.recommendation && (
                    <div className="mt-1.5 text-[10px] text-[#B7ACBD] border-t border-[#443849] pt-1">
                      <span className="text-[#F18B78] font-bold">REMEDIATION:</span> {f.recommendation}
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

