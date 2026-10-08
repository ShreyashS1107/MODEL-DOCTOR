"use client";

import React from "react";
import { NavSection } from "@/types/diagnostics";

interface NavEntry {
  id: NavSection;
  code: string;
  label: string;
  criticalCount?: number;
  warningCount?: number;
}

const NAVIGATION_ITEMS: NavEntry[] = [
  { id: "00_INTELLIGENCE", code: "00", label: "DIAGNOSTIC INTEL" },
  { id: "06_INVESTIGATION", code: "06", label: "ROOT-CAUSE INVESTIGATION" },
  { id: "07_REMEDIATION", code: "07", label: "REMEDIATION DECISION" },
  { id: "08_EXPERIMENT", code: "08", label: "EXPERIMENTAL VALIDATION" },
  { id: "09_TEMPORAL", code: "09", label: "TEMPORAL INTELLIGENCE" },
  { id: "01_OVERVIEW", code: "01", label: "OVERVIEW" },
  { id: "02_DATA", code: "02", label: "DATA QUALITY", warningCount: 1 },
  { id: "03_FORENSICS", code: "03", label: "DATA LEAKAGE", criticalCount: 1 },
  { id: "04_DRIFT", code: "04", label: "DISTRIBUTION DRIFT", criticalCount: 1, warningCount: 1 },
  { id: "05_PERFORMANCE", code: "05", label: "MODEL PERFORMANCE" },
  { id: "05_ERROR_FORENSICS", code: "05B", label: "ERROR FORENSICS" },
  { id: "06_EXPLAIN", code: "06B", label: "EXPLAINABILITY" },
  { id: "07_BIAS", code: "07B", label: "FAIRNESS & BIAS", warningCount: 1 },
  { id: "08_ROBUSTNESS", code: "08B", label: "ADVERSARIAL STRESS", warningCount: 1 },
  { id: "09_EXPERIMENTS", code: "09B", label: "EXPERIMENTS" },
  { id: "10_REPORTS", code: "10", label: "REPORTS" },
];

interface WorkstationSidebarProps {
  activeSection: NavSection;
  onSelectSection: (section: NavSection) => void;
}

export const WorkstationSidebar: React.FC<WorkstationSidebarProps> = ({
  activeSection,
  onSelectSection,
}) => {
  return (
    <aside className="w-56 bg-[#0c0e14] border-r border-[#1f2533] flex flex-col justify-between shrink-0 select-none">
      {/* Navigation Tree */}
      <div>
        <div className="px-3 py-2 border-b border-[#1f2533] text-[10px] font-mono text-[#64748b] uppercase tracking-wider">
          DIAGNOSTIC MODULES
        </div>

        <nav className="p-1 space-y-0.5">
          {NAVIGATION_ITEMS.map((item) => {
            const isActive = activeSection === item.id;

            return (
              <button
                key={item.id}
                onClick={() => onSelectSection(item.id)}
                className={`w-full flex items-center justify-between px-2.5 py-1.5 text-xs font-mono text-left transition-colors cursor-pointer ${
                  isActive
                    ? "bg-[#181d28] text-white font-semibold border-l-2 border-[#3b82f6]"
                    : "text-[#94a3b8] hover:bg-[#12161f] hover:text-[#f1f3f8] border-l-2 border-transparent"
                }`}
              >
                <div className="flex items-center gap-2 truncate">
                  <span className={`text-[10px] ${isActive ? "text-[#3b82f6]" : "text-[#64748b]"}`}>
                    {item.code}
                  </span>
                  <span className="truncate">{item.label}</span>
                </div>

                {/* Severity finding badges */}
                <div className="flex items-center gap-1 shrink-0 ml-1">
                  {item.criticalCount !== undefined && item.criticalCount > 0 && (
                    <span className="px-1 py-0.2 text-[9px] font-bold bg-[#ef4444] text-black">
                      {item.criticalCount}
                    </span>
                  )}
                  {item.warningCount !== undefined && item.warningCount > 0 && (
                    <span className="px-1 py-0.2 text-[9px] font-bold bg-[#f59e0b] text-black">
                      {item.warningCount}
                    </span>
                  )}
                </div>
              </button>
            );
          })}
        </nav>
      </div>

      {/* Workspace Footer Metadata */}
      <div className="p-3 border-t border-[#1f2533] text-[10px] font-mono text-[#64748b] space-y-1">
        <div>ORCHESTRATOR: <span className="text-[#94a3b8]">JAVA 21</span></div>
        <div>ML RUNTIME: <span className="text-[#94a3b8]">PY 3.14 / XGB 2.0</span></div>
        <div>LATENCY: <span className="text-[#10b981] tabular-nums">14ms</span></div>
      </div>
    </aside>
  );
};
