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
    <header className="h-12 bg-[#2A2432] border-b border-[#443849] px-3 flex items-center justify-between select-none shrink-0 z-30">
      {/* Left: Brand, Model ID, Dataset ID */}
      <div className="flex items-center gap-3 overflow-hidden">
        {/* Brand */}
        <div className="flex items-center gap-2 pr-3 border-r border-[#443849] shrink-0">
          <span className="font-sans font-bold text-sm tracking-wider text-[#F4EFF6]">
            MODEL DOCTOR
          </span>
          <span className="text-[10px] font-mono text-[#B7ACBD] px-1.5 py-0.5 bg-[#332B3C] border border-[#443849] rounded-sm">
            INSTRUMENT v1.0
          </span>
        </div>

        {/* Model Spec */}
        <div className="flex items-center gap-2 text-xs font-mono shrink-0">
          <span className="text-[#8E8295]">MODEL:</span>
          <span className="text-[#F4EFF6] font-semibold">{summary.modelId}</span>
          <span className="text-[10px] text-[#B7ACBD] px-1.5 py-0.5 bg-[#332B3C] border border-[#443849] rounded-sm">
            v{summary.modelVersion} • {summary.framework}
          </span>
        </div>

        <div className="hidden lg:block h-3.5 w-px bg-[#443849]" />

        {/* Dataset Spec */}
        <div className="hidden lg:flex items-center gap-2 text-xs font-mono shrink-0">
          <span className="text-[#8E8295]">DATASET:</span>
          <span className="text-[#B7ACBD]">{summary.datasetName}</span>
          <span className="text-[10px] text-[#8E8295]">
            ({summary.sampleCount.toLocaleString()} rows, {summary.featureCount} features)
          </span>
        </div>
      </div>

      {/* Right: Engine Status, Run ID & Controls */}
      <div className="flex items-center gap-3 shrink-0">
        {/* Cluster Status Nodes */}
        <div className="hidden xl:flex items-center gap-2 pr-3 border-r border-[#443849] text-[10px] font-mono">
          <div className="flex items-center gap-1.5 text-[#B7ACBD]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#B7D7A8]" />
            <span>ML ENGINE: READY</span>
          </div>
          <span className="text-[#5A4B62]">|</span>
          <div className="flex items-center gap-1.5 text-[#B7ACBD]">
            <span className="w-1.5 h-1.5 rounded-full bg-[#B7D7A8]" />
            <span>ORCHESTRATOR: READY</span>
          </div>
        </div>

        {/* Diagnostic Run ID */}
        <div className="hidden sm:flex items-center gap-1.5 px-2 py-1 bg-[#332B3C] border border-[#443849] text-[11px] font-mono rounded-sm">
          <span className="text-[#8E8295]">ACTIVE RUN:</span>
          <span className="text-[#F4EFF6] font-bold">{summary.runId}</span>
        </div>

        {/* Control Buttons */}
        <div className="flex items-center gap-1.5">
          <button
            onClick={onCompareBaseline}
            className="px-2.5 py-1 text-xs font-mono bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] transition-colors rounded-sm cursor-pointer"
          >
            COMPARE
          </button>
          <button
            onClick={onExportDossier}
            className="px-2.5 py-1 text-xs font-mono bg-[#332B3C] hover:bg-[#3D3448] border border-[#443849] text-[#B7ACBD] hover:text-[#F4EFF6] transition-colors rounded-sm cursor-pointer"
          >
            EXPORT
          </button>
          <button
            onClick={onRunDiagnostic}
            className="px-3 py-1 text-xs font-mono font-bold bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] border border-[#F18B78] transition-colors rounded-sm cursor-pointer shadow-sm"
          >
            RUN DIAGNOSTIC
          </button>
        </div>
      </div>
    </header>
  );
};
