"use client";

import React from "react";
import { ModelDiagnosticSummary } from "@/types/diagnostics";

interface WorkstationHeaderProps {
  summary: ModelDiagnosticSummary;
  onRunDiagnostic: () => void;
  onCompareBaseline: () => void;
  onExportDossier: () => void;
}

export const WorkstationHeader: React.FC<WorkstationHeaderProps> = ({
  summary,
  onRunDiagnostic,
  onCompareBaseline,
  onExportDossier,
}) => {
  return (
    <header className="h-12 bg-[#0c0e14] border-b border-[#1f2533] px-3 flex items-center justify-between select-none shrink-0 z-30">
      {/* Left: Brand, Model ID, Dataset ID */}
      <div className="flex items-center gap-3 overflow-hidden">
        {/* Brand */}
        <div className="flex items-center gap-2 pr-3 border-r border-[#1f2533] shrink-0">
          <span className="font-sans font-bold text-sm tracking-wider text-white">
            MODEL DOCTOR
          </span>
          <span className="text-[10px] font-mono text-[#64748b] tracking-normal">
            INSTRUMENT v1.0
          </span>
        </div>

        {/* Model Spec */}
        <div className="flex items-center gap-2 text-xs font-mono shrink-0">
          <span className="text-[#64748b]">MODEL:</span>
          <span className="text-[#f1f3f8] font-semibold">{summary.modelId}</span>
          <span className="text-[10px] text-[#94a3b8] px-1 py-0.5 bg-[#141822] border border-[#222633]">
            v{summary.modelVersion} • {summary.framework}
          </span>
        </div>

        <div className="hidden lg:block h-3.5 w-px bg-[#1f2533]" />

        {/* Dataset Spec */}
        <div className="hidden lg:flex items-center gap-2 text-xs font-mono shrink-0">
          <span className="text-[#64748b]">DATASET:</span>
          <span className="text-[#94a3b8]">{summary.datasetName}</span>
          <span className="text-[10px] text-[#64748b]">
            ({summary.sampleCount.toLocaleString()} rows, {summary.featureCount} features)
          </span>
        </div>
      </div>

      {/* Right: Engine Status, Run ID & Controls */}
      <div className="flex items-center gap-3 shrink-0">
        {/* Cluster Status Nodes */}
        <div className="hidden xl:flex items-center gap-2 pr-3 border-r border-[#1f2533] text-[10px] font-mono">
          <div className="flex items-center gap-1.5 text-[#94a3b8]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#10b981]" />
            <span>ML ENGINE: READY</span>
          </div>
          <span className="text-[#333a4d]">|</span>
          <div className="flex items-center gap-1.5 text-[#94a3b8]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#10b981]" />
            <span>ORCHESTRATOR: READY</span>
          </div>
        </div>

        {/* Diagnostic Run ID */}
        <div className="hidden sm:flex items-center gap-1.5 px-2 py-1 bg-[#141822] border border-[#222633] text-[11px] font-mono">
          <span className="text-[#64748b]">ACTIVE RUN:</span>
          <span className="text-[#f1f3f8] font-bold">{summary.runId}</span>
        </div>

        {/* Control Buttons */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={onCompareBaseline}
            className="px-2.5 py-1 text-xs font-mono bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#94a3b8] hover:text-[#f1f3f8] transition-colors cursor-pointer"
          >
            COMPARE
          </button>
          <button
            onClick={onExportDossier}
            className="px-2.5 py-1 text-xs font-mono bg-[#141822] hover:bg-[#1a202c] border border-[#222633] text-[#94a3b8] hover:text-[#f1f3f8] transition-colors cursor-pointer"
          >
            EXPORT
          </button>
          <button
            onClick={onRunDiagnostic}
            className="px-3 py-1 text-xs font-mono font-semibold bg-[#1d4ed8] hover:bg-[#2563eb] text-white border border-[#3b82f6] transition-colors cursor-pointer"
          >
            RUN DIAGNOSTIC
          </button>
        </div>
      </div>
    </header>
  );
};
