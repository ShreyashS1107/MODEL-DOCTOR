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
      <div className="space-y-4 font-mono text-xs text-[#F4EFF6]">
        <div className="p-4 border border-[#F18B78] bg-[#F18B78]/10 text-[#F18B78] rounded space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#F18B78] text-[#211D29] rounded">FAILED</span>
            <span className="text-sm font-bold text-[#F4EFF6] uppercase">DATA LEAKAGE &amp; CONTAMINATION AUDIT FAILED</span>
          </div>
          <p className="text-xs text-[#F18B78]">
            {statusMessage || "Diagnostic engine reported an execution failure during target contamination and leakage analysis."}
          </p>
          <div className="text-[10px] text-[#8E8295] pt-1 border-t border-[#F18B78]/30">
            Engine execution terminated without producing valid leakage metrics. Synthetic demo data is suppressed for failed live runs.
          </div>
        </div>
      </div>
    );
  }

  if (status === "NOT_IMPLEMENTED") {
    return (
      <div className="space-y-4 font-mono text-xs text-[#F4EFF6]">
        <div className="p-4 border border-[#E8B66D] bg-[#E8B66D]/10 text-[#E8B66D] rounded space-y-2">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#E8B66D] text-[#211D29] rounded">DEFERRED</span>
            <span className="text-sm font-bold text-[#F4EFF6] uppercase">LEAKAGE ENGINE DEFERRED</span>
          </div>
          <p className="text-xs text-[#E8B66D]">
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
    <div className="space-y-4 font-mono text-xs text-[#F4EFF6]">
      {/* Module Header Banner */}
      <div className="p-3 border border-[#443849] bg-[#2A2432] rounded flex flex-col sm:flex-row sm:items-center justify-between gap-2">
        <div>
          <div className="flex items-center gap-2">
            <span className="text-xs text-[#B7ACBD] uppercase font-bold">
              03 DATA LEAKAGE &amp; TARGET CONTAMINATION AUDIT
            </span>
            {isRealData ? (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#B7D7A8]/20 text-[#B7D7A8] border border-[#B7D7A8]/40 rounded">
                REAL STATISTICAL EVIDENCE
              </span>
            ) : (
              <span className="px-1.5 py-0.2 text-[9px] font-bold bg-[#E8B66D]/20 text-[#E8B66D] border border-[#E8B66D]/40 rounded">
                DEMO / SYNTHETIC FIXTURE FALLBACK
              </span>
            )}
          </div>
          <div className="text-[11px] text-[#8E8295] mt-0.5">
            Target: <strong className="text-[#F4EFF6]">{targetInfo.column}</strong> [{targetInfo.taskType}] &bull; Direct mutual info, correlation/Cramer&apos;s V, proxy surrogates, and train/eval row hash overlap.
          </div>
        </div>

        <div
          className={`px-2.5 py-1 text-xs font-bold shrink-0 rounded ${
            summary.criticalLeakageCount > 0
              ? "bg-[#F18B78] text-[#211D29]"
              : summary.highLeakageCount > 0
              ? "bg-[#E8B66D] text-[#211D29]"
              : "bg-[#B7D7A8] text-[#211D29]"
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
        <div className="p-3 border border-[#F18B78] bg-[#F18B78]/10 rounded space-y-2">
          <div className="flex justify-between items-start">
            <div>
              <div className="text-xs font-bold text-[#F18B78] uppercase flex items-center gap-2">
                <span>[CRITICAL] POTENTIAL TARGET LEAKAGE DETECTED:</span>
                <span className="text-[#F4EFF6] underline">{topLeaked.name}</span>
              </div>
              <div className="text-[11px] text-[#F4EFF6] mt-1 tabular-nums">
                Mutual Information = <strong>{topLeaked.mutualInformation?.toFixed(3)}</strong> (Threshold: &ge; 0.700) &bull;{" "}
                {topLeaked.associationMeasure || "Target Association"} ={" "}
                <strong>{topLeaked.targetAssociation?.toFixed(3)}</strong>
              </div>
            </div>
            <span className="px-2 py-0.5 text-[9px] font-bold bg-[#F18B78] text-[#211D29] rounded shrink-0">
              DROP RECOMMENDED
            </span>
          </div>

          {topFinding && (
            <div className="text-[11px] text-[#B7ACBD] leading-relaxed pt-1.5 border-t border-[#F18B78]/30">
              <strong>Forensic Evidence:</strong> {topFinding.description}
            </div>
          )}

          <div className="text-[11px] text-[#B7D7A8] bg-[#332B3C] p-2 border border-[#B7D7A8]/30 rounded">
            <strong>Prescribed Remediation:</strong>{" "}
            {topFinding?.recommendation ||
              `Verify whether '${topLeaked.name}' is available strictly before prediction timestamp. Drop from feature pipeline if it encodes ground truth outcomes.`}
          </div>
        </div>
      )}

      {/* Numerical Evidence Summary Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
        <div className="p-2.5 border border-[#443849] bg-[#2A2432] rounded">
          <div className="text-[10px] text-[#8E8295] uppercase">PEAK MUTUAL INFORMATION</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.maxMutualInformation || 0) >= 0.70
                ? "text-[#F18B78]"
                : (summary.maxMutualInformation || 0) >= 0.50
                ? "text-[#E8B66D]"
                : "text-[#B7D7A8]"
            }`}
          >
            {(summary.maxMutualInformation || 0).toFixed(3)}
          </div>
          <div className="text-[10px] text-[#8E8295] truncate">
            {summary.mostSuspiciousFeature || "None"}
          </div>
        </div>

        <div className="p-2.5 border border-[#443849] bg-[#2A2432] rounded">
          <div className="text-[10px] text-[#8E8295] uppercase">PEAK TARGET ASSOCIATION</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              Math.abs(summary.maxTargetAssociation || 0) >= 0.85
                ? "text-[#F18B78]"
                : Math.abs(summary.maxTargetAssociation || 0) >= 0.65
                ? "text-[#E8B66D]"
                : "text-[#B7D7A8]"
            }`}
          >
            {(summary.maxTargetAssociation || 0).toFixed(3)}
          </div>
          <div className="text-[10px] text-[#8E8295]">
            Pearson r / Cramer&apos;s V / &eta;
          </div>
        </div>

        <div className="p-2.5 border border-[#443849] bg-[#2A2432] rounded">
          <div className="text-[10px] text-[#8E8295] uppercase">TRAIN / EVAL ROW OVERLAP</div>
          <div
            className={`text-xl font-bold tabular-nums mt-0.5 ${
              (summary.overlapRowRate || 0) > 0.01 ? "text-[#F18B78]" : "text-[#F4EFF6]"
            }`}
          >
            {summary.overlapRowCount || 0} rows
          </div>
          <div className="text-[10px] text-[#8E8295]">
            {((summary.overlapRowRate || 0) * 100).toFixed(4)}% hash contamination
          </div>
        </div>
      </div>

      {/* Feature-by-Feature Forensic Ranking Table */}
      <div className="border border-[#443849] bg-[#2A2432] rounded p-3">
        <div className="text-xs text-[#B7ACBD] mb-3 uppercase font-bold flex items-center justify-between">
          <span>LEAKAGE CANDIDATE RANKING ({features.length} FEATURES AUDITED)</span>
          <span className="text-[10px] text-[#8E8295]">SORTED BY SUSPICION INDEX</span>
        </div>

        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead>
              <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] uppercase">
                <th className="py-1.5 px-2">Rank</th>
                <th className="py-1.5 px-2">Feature Name</th>
                <th className="py-1.5 px-2">Mutual Info (nats)</th>
                <th className="py-1.5 px-2">Target Association</th>
                <th className="py-1.5 px-2">Measure</th>
                <th className="py-1.5 px-2">Target Proxy</th>
                <th className="py-1.5 px-2">Leakage Risk</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-[#443849] tabular-nums">
              {features.map((f: any, idx: number) => {
                const mi = f.mutualInformation || 0;
                const assoc = f.targetAssociation || 0;
                const risk = f.risk || "LOW";
                const isCrit = risk === "CRITICAL";
                const isHigh = risk === "HIGH";
                const isMed = risk === "MEDIUM";

                return (
                  <tr key={f.name} className="hover:bg-[#332B3C]/50 transition-colors">
                    <td className="py-2 px-2 text-[#8E8295]">#{idx + 1}</td>
                    <td className="py-2 px-2 text-[#F4EFF6] font-bold">{f.name}</td>
                    <td
                      className={`py-2 px-2 font-bold ${
                        mi >= 0.70
                          ? "text-[#F18B78]"
                          : mi >= 0.50
                          ? "text-[#E8B66D]"
                          : mi >= 0.30
                          ? "text-[#B7ACBD]"
                          : "text-[#8E8295]"
                      }`}
                    >
                      {mi.toFixed(3)}
                    </td>
                    <td
                      className={`py-2 px-2 font-bold ${
                        Math.abs(assoc) >= 0.85
                          ? "text-[#F18B78]"
                          : Math.abs(assoc) >= 0.65
                          ? "text-[#E8B66D]"
                          : "text-[#8E8295]"
                      }`}
                    >
                      {assoc.toFixed(3)}
                    </td>
                    <td className="py-2 px-2 text-[#B7ACBD]">
                      <span className="text-[10px] text-[#8E8295]">{f.associationMeasure || "PEARSON"}</span>
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`text-[10px] font-bold ${
                          f.isPotentialProxy ? "text-[#F18B78]" : "text-[#8E8295]"
                        }`}
                      >
                        {f.isPotentialProxy ? "POTENTIAL PROXY" : "NO"}
                      </span>
                    </td>
                    <td className="py-2 px-2">
                      <span
                        className={`px-1.5 py-0.5 text-[9px] font-bold rounded ${
                          isCrit
                            ? "bg-[#F18B78] text-[#211D29]"
                            : isHigh
                            ? "bg-[#E8B66D] text-[#211D29]"
                            : isMed
                            ? "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]"
                            : "bg-[#B7D7A8] text-[#211D29]"
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
