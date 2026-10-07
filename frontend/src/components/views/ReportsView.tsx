"use client";

import React from "react";
import { MODEL_SUMMARY } from "@/lib/mockData";

export const ReportsView: React.FC = () => {
  return (
    <div className="space-y-4 font-mono text-xs">
      <div className="p-3 border border-[#1f2533] bg-[#0c0e14] flex items-center justify-between">
        <div>
          <div className="text-xs text-[#94a3b8] uppercase font-bold">
            09 COMPLIANCE CERTIFICATION &amp; FORENSIC DOSSIERS
          </div>
          <div className="text-[11px] text-[#64748b]">
            Export certified audit reports, regulatory compliance documentation, and machine-readable JSON metrics.
          </div>
        </div>
        <div className="text-xs text-[#64748b]">
          DOSSIER READY
        </div>
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div className="p-4 border border-[#1f2533] bg-[#0c0e14] flex flex-col justify-between">
          <div>
            <div className="text-xs font-bold text-white uppercase mb-1">
              FULL FORENSIC AUDIT DOSSIER (PDF)
            </div>
            <p className="text-[11px] text-[#94a3b8] leading-relaxed">
              Complete statistical report covering all 7 diagnostic engines, root cause leakage analysis, and remediation actions.
            </p>
          </div>
          <button
            onClick={() => alert("Downloading PDF Dossier for " + MODEL_SUMMARY.modelId)}
            className="mt-4 py-2 px-3 bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-white font-mono text-xs font-semibold cursor-pointer"
          >
            GENERATE PDF AUDIT
          </button>
        </div>

        <div className="p-4 border border-[#1f2533] bg-[#0c0e14] flex flex-col justify-between">
          <div>
            <div className="text-xs font-bold text-white uppercase mb-1">
              MACHINE-READABLE EVIDENCE TELEMETRY (JSON)
            </div>
            <p className="text-[11px] text-[#94a3b8] leading-relaxed">
              Full raw schema containing exact statistical test scores, p-values, and SHAP matrices for CI/CD gates and MLOps pipelines.
            </p>
          </div>
          <button
            onClick={() => alert("Exporting JSON Metrics for " + MODEL_SUMMARY.modelId)}
            className="mt-4 py-2 px-3 bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#94a3b8] hover:text-white font-mono text-xs font-semibold cursor-pointer"
          >
            EXPORT JSON DATA
          </button>
        </div>
      </div>
    </div>
  );
};
