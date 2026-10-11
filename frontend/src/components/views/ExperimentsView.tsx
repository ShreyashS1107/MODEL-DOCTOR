"use client";

import React from "react";
import { DIAGNOSTIC_RUNS } from "@/lib/mockData";

export const ExperimentsView: React.FC = () => {
  return (
    <div className="space-y-4 font-mono text-xs">
      <div className="p-3 border border-[#443849] bg-[#2A2432] flex items-center justify-between rounded">
        <div>
          <div className="text-xs text-[#F4EFF6] uppercase font-bold tracking-wider">
            08 EXPERIMENTS &amp; REGRESSION TRACKING
          </div>
          <div className="text-[11px] text-[#B7ACBD]">
            Multi-model diagnostic fingerprint comparator and regression tracking across production versions.
          </div>
        </div>
        <div className="text-xs text-[#8E8295]">
          4 CANDIDATE ITERATIONS
        </div>
      </div>

      <div className="border border-[#443849] bg-[#2A2432] p-3 rounded">
        <table className="w-full text-left text-xs">
          <thead>
            <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] uppercase">
              <th className="py-2 px-2.5">Model Version</th>
              <th className="py-2 px-2.5">Health Score</th>
              <th className="py-2 px-2.5">Leakage MI</th>
              <th className="py-2 px-2.5">Max PSI Drift</th>
              <th className="py-2 px-2.5">Calibration ECE</th>
              <th className="py-2 px-2.5">Disparate Impact</th>
              <th className="py-2 px-2.5">Audit State</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-[#443849]/60 tabular-nums">
            <tr className="bg-[#332B3C]/80">
              <td className="py-2.5 px-2.5 text-[#F4EFF6] font-bold">fraud_classifier_v17 (Active)</td>
              <td className="py-2.5 px-2.5 text-[#F18B78] font-bold">73 / 100</td>
              <td className="py-2.5 px-2.5 text-[#F18B78] font-bold">0.941</td>
              <td className="py-2.5 px-2.5 text-[#F18B78] font-bold">0.312</td>
              <td className="py-2.5 px-2.5 text-[#E8B66D]">0.084</td>
              <td className="py-2.5 px-2.5 text-[#E8B66D]">0.771</td>
              <td className="py-2.5 px-2.5"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#F18B78] text-[#211D29] rounded">FAIL</span></td>
            </tr>
            <tr className="hover:bg-[#332B3C]/40 transition-colors">
              <td className="py-2.5 px-2.5 text-[#B7ACBD]">fraud_classifier_v16 (Baseline)</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8] font-bold">89 / 100</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.120</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.045</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.042</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.840</td>
              <td className="py-2.5 px-2.5"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#E8B66D] text-[#211D29] rounded">WARN</span></td>
            </tr>
            <tr className="hover:bg-[#332B3C]/40 transition-colors">
              <td className="py-2.5 px-2.5 text-[#B7ACBD]">credit_risk_lgbm_v04</td>
              <td className="py-2.5 px-2.5 text-[#E8B66D] font-bold">82 / 100</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.080</td>
              <td className="py-2.5 px-2.5 text-[#E8B66D]">0.140</td>
              <td className="py-2.5 px-2.5 text-[#E8B66D]">0.078</td>
              <td className="py-2.5 px-2.5 text-[#B7D7A8]">0.812</td>
              <td className="py-2.5 px-2.5"><span className="px-1.5 py-0.5 text-[9px] font-bold bg-[#E8B66D] text-[#211D29] rounded">WARN</span></td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
};
