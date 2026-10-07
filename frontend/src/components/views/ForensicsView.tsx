"use client";

import React from "react";
import { FEATURE_NODES } from "@/lib/mockData";

export interface ForensicsViewProps {
  result?: Record<string, any> | null;
  status?: string;
  statusMessage?: string;
}

export const ForensicsView: React.FC<ForensicsViewProps> = ({ result, status, statusMessage }) => {
  if (status === "FAILED") {
    return (
      <div className="space-y-4 font-mono text-xs">
        <div className="p-4 border border-[#ef4444] bg-[#1a0f0f] text-[#f87171] space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAILED</span>
            <span className="text-sm font-bold text-white uppercase">DATA LEAKAGE &amp; CONTAMINATION AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#fca5a5]">
            {statusMessage || "Diagnostic engine reported an execution failure during target contamination and leakage analysis."}
          </p>
          <div className="text-[10px] text-[#94a3b8] pt-1 border-t border-[#331518]">
            Engine execution terminated without producing valid leakage metrics. Synthetic demo data is suppressed for failed live runs.
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
            <span className="text-sm font-bold text-white uppercase">LEAKAGE ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#fef08a]">
            {statusMessage || "Module is registered for subsequent analytical phases."}
          </p>
        </div>
      </div>
    );
  }

  const isRealData = Boolean(result && result.target && Array.isArray(result.features));

  const targetInfo = isRealData
    ? result!.target
    : {
        column: "is_fraud",
        taskType: "BINARY_CLASSIFICATION",
        sampleCount: 250000,
        uniqueCount: 2,
      };

  const summary = isRealData
    ? result!.summary
    : {
        evaluatedFeaturesCount: 10,
        criticalLeakageCount: 1,
        highLeakageCount: 1,
        mediumLeakageCount: 2,
        lowLeakageCount: 6,
        maxMutualInformation: 0.941,
        maxTargetAssociation: 0.962,
        mostSuspiciousFeature: "transaction_id_hash",
        overlapRowCount: 12,
        overlapRowRate: 0.000048,
        healthScore: 42.0,
        passed: false,
      };

  const features = isRealData
    ? result!.features
    : FEATURE_NODES.map((f) => ({
        name: f.name,
        mutualInformation: f.mutualInfoTarget,
        targetAssociation: f.correlationTarget,
        associationMeasure: "PEARSON_CORRELATION",
        sampleCount: 250000,
        risk: f.severity === "CRITICAL" ? "CRITICAL" : f.severity === "WARNING" ? "HIGH" : "LOW",
        isPotentialProxy: f.mutualInfoTarget >= 0.75,
        isPotentialLeakage: f.mutualInfoTarget >= 0.50,
      }));

  const findings: any[] = isRealData && Array.isArray(result!.findings) ? result!.findings : [];

  // Top leaked feature (either from features[0] or finding)
  const topLeaked = features.find((f: any) => f.risk === "CRITICAL") || features[0];
  const topFinding = findings.find((f: any) => f.severity === "CRITICAL") || findings[0];

  return (
    <div className="space-y-4 font-mono text-xs">
      {/* Module Header Banner */}
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#94a3b8] uppercase font-bold">
              03 DATA LEAKAGE &amp; TARGET CONTAMINATION AUDIT
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
            Target: <strong className="text-white">{targetInfo.column}</strong> [{targetInfo.taskType}] &bull; Direct mutual info, correlation/Cramer&apos;s V, proxy surrogates, and train/eval row hash overlap.
          </div>
        </div>

        <div
          className={`px-2.5 py-1 text-xs font-bold shrink-0 ${
            summary.criticalLeakageCount > 0
              ? "bg-[#ef4444] text-black"
              : summary.highLeakageCount > 0
              ? "bg-[#f59e0b] text-black"
              : "bg-[#10b981] text-black"
          }`}
        >
          {summary.criticalLeakageCount > 0
            ? `STATUS: CRITICAL LEAKAGE DETECTED (${summary.criticalLeakageCount} FEATURES)`
            : summary.highLeakageCount > 0
            ? `STATUS: ELEVATED RISK (${summary.highLeakageCount} FEATURES)`
            : "STATUS: NOMINAL (NO CRITICAL LEAKAGE)"}
        </div>
      </div>

      {/* Critical Violation Alert Box */}
      {topLeaked && topLeaked.risk === "CRITICAL" && (
        <div className="p-3 border border-[#ef4444] bg-[#170e10] space-y-2">
          <div className="flex justify-between items-start">
            <div>
              <div className="text-xs font-bold text-[#ef4444] uppercase flex items-center gap-2">
                <span>[CRITICAL] POTENTIAL TARGET LEAKAGE DETECTED:</span>
                <span className="text-white underline">{topLeaked.name}</span>
              </div>
              <div className="text-[11px] text-[#f1f3f8] mt-1 tabular-nums">
                Mutual Information = <strong>{topLeaked.mutualInformation?.toFixed(3)}</strong> (Threshold: &ge; 0.700) &bull;{" "}
                {topLeaked.associationMeasure || "Target Association"} ={" "}
                <strong>{topLeaked.targetAssociation?.toFixed(3)}</strong>
              </div>
            </div>
            <span className="px-2 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black shrink-0">
              DROP RECOMMENDED
            </span>
          </div>

          {topFinding && (
            <div className="text-[11px] text-[#94a3b8] leading-relaxed pt-1.5 border-t border-[#331518]">
              <strong>Forensic Evidence:</strong> {topFinding.description}
            </div>
          )}

          <div className="text-[11px] text-[#10b981] bg-[#0b1411] p-2 border border-[#12382c]">
            <strong>Prescribed Remediation:</strong>{" "}
            {topFinding?.recommendation ||
              `Verify whether '${topLeaked.name}' is available strictly before prediction timestamp. Drop from feature pipeline if it encodes ground truth outcomes.`}
          </div>
        </div>
      )}

      {/* Numerical Evidence Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">PEAK MUTUAL INFORMATION</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.maxMutualInformation || 0) >= 0.70
                ? "text-[#ef4444]"
                : (summary.maxMutualInformation || 0) >= 0.50
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {(summary.maxMutualInformation || 0).toFixed(3)}
          </div>
          <div className="text-[10px] text-[#64748b] truncate">
            {summary.mostSuspiciousFeature || "None"}
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">PEAK TARGET ASSOCIATION</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              Math.abs(summary.maxTargetAssociation || 0) >= 0.85
                ? "text-[#ef4444]"
                : Math.abs(summary.maxTargetAssociation || 0) >= 0.65
                ? "text-[#f59e0b]"
                : "text-[#10b981]"
            }`}
          >
            {(summary.maxTargetAssociation || 0).toFixed(3)}
          </div>
          <div className="text-[10px] text-[#64748b]">
            Pearson r / Cramer&apos;s V / &eta;
          </div>
        </div>

        <div className="p-2.5 border border-[#1f2533] bg-[#0c0e14]">
          <div className="text-[10px] text-[#64748b] uppercase">TRAIN / EVAL ROW OVERLAP</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.overlapRowRate || 0) > 0.01 ? "text-[#ef4444]" : "text-white"
            }`}
          >
            {summary.overlapRowCount || 0} rows
          </div>
          <div className="text-[10px] text-[#64748b]">
            {((summary.overlapRowRate || 0) * 100).toFixed(4)}% hash contamination
          </div>
        </div>
      </div>

      {/* Feature-by-Feature Forensic Ranking Table */}
      <div className="border border-[#1f2533] bg-[#0c0e14] p-3">
        <div className="text-xs text-[#94a3b8] mb-3 uppercase font-bold flex items-center justify-between">
          <span>LEAKAGE CANDIDATE RANKING ({features.length} FEATURES AUDITED)</span>
          <span className="text-[10px] text-[#64748b]">SORTED BY SUSPICION INDEX</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                <th className="py-1.5 px-2">Rank</th>
                <th className="py-1.5 px-2">Feature Name</th>
                <th className="py-1.5 px-2">Mutual Info (nats)</th>
                <th className="py-1.5 px-2">Target Association</th>
                <th className="py-1.5 px-2">Measure</th>
                <th className="py-1.5 px-2">Target Proxy</th>
                <th className="py-1.5 px-2">Leakage Risk</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#1f2533] tabular-nums">
              {features.map((f: any, idx: number) => {
                const mi = f.mutualInformation || 0;
                const assoc = f.targetAssociation || 0;
                const risk = f.risk || "LOW";
                const isCrit = risk === "CRITICAL";
                const isHigh = risk === "HIGH";
                const isMed = risk === "MEDIUM";

                return (
                  <tr key={f.name} className="hover:bg-[#12161f]">
                    <td className="py-2 px-2 text-[#64748b]">#{idx + 1}</td>
                    <td className="py-2 px-2 text-[#f1f3f8] font-bold">{f.name}</td>
                    <td
                      className={`py-2 px-2 font-bold ${
                        mi >= 0.70
                          ? "text-[#ef4444]"
                          : mi >= 0.50
                          ? "text-[#f59e0b]"
                          : mi >= 0.30
                          ? "text-[#3b82f6]"
                          : "text-[#64748b]"
                      }`}
                    >
                      {mi.toFixed(3)}
                    </td>
                    <td
                      className={`py-2 px-2 font-bold ${
                        Math.abs(assoc) >= 0.85
                          ? "text-[#ef4444]"
                          : Math.abs(assoc) >= 0.65
                          ? "text-[#f59e0b]"
                          : "text-[#64748b]"
                      }`}
                    >
                      {assoc.toFixed(3)}
                    </td>
                    <td className="py-2 px-2 text-[#94a3b8]">
                      <span className="text-[10px] text-[#64748b]">{f.associationMeasure || "PEARSON"}</span>
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`text-[10px] font-bold ${
                          f.isPotentialProxy ? "text-[#ef4444]" : "text-[#64748b]"
                        }`}
                      >
                        {f.isPotentialProxy ? "POTENTIAL PROXY" : "NO"}
                      </span>
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`px-1.5 py-0.5 text-[9px] font-bold ${
                          isCrit
                            ? "bg-[#ef4444] text-black"
                            : isHigh
                            ? "bg-[#f59e0b] text-black"
                            : isMed
                            ? "bg-[#3b82f6] text-white"
                            : "bg-[#10b981] text-black"
                        }`}
                      >
                        {risk}
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
