"use client";

import React from "react";
import { DIAGNOSTIC_RUNS } from "@/lib/mockData";

export const ExperimentsView: React.FC = () => {
  return (
    <div className="space-y-4 font-mono text-xs">
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex items-center justify-between">
        <div>
          <div className="text-xs text-[#94a3b8] uppercase font-bold">
            08 EXPERIMENTS &amp; REGRESSION TRACKING
          </div>
          <div className="text-[11px] text-[#64748b]">
            Multi-model diagnostic fingerprint comparator and regression tracking across production versions.
          </div>
        </div>
        <div className="text-xs text-[#64748b]">
          4 CANDIDATE ITERATIONS
        </div>
      </div>

      <div className="border border-[#1f2533] bg-[#0c0e14] p-3">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
              <th className="py-1.5 px-2">Model Version</th>
              <th className="py-1.5 px-2">Health Score</th>
              <th className="py-1.5 px-2">Leakage MI</th>
              <th className="py-1.5 px-2">Max PSI Drift</th>
              <th className="py-1.5 px-2">Calibration ECE</th>
              <th className="py-1.5 px-2">Disparate Impact</th>
              <th className="py-1.5 px-2">Audit State</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#1f2533] tabular-nums">
            <tr className="bg-[#1c1114]">
              <td className="py-2 px-2 text-white font-bold">fraud_classifier_v17 (Active)</td>
              <td className="py-2 px-2 text-[#ef4444] font-bold">73 / 100</td>
              <td className="py-2 px-2 text-[#ef4444] font-bold">0.941</td>
              <td className="py-2 px-2 text-[#ef4444] font-bold">0.312</td>
              <td className="py-2 px-2 text-[#f59e0b]">0.084</td>
              <td className="py-2 px-2 text-[#f59e0b]">0.771</td>
              <td className="py-2 px-2"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#ef4444] text-black">FAIL</span></td>
            </tr>
            <tr className="hover:bg-[#12161f]">
              <td className="py-2 px-2 text-[#94a3b8]">fraud_classifier_v16 (Baseline)</td>
              <td className="py-2 px-2 text-[#10b981] font-bold">89 / 100</td>
              <td className="py-2 px-2 text-[#10b981]">0.120</td>
              <td className="py-2 px-2 text-[#10b981]">0.045</td>
              <td className="py-2 px-2 text-[#10b981]">0.042</td>
              <td className="py-2 px-2 text-[#10b981]">0.840</td>
              <td className="py-2 px-2"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#f59e0b] text-black">WARN</span></td>
            </tr>
            <tr className="hover:bg-[#12161f]">
              <td className="py-2 px-2 text-[#94a3b8]">credit_risk_lgbm_v04</td>
              <td className="py-2 px-2 text-[#f59e0b] font-bold">82 / 100</td>
              <td className="py-2 px-2 text-[#10b981]">0.080</td>
              <td className="py-2 px-2 text-[#f59e0b]">0.140</td>
              <td className="py-2 px-2 text-[#f59e0b]">0.078</td>
              <td className="py-2 px-2 text-[#10b981]">0.812</td>
              <td className="py-2 px-2"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#f59e0b] text-black">WARN</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};
