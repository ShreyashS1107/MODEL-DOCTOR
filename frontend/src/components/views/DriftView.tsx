"use client";

import React, { useState } from "react";
import { DISTRIBUTION_BINS } from "@/lib/mockData";

export interface DriftViewProps {
  result?: Record<string, any> | null;
  status?: string;
  statusMessage?: string;
}

export const DriftView: React.FC<DriftViewProps> = ({ result, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">DISTRIBUTION &amp; COVARIATE DRIFT AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during covariate and concept drift analysis."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid drift metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">DRIFT ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = Boolean(result && result.summary && Array.isArray(result.features));

  // Extract real summary metrics if available, otherwise use default demo fallback
  const summary = isRealData
    ? result!.summary
    : {
        baselineRowCount: 50000,
        evaluationRowCount: 50000,
        featuresEvaluated: 47,
        numericFeaturesEvaluated: 32,
        categoricalFeaturesEvaluated: 15,
        statisticallySignificantCount: 3,
        practicalDriftCount: 1,
        criticalDriftCount: 1,
        highDriftCount: 0,
        mediumDriftCount: 2,
        lowDriftCount: 44,
        maxPsi: 0.312,
        maxPsiFeature: "user_velocity_6h",
        avgWasserstein: 0.041,
        healthScore: 78.0,
        passed: true,
      };

  const schema = isRealData && result!.schema ? result!.schema : null;
  const features: any[] = isRealData ? result!.features : [];
  const findings: any[] = isRealData && Array.isArray(result!.findings) ? result!.findings : [];

  // Selected feature for detailed distribution bin / category inspection
  const [selectedFeatureName, setSelectedFeatureName] = useState<string>(
    isRealData && features.length > 0 ? features[0].feature : "user_velocity_6h"
  );

  const selectedFeatureData = isRealData
    ? features.find((f) => f.feature === selectedFeatureName) || features[0]
    : null;

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Module Header Banner */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#94a3b8] uppercase font-bold">
              04 COVARIATE &amp; CONCEPT DISTRIBUTION DRIFT
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
            Two-sample Kolmogorov-Smirnov continuous test, Population Stability Index (PSI), Wasserstein-1 metric, and Chi-Square contingency.
          </div>
        </div>

        <div
          className={`px-2.5 py-1 text-xs font-bold shrink-0 ${
            summary.criticalDriftCount > 0 || (summary.maxPsi && summary.maxPsi >= 0.25)
              ? "bg-[#ef4444] text-black"
              : summary.highDriftCount > 0 || summary.mediumDriftCount > 0
              ? "bg-[#f59e0b] text-black"
              : "bg-[#10b981] text-black"
          }`}
        >
          {summary.criticalDriftCount > 0 || (summary.maxPsi && summary.maxPsi >= 0.25)
            ? `STATUS: CRITICAL DRIFT (MAX PSI = ${summary.maxPsi?.toFixed(3)})`
            : summary.highDriftCount > 0
            ? `STATUS: ELEVATED DRIFT (MAX PSI = ${summary.maxPsi?.toFixed(3)})`
            : `STATUS: NOMINAL STABILITY (MAX PSI = ${summary.maxPsi?.toFixed(3)})`}
        </div>
      </div>

      {/* Metrics Summary Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">MAX PSI (STABILITY)</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.maxPsi || 0) >= 0.25
                ? "text-[#ef4444]"
                : (summary.maxPsi || 0) >= 0.10
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {summary.maxPsi !== undefined && summary.maxPsi !== null ? summary.maxPsi.toFixed(3) : "0.000"}
          </div>
          <div className="text-[10px] text-[#64748b] truncate">
            {summary.maxPsiFeature || "None"} (&gt;= 0.25 is High)
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">STATISTICALLY SIGNIFICANT</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              summary.statisticallySignificantCount > 0 ? "text-[#f59e0b]" : "text-[#10b981]"
            }`}
          >
            {summary.statisticallySignificantCount ?? 0} / {summary.featuresEvaluated ?? 0}
          </div>
          <div className="text-[10px] text-[#64748b]">FDR adjusted p &lt; 0.05</div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">PRACTICAL DRIFT (HIGH)</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.practicalDriftCount || 0) > 0 ? "text-[#ef4444]" : "text-white"
            }`}
          >
            {(summary.criticalDriftCount || 0) + (summary.highDriftCount || 0)} feature(s)
          </div>
          <div className="text-[10px] text-[#64748b]">PSI &gt;= 0.25 or KS &gt;= 0.20</div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">WASSERSTEIN-1 (AVG)</div>
          <div className="text-xl font-bold text-white tabular-nums mt-0.5">
            {summary.avgWasserstein !== undefined && summary.avgWasserstein !== null
              ? summary.avgWasserstein.toFixed(4)
              : "0.0000"}
          </div>
          <div className="text-[10px] text-[#64748b]">Earth Mover continuous mean</div>
        </div>
      </div>

      {/* Schema Comparison Banner if Schema Drift Detected */}
      {schema && (schema.missingInEvaluation?.length > 0 || schema.newInEvaluation?.length > 0 || schema.dtypeDifferences?.length > 0) && (
        <div className="border border-[#f59e0b] bg-[#16120b] p-3 space-y-2">
          <div className="text-xs text-[#f59e0b] uppercase font-bold flex items-center justify-between">
            <span>SCHEMA TOPOLOGY DIFFERENCES DETECTED</span>
            <span className="text-[10px] text-[#94a3b8]">
              BASELINE: {schema.baselineRowCount?.toLocaleString()} rows | EVAL: {schema.evaluationRowCount?.toLocaleString()} rows
            </span>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-2 text-[11px]">
            {schema.missingInEvaluation?.length > 0 && (
              <div className="p-2 border border-[#ef4444]/40 bg-[#170e10]">
                <span className="text-[#ef4444] font-bold">MISSING IN EVALUATION ({schema.missingInEvaluation.length}):</span>
                <div className="text-[#94a3b8] mt-0.5">{schema.missingInEvaluation.join(", ")}</div>
              </div>
            )}
            {schema.newInEvaluation?.length > 0 && (
              <div className="p-2 border border-[#3b82f6]/40 bg-[#0e1420]">
                <span className="text-[#3b82f6] font-bold">NEW IN EVALUATION ({schema.newInEvaluation.length}):</span>
                <div className="text-[#94a3b8] mt-0.5">{schema.newInEvaluation.join(", ")}</div>
              </div>
            )}
            {schema.dtypeDifferences?.length > 0 && (
              <div className="p-2 border border-[#f59e0b]/40 bg-[#17130b]">
                <span className="text-[#f59e0b] font-bold">TYPE MISMATCHES ({schema.dtypeDifferences.length}):</span>
                <div className="text-[#94a3b8] mt-0.5">
                  {schema.dtypeDifferences.map((m: any) => `${m.column} (${m.baselineInferred}->${m.evaluationInferred})`).join(", ")}
                </div>
              </div>
            )}
          </div>
        </div>
      )}

      {/* Structured Findings Section */}
      {findings.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>DISTRIBUTION DRIFT FINDINGS &amp; EVIDENCE ({findings.length})</span>
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
                    {f.affected_features?.length > 0 && (
                      <span className="text-[10px] text-[#94a3b8] font-bold">
                        FEATURE: {f.affected_features.join(", ")}
                      </span>
                    )}
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

      {/* Selected Feature Drift Profile Inspector */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3">
        <div className="text-xs text-[#94a3b8] mb-3 uppercase font-bold flex items-center justify-between">
          <span>
            DRIFT PROFILE FOR SELECTED FEATURE:{" "}
            <span className="text-[#3b82f6]">{selectedFeatureName}</span>
          </span>
          {selectedFeatureData && (
            <span className="text-[10px] text-[#64748b]">
              PSI: {selectedFeatureData.psi?.toFixed(4)} | TYPE: {selectedFeatureData.type} | SEVERITY: {selectedFeatureData.severity}
            </span>
          )}
        </div>

        {/* Real feature distribution quantile bins or category bars */}
        {isRealData && selectedFeatureData ? (
          <div>
            {selectedFeatureData.type === "NUMERIC" && selectedFeatureData.binning?.bins?.length > 0 ? (
              <div className="space-y-2">
                {selectedFeatureData.binning.bins.map((bin: any) => (
                  <div key={bin.binIndex} className="text-xs">
                    <div className="flex justify-between text-[11px] mb-1">
                      <span className="text-[#f1f3f8] w-28 truncate">{bin.binRange}</span>
                      <span className="text-[#64748b]">
                        Baseline: {(bin.baselineDensity * 100).toFixed(1)}% | Current: {(bin.currentDensity * 100).toFixed(1)}%
                      </span>
                      <span className={bin.psiDelta > 0.03 ? "text-[#ef4444] font-bold" : "text-[#64748b]"}>
                        PSI Contribution: {bin.psiDelta.toFixed(4)}
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-1 h-3 bg-[#141822] p-0.5 border border-[#1f2533]">
                      <div
                        className="bg-[#3b82f6] h-full"
                        style={{ width: `${Math.min(100, bin.baselineDensity * 100 * 2.5)}%` }}
                      />
                      <div
                        className={bin.psiDelta > 0.03 ? "bg-[#ef4444] h-full" : "bg-[#10b981] h-full"}
                        style={{ width: `${Math.min(100, bin.currentDensity * 100 * 2.5)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : selectedFeatureData.type === "CATEGORICAL" && selectedFeatureData.categoryDistribution?.length > 0 ? (
              <div className="space-y-2">
                {selectedFeatureData.categoryDistribution.map((cat: any) => (
                  <div key={cat.category} className="text-xs">
                    <div className="flex justify-between text-[11px] mb-1">
                      <span className="text-[#f1f3f8] w-28 truncate font-bold">{cat.category}</span>
                      <span className="text-[#64748b]">
                        Baseline: {(cat.baselineProportion * 100).toFixed(1)}% ({cat.baselineCount}) | Current: {(cat.evaluationProportion * 100).toFixed(1)}% ({cat.evaluationCount})
                      </span>
                      <span className={Math.abs(cat.proportionDelta) > 0.05 ? "text-[#f59e0b] font-bold" : "text-[#64748b]"}>
                        Delta: {(cat.proportionDelta * 100).toFixed(1)}% (PSI d: {cat.psiDelta?.toFixed(4)})
                      </span>
                    </div>
                    <div className="grid grid-cols-2 gap-1 h-3 bg-[#141822] p-0.5 border border-[#1f2533]">
                      <div
                        className="bg-[#3b82f6] h-full"
                        style={{ width: `${Math.min(100, cat.baselineProportion * 100 * 2)}%` }}
                      />
                      <div
                        className={Math.abs(cat.proportionDelta) > 0.05 ? "bg-[#f59e0b] h-full" : "bg-[#10b981] h-full"}
                        style={{ width: `${Math.min(100, cat.evaluationProportion * 100 * 2)}%` }}
                      />
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-[11px] text-[#64748b] py-2">
                No granular binning data available for this feature.
              </div>
            )}
          </div>
        ) : (
          /* Demo Fallback Chart */
          <div className="space-y-2">
            {DISTRIBUTION_BINS.map((bin) => (
              <div key={bin.binIndex} className="text-xs">
                <div className="flex justify-between text-[11px] mb-1">
                  <span className="text-[#f1f3f8] w-20">{bin.binRange}</span>
                  <span className="text-[#64748b]">
                    Baseline: {(bin.baselineDensity * 100).toFixed(1)}% | Current: {(bin.currentDensity * 100).toFixed(1)}%
                  </span>
                  <span className={bin.psiDelta > 0.03 ? "text-[#ef4444] font-bold" : "text-[#64748b]"}>
                    PSI Contribution: {bin.psiDelta.toFixed(3)}
                  </span>
                </div>
                <div className="grid grid-cols-2 gap-1 h-3 bg-[#141822] p-0.5 border border-[#1f2533]">
                  <div className="bg-[#3b82f6] h-full" style={{ width: `${bin.baselineDensity * 100 * 2}%` }} />
                  <div
                    className={bin.psiDelta > 0.03 ? "bg-[#ef4444] h-full" : "bg-[#10b981] h-full"}
                    style={{ width: `${bin.currentDensity * 100 * 2}%` }}
                  />
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Feature Drift Ranking & Statistical Evidence Table */}
      {isRealData && features.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3">
          <div className="text-xs text-[#94a3b8] mb-3 uppercase font-bold flex items-center justify-between">
            <span>FEATURE-BY-FEATURE DRIFT MATRIX ({features.length} FEATURES)</span>
            <span className="text-[10px] text-[#64748b]">CLICK ROW TO INSPECT BINS</span>
          </div>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                  <th className="py-1.5 px-2">Feature Name</th>
                  <th className="py-1.5 px-2">Type</th>
                  <th className="py-1.5 px-2">KS / Chi2 Stat</th>
                  <th className="py-1.5 px-2">Raw P-Value</th>
                  <th className="py-1.5 px-2">FDR Adj P-Val</th>
                  <th className="py-1.5 px-2">Wasserstein (Norm)</th>
                  <th className="py-1.5 px-2">PSI</th>
                  <th className="py-1.5 px-2">Stat Sig</th>
                  <th className="py-1.5 px-2">Practical</th>
                  <th className="py-1.5 px-2">Severity</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1f2533] tabular-nums">
                {features.map((f: any) => {
                  const isSelected = f.feature === selectedFeatureName;
                  const isCrit = f.severity === "CRITICAL";
                  const isHigh = f.severity === "HIGH";
                  const isWarn = f.severity === "WARNING" || f.severity === "MEDIUM";

                  const statVal = f.type === "NUMERIC" ? f.ks?.statistic : f.chi_square?.statistic;
                  const rawP = f.type === "NUMERIC" ? f.ks?.p_value : f.chi_square?.p_value;
                  const adjP = f.type === "NUMERIC" ? f.ks?.adjusted_p_value : f.chi_square?.adjusted_p_value;
                  const normW = f.wasserstein?.normalized_distance;

                  return (
                    <tr
                      key={f.feature}
                      onClick={() => setSelectedFeatureName(f.feature)}
                      className={`cursor-pointer transition-colors ${
                        isSelected ? "bg-[#141c2e] border-l-2 border-[#3b82f6]" : "hover:bg-[#12161f]"
                      }`}
                    >
                      <td className="py-2 px-2 text-[#f1f3f8] font-bold">{f.feature}</td>
                      <td className="py-2 px-2 text-[#94a3b8]">
                        <span className="px-1 py-0.2 bg-[#141822] border border-[#222633] text-[10px]">
                          {f.type}
                        </span>
                      </td>
                      <td className="py-2 px-2 text-[#f1f3f8]">
                        {statVal !== undefined && statVal !== null ? statVal.toFixed(3) : "-"}
                      </td>
                      <td className="py-2 px-2 text-[#64748b]">
                        {rawP !== undefined && rawP !== null
                          ? rawP < 0.0001
                            ? rawP.toExponential(2)
                            : rawP.toFixed(4)
                          : "-"}
                      </td>
                      <td
                        className={`py-2 px-2 ${
                          adjP !== undefined && adjP < 0.05 ? "text-[#f59e0b] font-bold" : "text-[#64748b]"
                        }`}
                      >
                        {adjP !== undefined && adjP !== null
                          ? adjP < 0.0001
                            ? adjP.toExponential(2)
                            : adjP.toFixed(4)
                          : "-"}
                      </td>
                      <td className="py-2 px-2 text-[#94a3b8]">
                        {f.type === "NUMERIC"
                          ? normW !== undefined && normW !== null
                            ? `${normW.toFixed(3)} (${f.wasserstein?.normalization})`
                            : `${f.wasserstein?.distance?.toFixed(2) || "0.00"}`
                          : "N/A"}
                      </td>
                      <td
                        className={`py-2 px-2 font-bold ${
                          (f.psi || 0) >= 0.25
                            ? "text-[#ef4444]"
                            : (f.psi || 0) >= 0.10
                            ? "text-[#f59e0b]"
                            : "text-[#10b981]"
                        }`}
                      >
                        {f.psi !== undefined && f.psi !== null ? f.psi.toFixed(4) : "0.0000"}
                      </td>
                      <td className="py-2 px-2">
                        <span
                          className={`text-[10px] font-bold ${
                            f.statistically_significant ? "text-[#f59e0b]" : "text-[#64748b]"
                          }`}
                        >
                          {f.statistically_significant ? "TRUE" : "FALSE"}
                        </span>
                      </td>
                      <td className="py-2 px-2">
                        <span
                          className={`text-[10px] font-bold ${
                            f.practical_drift === "HIGH"
                              ? "text-[#ef4444]"
                              : f.practical_drift === "MEDIUM"
                              ? "text-[#f59e0b]"
                              : "text-[#10b981]"
                          }`}
                        >
                          {f.practical_drift || "LOW"}
                        </span>
                      </td>
                      <td className="py-2 px-2">
                        <span
                          className={`px-1.5 py-0.5 text-[9px] font-bold ${
                            isCrit
                              ? "bg-[#ef4444] text-black"
                              : isHigh
                              ? "bg-[#ef4444] text-black"
                              : isWarn
                              ? "bg-[#f59e0b] text-black"
                              : "bg-[#10b981] text-black"
                          }`}
                        >
                          {f.severity || "LOW"}
                        </span>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </div>
      )}
    </div>
  );
};
