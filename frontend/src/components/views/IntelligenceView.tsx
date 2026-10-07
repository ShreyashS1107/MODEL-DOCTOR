"use client";

import React, { useState } from "react";
import {
  DiagnosticCorrelation,
  RunSummary,
  FeatureProfile,
  InvestigationPriority,
  recalculateDiagnosticCorrelations,
} from "@/lib/api";
import { NavSection } from "@/types/diagnostics";

export interface IntelligenceViewProps {
  runId: string;
  correlations: DiagnosticCorrelation[];
  runSummary: RunSummary | null;
  isLoading?: boolean;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
}

const MODULE_TO_SECTION_MAP: Record<string, NavSection> = {
  DATA_QUALITY: "02_DATA",
  LEAKAGE: "03_FORENSICS",
  DRIFT: "04_DRIFT",
  PERFORMANCE: "05_PERFORMANCE",
  EXPLAINABILITY: "06_EXPLAIN",
  BIAS: "07_BIAS",
  FAIRNESS: "07_BIAS",
  ROBUSTNESS: "08_ROBUSTNESS",
};

export const IntelligenceView: React.FC<IntelligenceViewProps> = ({
  runId,
  correlations = [],
  runSummary,
  isLoading = false,
  onSelectSection,
  onSelectFeature,
  onRefresh,
}) => {
  const [selectedFinding, setSelectedFinding] = useState<DiagnosticCorrelation | null>(null);
  const [priorityFilter, setPriorityFilter] = useState<string>("ALL");
  const [moduleFilter, setModuleFilter] = useState<string>("ALL");
  const [featureSearch, setFeatureSearch] = useState<string>("");
  const [activeTab, setActiveTab] = useState<"FINDINGS" | "FEATURE_MATRIX">("FINDINGS");
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);

  const handleRecalculate = async () => {
    if (!runId) return;
    setIsRecalculating(true);
    try {
      await recalculateDiagnosticCorrelations(runId);
      if (onRefresh) onRefresh();
    } catch (err: any) {
      alert(`Failed to recalculate correlations: ${err.message || err}`);
    } finally {
      setIsRecalculating(false);
    }
  };

  const filteredFindings = correlations.filter((finding) => {
    if (priorityFilter !== "ALL" && finding.priority !== priorityFilter) return false;
    if (moduleFilter !== "ALL" && !finding.sourceModules.includes(moduleFilter)) return false;
    if (featureSearch.trim()) {
      const q = featureSearch.toLowerCase();
      const matchFeat = finding.feature?.toLowerCase().includes(q);
      const matchSummary = finding.summary.toLowerCase().includes(q);
      const matchRule = finding.ruleId.toLowerCase().includes(q);
      if (!matchFeat && !matchSummary && !matchRule) return false;
    }
    return true;
  });

  const getPriorityBadgeClass = (priority: InvestigationPriority) => {
    switch (priority) {
      case "CRITICAL":
        return "bg-[#ef4444] text-black font-bold";
      case "HIGH":
        return "bg-[#f59e0b] text-black font-bold";
      case "MEDIUM":
        return "bg-[#eab308] text-black font-bold";
      case "LOW":
        return "bg-[#3b82f6] text-white";
      case "INFO":
      default:
        return "bg-[#64748b] text-white";
    }
  };

  const getSeverityBadgeClass = (severity: string) => {
    switch (severity) {
      case "CRITICAL":
        return "text-[#ef4444] border-[#ef4444]/40 bg-[#1a0f0f]";
      case "HIGH":
      case "WARNING":
        return "text-[#f59e0b] border-[#f59e0b]/40 bg-[#18130a]";
      case "MEDIUM":
      case "OBSERVATION":
        return "text-[#eab308] border-[#eab308]/40 bg-[#161408]";
      default:
        return "text-[#94a3b8] border-[#334155] bg-[#0c0e14]";
    }
  };

  const featureProfilesList = runSummary?.featureProfiles
    ? Object.values(runSummary.featureProfiles).sort((a, b) => b.findingsCount - a.findingsCount)
    : [];

  return (
    <div className="space-y-6 font-mono text-xs text-[#f1f3f8]">
      {/* HUD Header */}
      <div className="p-4 bg-[#0c0e14] border border-[#1f2533] rounded space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#1f2533] pb-3">
          <div className="flex items-center gap-3">
            <span className="text-sm font-bold text-white tracking-wide uppercase">
              CROSS-MODULE DIAGNOSTIC INTELLIGENCE
            </span>
            <span className="px-2 py-0.5 text-[10px] font-bold bg-[#1e293b] text-[#93c5fd] border border-[#3b82f6]/30 rounded">
              PHASE 4 ENGINE
            </span>
            <span className="px-2 py-0.5 text-[10px] font-medium bg-[#10b981]/10 text-[#34d399] border border-[#10b981]/30 rounded">
              100% DETERMINISTIC RULES
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating || isLoading}
              className="px-3 py-1 bg-[#1e293b] hover:bg-[#334155] text-[#93c5fd] border border-[#3b82f6]/40 text-[11px] font-semibold rounded cursor-pointer transition-colors disabled:opacity-50"
            >
              {isRecalculating ? "RECALCULATING..." : "RE-ANALYZE CORRELATIONS"}
            </button>
          </div>
        </div>

        {/* Status Metrics Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">MODULES EVALUATED</div>
            <div className="text-lg font-bold text-white">
              {runSummary?.completedModules ?? 7} / {runSummary?.moduleCount ?? 7}
            </div>
            <div className="text-[10px] text-[#10b981]">
              {runSummary?.failedModules === 0 ? "ALL ENGINES COMPLETE" : `${runSummary?.failedModules || 0} PARTIAL`}
            </div>
          </div>

          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">CRITICAL FINDINGS</div>
            <div className="text-lg font-bold text-[#ef4444]">
              {runSummary?.criticalFindings ?? correlations.filter((c) => c.priority === "CRITICAL").length}
            </div>
            <div className="text-[10px] text-[#64748b]">IMMEDIATE ACTION</div>
          </div>

          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">HIGH PRIORITY</div>
            <div className="text-lg font-bold text-[#f59e0b]">
              {runSummary?.highPriorityFindings ?? correlations.filter((c) => c.priority === "HIGH").length}
            </div>
            <div className="text-[10px] text-[#64748b]">EVIDENCE CORRELATED</div>
          </div>

          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">MEDIUM / LOW</div>
            <div className="text-lg font-bold text-[#93c5fd]">
              {(runSummary?.mediumPriorityFindings ?? 0) + (runSummary?.lowPriorityFindings ?? 0)}
            </div>
            <div className="text-[10px] text-[#64748b]">INVESTIGATION QUEUE</div>
          </div>

          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">REPEATED FEATURES</div>
            <div className="text-lg font-bold text-[#a78bfa]">
              {runSummary?.topFeatures?.length ?? 0}
            </div>
            <div className="text-[10px] text-[#64748b]">CROSS-MODULE SYMPTOMS</div>
          </div>

          <div className="p-2.5 bg-[#12161f] border border-[#1f2533] rounded">
            <div className="text-[10px] text-[#64748b] uppercase">TOTAL HYPOTHESES</div>
            <div className="text-lg font-bold text-white">
              {correlations.length}
            </div>
            <div className="text-[10px] text-[#10b981]">DETERMINISTIC FINDINGS</div>
          </div>
        </div>

        {/* Top Investigation Areas & Non-Causality Disclaimer */}
        <div className="p-3 bg-[#111622] border border-[#263147] rounded space-y-2">
          <div className="flex items-center justify-between gap-2">
            <div className="text-[11px] font-bold text-[#93c5fd] uppercase tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[#3b82f6] animate-pulse"></span>
              PRIMARY INVESTIGATION DIRECTIVES
            </div>
            <div className="text-[10px] text-[#94a3b8] italic">
              Associative evidence only • No causal assertions
            </div>
          </div>

          {runSummary?.topInvestigationAreas && runSummary.topInvestigationAreas.length > 0 ? (
            <div className="flex flex-wrap gap-2 pt-1">
              {runSummary.topInvestigationAreas.map((area, idx) => (
                <div
                  key={idx}
                  className="px-2.5 py-1 bg-[#182030] text-[#cbd5e1] border border-[#3b82f6]/30 text-[11px] rounded flex items-center gap-2"
                >
                  <span className="text-[#3b82f6] font-bold">#{idx + 1}</span>
                  <span>{area}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-[11px] text-[#64748b]">
              No multi-module anomaly intersections flagged. System operating within baseline thresholds.
            </div>
          )}
        </div>
      </div>

      {/* Navigation View Switcher (Findings vs Feature Matrix) */}
      <div className="flex items-center justify-between gap-4 border-b border-[#1f2533] pb-2">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab("FINDINGS")}
            className={`px-3 py-1.5 text-xs font-semibold rounded cursor-pointer transition-colors ${
              activeTab === "FINDINGS"
                ? "bg-[#1f293d] text-white border-b-2 border-[#3b82f6]"
                : "text-[#94a3b8] hover:text-white"
            }`}
          >
            INVESTIGATION FINDINGS ({filteredFindings.length})
          </button>
          <button
            onClick={() => setActiveTab("FEATURE_MATRIX")}
            className={`px-3 py-1.5 text-xs font-semibold rounded cursor-pointer transition-colors ${
              activeTab === "FEATURE_MATRIX"
                ? "bg-[#1f293d] text-white border-b-2 border-[#3b82f6]"
                : "text-[#94a3b8] hover:text-white"
            }`}
          >
            FEATURE-CENTRIC FORENSIC MATRIX ({featureProfilesList.length})
          </button>
        </div>

        {activeTab === "FINDINGS" && (
          <div className="flex flex-wrap items-center gap-2">
            {/* Search Input */}
            <input
              type="text"
              placeholder="Filter feature / rule / summary..."
              value={featureSearch}
              onChange={(e) => setFeatureSearch(e.target.value)}
              className="px-2.5 py-1 bg-[#12161f] border border-[#1f2533] text-xs text-white rounded focus:outline-none focus:border-[#3b82f6] w-48 sm:w-60"
            />

            {/* Priority Filter */}
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="px-2 py-1 bg-[#12161f] border border-[#1f2533] text-xs text-white rounded focus:outline-none"
            >
              <option value="ALL">ALL PRIORITIES</option>
              <option value="CRITICAL">CRITICAL</option>
              <option value="HIGH">HIGH</option>
              <option value="MEDIUM">MEDIUM</option>
              <option value="LOW">LOW</option>
              <option value="INFO">INFO</option>
            </select>

            {/* Module Filter */}
            <select
              value={moduleFilter}
              onChange={(e) => setModuleFilter(e.target.value)}
              className="px-2 py-1 bg-[#12161f] border border-[#1f2533] text-xs text-white rounded focus:outline-none"
            >
              <option value="ALL">ALL MODULES</option>
              <option value="DRIFT">DRIFT</option>
              <option value="EXPLAINABILITY">EXPLAINABILITY</option>
              <option value="PERFORMANCE">PERFORMANCE</option>
              <option value="ROBUSTNESS">ROBUSTNESS</option>
              <option value="LEAKAGE">LEAKAGE</option>
              <option value="DATA_QUALITY">DATA QUALITY</option>
              <option value="BIAS">BIAS / FAIRNESS</option>
            </select>
          </div>
        )}
      </div>

      {/* Main Tab Content */}
      {activeTab === "FINDINGS" ? (
        <div className="space-y-4">
          {filteredFindings.length === 0 ? (
            <div className="p-8 text-center bg-[#0c0e14] border border-[#1f2533] rounded space-y-2">
              <div className="text-sm font-semibold text-[#94a3b8]">NO CROSS-MODULE FINDINGS MATCH FILTER</div>
              <p className="text-xs text-[#64748b]">
                Either no diagnostic anomalies satisfy the correlation threshold or active filters exclude them.
              </p>
            </div>
          ) : (
            filteredFindings.map((finding) => (
              <div
                key={finding.id}
                className="p-4 bg-[#0c0e14] border border-[#1f2533] hover:border-[#3b82f6]/50 rounded transition-colors space-y-3"
              >
                {/* Finding Header */}
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`px-2 py-0.5 text-[10px] rounded ${getPriorityBadgeClass(finding.priority)}`}>
                      {finding.priority} PRIORITY
                    </span>
                    <span className="text-[10px] px-2 py-0.5 bg-[#1e293b] text-[#94a3b8] border border-[#334155] rounded">
                      SCORE: {finding.priorityScore}
                    </span>
                    <span className={`text-[10px] px-2 py-0.5 border rounded ${getSeverityBadgeClass(finding.severity)}`}>
                      {finding.severity}
                    </span>
                    <span className="text-[10px] px-2 py-0.5 bg-[#182030] text-[#93c5fd] border border-[#3b82f6]/30 rounded">
                      CONFIDENCE: {finding.confidence}
                    </span>
                    <span className="text-[10px] text-[#64748b]">{finding.ruleId}</span>
                  </div>

                  {finding.feature && (
                    <div className="flex items-center gap-1.5">
                      <span className="text-[10px] text-[#64748b]">TARGET FEATURE:</span>
                      <span className="px-2 py-0.5 bg-[#2d1b4e] text-[#d8b4fe] border border-[#7c3aed]/40 text-xs font-bold rounded">
                        {finding.feature}
                      </span>
                    </div>
                  )}
                </div>

                {/* Finding Summary */}
                <div className="space-y-1">
                  <div className="text-sm font-bold text-white tracking-wide">
                    {finding.summary}
                  </div>
                  {finding.whyItMatters && (
                    <div className="text-xs text-[#cbd5e1] leading-relaxed">
                      <span className="text-[#94a3b8] font-semibold">Diagnostic Context: </span>
                      {finding.whyItMatters}
                    </div>
                  )}
                </div>

                {/* Investigation Guidance & Associative Disclaimer */}
                {finding.investigationDirection && (
                  <div className="p-2.5 bg-[#131722] border border-[#242e42] rounded text-xs space-y-1">
                    <div className="text-[10px] font-bold text-[#60a5fa] uppercase tracking-wide">
                      RECOMMENDED INVESTIGATION DIRECTIVE
                    </div>
                    <div className="text-[#cbd5e1]">{finding.investigationDirection}</div>
                    <div className="text-[10px] text-[#64748b] pt-1 border-t border-[#1f2533] italic">
                      Finding represents deterministic associative evidence from {finding.sourceModules.join(", ")}. It does not assert direct causality.
                    </div>
                  </div>
                )}

                {/* Evidence Metrics Chips & Source Modules */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-[#1a202c]">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[10px] text-[#64748b] uppercase">SOURCE MODULES:</span>
                    {finding.sourceModules.map((mod) => {
                      const targetNav = MODULE_TO_SECTION_MAP[mod];
                      return (
                        <button
                          key={mod}
                          onClick={() => targetNav && onSelectSection && onSelectSection(targetNav)}
                          className="px-2 py-0.5 bg-[#182030] hover:bg-[#25324d] text-[#93c5fd] border border-[#3b82f6]/30 text-[10px] rounded cursor-pointer transition-colors"
                          title={`Navigate to ${mod} raw diagnostic view`}
                        >
                          {mod} →
                        </button>
                      );
                    })}
                  </div>

                  <button
                    onClick={() => setSelectedFinding(finding)}
                    className="px-3 py-1 bg-[#1e293b] hover:bg-[#334155] text-white border border-[#475569] text-xs rounded cursor-pointer transition-colors"
                  >
                    INSPECT EVIDENCE GRAPH &amp; PROVENANCE
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      ) : (
        /* Feature-Centric Forensic Matrix */
        <div className="space-y-4">
          <div className="p-3 bg-[#0c0e14] border border-[#1f2533] rounded">
            <div className="text-xs font-bold text-white mb-1">
              CROSS-MODULE FEATURE FORENSIC MATRIX
            </div>
            <p className="text-[11px] text-[#94a3b8]">
              Consolidates per-feature telemetry across Drift, Explainability, Robustness, Data Quality, and Leakage modules to pinpoint multi-dimensional risk.
            </p>
          </div>

          <div className="overflow-x-auto border border-[#1f2533] rounded bg-[#0c0e14]">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#1f2533] bg-[#12161f] text-[10px] text-[#64748b] uppercase tracking-wider">
                  <th className="p-2.5">FEATURE</th>
                  <th className="p-2.5">DRIFT (PSI)</th>
                  <th className="p-2.5">EXPLAINABILITY (SHAP)</th>
                  <th className="p-2.5">ROBUSTNESS (FLIP %)</th>
                  <th className="p-2.5">LEAKAGE (MI / R)</th>
                  <th className="p-2.5">QUALITY (MISSING %)</th>
                  <th className="p-2.5 text-right">CROSS-MODULE FINDINGS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#1a202c] text-xs">
                {featureProfilesList.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-6 text-center text-[#64748b]">
                      No feature profiles aggregated for this run.
                    </td>
                  </tr>
                ) : (
                  featureProfilesList.map((fp) => (
                    <tr key={fp.featureName} className="hover:bg-[#151b26] transition-colors">
                      <td className="p-2.5 font-bold text-white">
                        <button
                          onClick={() => onSelectFeature && onSelectFeature(fp.featureName)}
                          className="hover:text-[#93c5fd] hover:underline text-left cursor-pointer"
                        >
                          {fp.featureName}
                        </button>
                      </td>
                      <td className="p-2.5">
                        {fp.driftPsi !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-white font-mono">{fp.driftPsi.toFixed(3)}</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.driftSeverity || "LOW")}`}>
                              {fp.driftSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#475569]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.importanceRank !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-[#a78bfa] font-bold">#{fp.importanceRank}</span>
                            {fp.importanceScore !== undefined && (
                              <span className="text-[#94a3b8] text-[10px]">({fp.importanceScore.toFixed(3)})</span>
                            )}
                          </div>
                        ) : (
                          <span className="text-[#475569]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.robustnessFlipRate !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-white font-mono">{(fp.robustnessFlipRate * 100).toFixed(1)}%</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.robustnessSeverity || "LOW")}`}>
                              {fp.robustnessSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#475569]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.leakageCorrelation !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-white font-mono">{fp.leakageCorrelation.toFixed(3)}</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.leakageSeverity || "LOW")}`}>
                              {fp.leakageSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#475569]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.missingPercentage !== undefined ? (
                          <span className="text-white font-mono">{(fp.missingPercentage * 100).toFixed(1)}%</span>
                        ) : (
                          <span className="text-[#475569]">—</span>
                        )}
                      </td>
                      <td className="p-2.5 text-right">
                        {fp.findingsCount > 0 ? (
                          <span className="px-2 py-0.5 bg-[#ef4444]/20 text-[#f87171] border border-[#ef4444]/40 font-bold rounded">
                            {fp.findingsCount} FINDING{fp.findingsCount > 1 ? "S" : ""}
                          </span>
                        ) : (
                          <span className="text-[#10b981] font-semibold text-[10px]">NOMINAL</span>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>
      )}

      {/* Finding Evidence & Provenance Inspector Modal */}
      {selectedFinding && (
        <div className="fixed inset-0 z-50 bg-black/80 flex items-center justify-center p-4 backdrop-blur-sm">
          <div className="bg-[#0c0e14] border border-[#3b82f6] rounded-lg max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden shadow-2xl">
            {/* Modal Header */}
            <div className="p-4 border-b border-[#1f2533] flex items-center justify-between bg-[#12161f]">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 text-[10px] rounded ${getPriorityBadgeClass(selectedFinding.priority)}`}>
                    {selectedFinding.priority} PRIORITY
                  </span>
                  <span className="text-xs font-bold text-white tracking-wide">
                    {selectedFinding.ruleId}
                  </span>
                </div>
                <div className="text-[11px] text-[#94a3b8]">
                  Deterministic Finding ID: {selectedFinding.id}
                </div>
              </div>

              <button
                onClick={() => setSelectedFinding(null)}
                className="text-[#94a3b8] hover:text-white text-lg font-bold px-2 py-1 cursor-pointer"
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-4 overflow-y-auto space-y-4 flex-1">
              {/* Summary */}
              <div className="space-y-1">
                <div className="text-[10px] text-[#64748b] uppercase">FINDING SUMMARY</div>
                <div className="text-sm font-bold text-white">{selectedFinding.summary}</div>
              </div>

              {/* Target Feature */}
              {selectedFinding.feature && (
                <div className="p-2.5 bg-[#181d28] border border-[#334155] rounded flex items-center justify-between">
                  <span className="text-xs text-[#94a3b8]">AFFECTED FEATURE:</span>
                  <span className="text-xs font-bold text-[#d8b4fe] bg-[#2d1b4e] px-2 py-0.5 rounded border border-[#7c3aed]/40">
                    {selectedFinding.feature}
                  </span>
                </div>
              )}

              {/* Provenance & Source Result Traceability */}
              <div className="p-3 bg-[#12161f] border border-[#1f2533] rounded space-y-2">
                <div className="text-[10px] font-bold text-[#93c5fd] uppercase tracking-wider">
                  SOURCE PROVENANCE &amp; RESULT TRACEABILITY
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#64748b]">Run ID: </span>
                    <span className="text-white font-mono">{selectedFinding.runId}</span>
                  </div>
                  <div>
                    <span className="text-[#64748b]">Confidence: </span>
                    <span className="text-[#93c5fd] font-bold">{selectedFinding.confidence}</span>
                  </div>
                  <div>
                    <span className="text-[#64748b]">Priority Score: </span>
                    <span className="text-white font-bold">{selectedFinding.priorityScore} / 100</span>
                  </div>
                  <div>
                    <span className="text-[#64748b]">Severity: </span>
                    <span className="text-[#f59e0b] font-bold">{selectedFinding.severity}</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-[#1f2533] space-y-1">
                  <span className="text-[#64748b] text-[10px] uppercase">SOURCE RESULT RECORD IDS:</span>
                  <div className="flex flex-wrap gap-1">
                    {selectedFinding.sourceResultIds.map((rid) => (
                      <span key={rid} className="px-2 py-0.5 bg-[#1e293b] text-[#93c5fd] text-[10px] font-mono rounded">
                        {rid}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Structured Evidence Payload */}
              <div className="space-y-1">
                <div className="text-[10px] text-[#64748b] uppercase">MATHEMATICAL EVIDENCE DATA</div>
                <pre className="p-3 bg-[#090b0e] border border-[#1f2533] rounded text-[11px] text-[#a5f3fc] overflow-x-auto">
                  {JSON.stringify(selectedFinding.evidence, null, 2)}
                </pre>
              </div>

              {/* Causality Disclaimer */}
              <div className="p-2.5 bg-[#16120b] border border-[#78350f] rounded text-[11px] text-[#fde047] space-y-1">
                <div className="font-bold uppercase tracking-wider">METHODOLOGY &amp; LIMITATIONS</div>
                <p>
                  This cross-module finding was evaluated via deterministic rule evaluation against persisted Phase 3 diagnostic outputs.
                  Model Doctor reports associative patterns and empirical relationships; these findings do not prove unobserved causality.
                </p>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-3 border-t border-[#1f2533] flex justify-end bg-[#12161f]">
              <button
                onClick={() => setSelectedFinding(null)}
                className="px-4 py-1.5 bg-[#1e293b] hover:bg-[#334155] text-white text-xs font-semibold rounded cursor-pointer"
              >
                CLOSE INSPECTOR
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
