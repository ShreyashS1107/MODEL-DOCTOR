"use client";

import React from "react";
import { FEATURE_NODES } from "@/lib/mockData";

export interface DataQualityViewProps {
  result?: Record<string, any> | null;
  status?: string;
  statusMessage?: string;
}

export const DataQualityView: React.FC<DataQualityViewProps> = ({ result, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">DATA QUALITY &amp; INPUT INTEGRITY AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during data quality analysis."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid dataset metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">DATA QUALITY ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = Boolean(result && result.summary && Array.isArray(result.columns));

  // Extract real metrics if available, otherwise fall back to mock data
  const summary = isRealData
    ? result!.summary
    : {
        rowCount: 250000,
        columnCount: 47,
        duplicateRowCount: 0,
        duplicateRowRate: 0.0,
        memoryUsageBytes: 94000000,
        totalMissingCells: 300,
        globalMissingRate: 0.0012,
        columnsWithMissingValues: 2,
        maxMissingRate: 0.042,
        constantColumnsCount: 0,
        healthScore: 91.0,
        passed: true,
      };

  const columns = isRealData
    ? result!.columns
    : FEATURE_NODES.map((f) => ({
        name: f.name,
        inferredType: f.dataType.toUpperCase(),
        missingRate: f.missingRate,
        outlierRate: f.outlierFraction,
        isConstant: false,
        uniqueCount: 1500,
        sampleCount: 250000,
      }));

  const findings: any[] = isRealData && Array.isArray(result!.findings) ? result!.findings : [];

  const maxMissingCol = isRealData
    ? columns.reduce((max: any, c: any) => (c.missingRate > (max?.missingRate || 0) ? c : max), null)
    : { name: "device_trust_score", missingRate: 0.042 };

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Module Header Banner */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#94a3b8] uppercase font-bold">
              02 DATA QUALITY &amp; INPUT INTEGRITY AUDIT
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
            Missingness topology, zero-variance columns, type fidelity, non-finite values, and IQR outlier bounds.
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
            ? `STATUS: AUDIT PASS (HEALTH: ${summary.healthScore?.toFixed(0)}/100)`
            : `STATUS: AUDIT FAILED (HEALTH: ${summary.healthScore?.toFixed(0)}/100)`}
        </div>
      </div>

      {/* Numerical Metrics Summary Grid */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-2">
        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">COLUMNS AUDITED</div>
          <div className="text-xl font-bold text-white tabular-nums mt-0.5">
            {summary.columnCount}
          </div>
          <div className="text-[10px] text-[#64748b]">
            {summary.constantColumnsCount || 0} constant column(s)
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">GLOBAL MISSING RATE</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              summary.globalMissingRate > 0.02 ? "text-[#f59e0b]" : "text-[#10b981]"
            }`}
          >
            {(summary.globalMissingRate * 100).toFixed(2)}%
          </div>
          <div className="text-[10px] text-[#64748b]">
            {summary.totalMissingCells ?? 0} missing cells ({summary.columnsWithMissingValues ?? 0} cols)
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">MAX COLUMN NULL RATE</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.maxMissingRate || 0) > 0.02 ? "text-[#f59e0b]" : "text-white"
            }`}
          >
            {((summary.maxMissingRate || 0) * 100).toFixed(2)}%
          </div>
          <div className="text-[10px] text-[#64748b] truncate">
            {maxMissingCol?.name || "None"}
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">DUPLICATE ROW RATE</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              summary.duplicateRowRate > 0.01 ? "text-[#ef4444]" : "text-white"
            }`}
          >
            {(summary.duplicateRowRate * 100).toFixed(2)}%
          </div>
          <div className="text-[10px] text-[#64748b]">
            {summary.duplicateRowCount || 0} / {summary.rowCount?.toLocaleString()} rows
          </div>
        </div>
      </div>

      {/* Structured Findings Section */}
      {findings.length > 0 && (
        <div className="border border-[#1f2533] bg-[#0c0e14] p-3 space-y-2">
          <div className="text-xs text-[#94a3b8] uppercase font-bold flex items-center justify-between">
            <span>DATA QUALITY FINDINGS &amp; ANOMALIES ({findings.length})</span>
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
                        COL: {f.affected_features.join(", ")}
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

      {/* Feature Quality Profile Table */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3">
        <div className="text-xs text-[#94a3b8] mb-3 uppercase font-bold flex items-center justify-between">
          <span>COLUMN-BY-COLUMN INTEGRITY PROFILE ({columns.length} COLUMNS)</span>
          <span className="text-[10px] text-[#64748b]">EVALUATION DATASET PARTITION</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                <th className="py-1.5 px-2">Column Name</th>
                <th className="py-1.5 px-2">Inferred Type</th>
                <th className="py-1.5 px-2">Distinct</th>
                <th className="py-1.5 px-2">Missing %</th>
                <th className="py-1.5 px-2">Outlier % (IQR)</th>
                <th className="py-1.5 px-2">Constant</th>
                <th className="py-1.5 px-2">Non-Finite</th>
                <th className="py-1.5 px-2">Status</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1f2533] tabular-nums">
              {columns.map((c: any) => {
                const missRate = c.missingRate || 0;
                const outRate = c.outlierRate || 0;
                const isConst = Boolean(c.isConstant);
                const nonFinite = c.nonFiniteCount || 0;

                const hasCritical = missRate >= 0.20 || nonFinite > 0;
                const hasWarning = missRate >= 0.02 || isConst || outRate >= 0.05 || Boolean(c.isNearConstant);

                return (
                  <tr key={c.name} className="hover:bg-[#12161f]">
                    <td className="py-2 px-2 text-[#f1f3f8] font-bold">{c.name}</td>
                    <td className="py-2 px-2 text-[#94a3b8]">
                      <span className="px-1 py-0.2 bg-[#141822] border border-[#222633] text-[10px]">
                        {c.inferredType || c.dtype || "UNKNOWN"}
                      </span>
                    </td>
                    <td className="py-2 px-2 text-[#94a3b8]">{c.uniqueCount?.toLocaleString() ?? "-"}</td>
                    <td
                      className={`py-2 px-2 ${
                        missRate >= 0.20
                          ? "text-[#ef4444] font-bold"
                          : missRate >= 0.02
                          ? "text-[#f59e0b] font-bold"
                          : "text-[#64748b]"
                      }`}
                    >
                      {(missRate * 100).toFixed(2)}%
                    </td>
                    <td className="py-2 px-2 text-[#64748b]">
                      {outRate > 0 ? `${(outRate * 100).toFixed(2)}%` : "0.00%"}
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`text-[10px] font-bold ${
                          isConst ? "text-[#f59e0b]" : "text-[#10b981]"
                        }`}
                      >
                        {isConst ? "TRUE" : c.isNearConstant ? "NEAR" : "FALSE"}
                      </span>
                    </td>
                    <td className="py-2 px-2">
                      <span className={`text-[10px] ${nonFinite > 0 ? "text-[#ef4444] font-bold" : "text-[#64748b]"}`}>
                        {nonFinite > 0 ? `${nonFinite} INF` : "0"}
                      </span>
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`px-1.5 py-0.5 text-[9px] font-bold ${
                          hasCritical
                            ? "bg-[#ef4444] text-black"
                            : hasWarning
                            ? "bg-[#f59e0b] text-black"
                            : "bg-[#10b981] text-black"
                        }`}
                      >
                        {hasCritical ? "CRITICAL" : hasWarning ? "WARNING" : "NOMINAL"}
                      </span>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};
