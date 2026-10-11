"use client";

import React from "react";
import { MODEL_SUMMARY } from "@/lib/mockData";

export const ReportsView: React.FC = () => {
  return (
    <div className="space-y-4 font-mono text-xs text-[#F4EFF6]">
      <div className="p-3 border border-[#443849] bg-[#2A2432] rounded flex items-center justify-between">
        <div>
          <div className="text-xs text-[#B7ACBD] uppercase font-bold">
            09 COMPLIANCE CERTIFICATION &amp; FORENSIC DOSSIERS
          </div>
          <div className="text-[11px] text-[#8E8295]">
            Export certified audit reports, regulatory compliance documentation, and machine-readable JSON metrics.
          </div>
        </div>
        <div className="text-xs text-[#B7D7A8] font-bold">
          DOSSIER READY
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="p-4 border border-[#443849] bg-[#2A2432] rounded flex flex-col justify-between">
          <div>
            <div className="text-xs font-bold text-[#F4EFF6] uppercase mb-1">
              FULL FORENSIC AUDIT DOSSIER (PDF)
            </div>
            <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
              Complete statistical report covering all diagnostic engines, root cause leakage analysis, and remediation actions.
            </p>
          </div>
          <button
            onClick={() => alert("Downloading PDF Dossier for " + MODEL_SUMMARY.modelId)}
            className="mt-4 py-2 px-3 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] font-mono text-xs font-bold rounded cursor-pointer transition-colors shadow-sm"
          >
            GENERATE PDF AUDIT
          </button>
        </div>

        <div className="p-4 border border-[#443849] bg-[#2A2432] rounded flex flex-col justify-between">
          <div>
            <div className="text-xs font-bold text-[#F4EFF6] uppercase mb-1">
              MACHINE-READABLE EVIDENCE TELEMETRY (JSON)
            </div>
            <p className="text-[11px] text-[#B7ACBD] leading-relaxed">
              Full raw schema containing exact statistical test scores, p-values, and SHAP matrices for CI/CD gates and MLOps pipelines.
            </p>
          </div>
          <button
            onClick={() => alert("Exporting JSON Metrics for " + MODEL_SUMMARY.modelId)}
            className="mt-4 py-2 px-3 bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] font-mono text-xs font-semibold rounded cursor-pointer transition-colors"
          >
            EXPORT JSON DATA
          </button>
        </div>
      </div>
    </div>
  );
};
