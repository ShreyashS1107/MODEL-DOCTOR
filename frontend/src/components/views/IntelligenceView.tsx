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
        return "bg-[#F18B78] text-[#211D29] font-bold";
      case "HIGH":
        return "bg-[#F18B78]/80 text-[#211D29] font-bold";
      case "MEDIUM":
        return "bg-[#E8B66D] text-[#211D29] font-bold";
      case "LOW":
        return "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]";
      case "INFO":
      default:
        return "bg-[#2A2432] text-[#8E8295] border border-[#443849]";
    }
  };

  const getSeverityBadgeClass = (severity: string) => {
    switch (severity) {
      case "CRITICAL":
        return "text-[#F18B78] border-[#F18B78]/40 bg-[#F18B78]/10";
      case "HIGH":
      case "WARNING":
        return "text-[#E8B66D] border-[#E8B66D]/40 bg-[#E8B66D]/10";
      case "MEDIUM":
      case "OBSERVATION":
        return "text-[#E8B66D] border-[#E8B66D]/30 bg-[#E8B66D]/5";
      default:
        return "text-[#B7ACBD] border-[#443849] bg-[#1C1824]";
    }
  };

  const featureProfilesList = runSummary?.featureProfiles
    ? Object.values(runSummary.featureProfiles).sort((a, b) => b.findingsCount - a.findingsCount)
    : [];

  return (
    <div className="space-y-6 font-mono text-xs text-[#F4EFF6]">
      {/* HUD Header */}
      <div className="p-4 bg-[#2A2432] border border-[#443849] rounded space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-[#443849] pb-3">
          <div className="flex items-center gap-3">
            <span className="text-sm font-bold text-[#F4EFF6] tracking-wide uppercase">
              CROSS-MODULE DIAGNOSTIC INTELLIGENCE
            </span>
            <span className="px-2 py-0.5 text-[10px] font-bold bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/30 rounded">
              PHASE 4 ENGINE
            </span>
            <span className="px-2 py-0.5 text-[10px] font-medium bg-[#B7D7A8]/10 text-[#B7D7A8] border border-[#B7D7A8]/30 rounded">
              100% DETERMINISTIC RULES
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleRecalculate}
              disabled={isRecalculating || isLoading}
              className="px-3 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] text-[11px] font-bold rounded cursor-pointer transition-colors disabled:opacity-50"
            >
              {isRecalculating ? "RECALCULATING..." : "RE-ANALYZE CORRELATIONS"}
            </button>
          </div>
        </div>

        {/* Status Metrics Bar */}
        <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-6 gap-3">
          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">MODULES EVALUATED</div>
            <div className="text-lg font-bold text-[#F4EFF6]">
              {runSummary?.completedModules ?? 7} / {runSummary?.moduleCount ?? 7}
            </div>
            <div className="text-[10px] text-[#B7D7A8]">
              {runSummary?.failedModules === 0 ? "ALL ENGINES COMPLETE" : `${runSummary?.failedModules || 0} PARTIAL`}
            </div>
          </div>

          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">CRITICAL FINDINGS</div>
            <div className="text-lg font-bold text-[#F18B78]">
              {runSummary?.criticalFindings ?? correlations.filter((c) => c.priority === "CRITICAL").length}
            </div>
            <div className="text-[10px] text-[#8E8295]">IMMEDIATE ACTION</div>
          </div>

          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">HIGH PRIORITY</div>
            <div className="text-lg font-bold text-[#E8B66D]">
              {runSummary?.highPriorityFindings ?? correlations.filter((c) => c.priority === "HIGH").length}
            </div>
            <div className="text-[10px] text-[#8E8295]">EVIDENCE CORRELATED</div>
          </div>

          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">MEDIUM / LOW</div>
            <div className="text-lg font-bold text-[#B7ACBD]">
              {(runSummary?.mediumPriorityFindings ?? 0) + (runSummary?.lowPriorityFindings ?? 0)}
            </div>
            <div className="text-[10px] text-[#8E8295]">INVESTIGATION QUEUE</div>
          </div>

          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">REPEATED FEATURES</div>
            <div className="text-lg font-bold text-[#F18B78]">
              {runSummary?.topFeatures?.length ?? 0}
            </div>
            <div className="text-[10px] text-[#8E8295]">CROSS-MODULE SYMPTOMS</div>
          </div>

          <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded">
            <div className="text-[10px] text-[#8E8295] uppercase">TOTAL HYPOTHESES</div>
            <div className="text-lg font-bold text-[#F4EFF6]">
              {correlations.length}
            </div>
            <div className="text-[10px] text-[#B7D7A8]">DETERMINISTIC FINDINGS</div>
          </div>
        </div>

        {/* Top Investigation Areas & Non-Causality Disclaimer */}
        <div className="p-3 bg-[#332B3C] border border-[#443849] rounded space-y-2">
          <div className="flex items-center justify-between gap-2">
            <div className="text-[11px] font-bold text-[#F18B78] uppercase tracking-wider flex items-center gap-2">
              <span className="w-2 h-2 rounded-full bg-[#F18B78] animate-pulse"></span>
              PRIMARY INVESTIGATION DIRECTIVES
            </div>
            <div className="text-[10px] text-[#8E8295] italic">
              Associative evidence only • No causal assertions
            </div>
          </div>

          {runSummary?.topInvestigationAreas && runSummary.topInvestigationAreas.length > 0 ? (
            <div className="flex flex-wrap gap-2 pt-1">
              {runSummary.topInvestigationAreas.map((area, idx) => (
                <div
                  key={idx}
                  className="px-2.5 py-1 bg-[#2A2432] text-[#B7ACBD] border border-[#443849] text-[11px] rounded flex items-center gap-2"
                >
                  <span className="text-[#F18B78] font-bold">#{idx + 1}</span>
                  <span>{area}</span>
                </div>
              ))}
            </div>
          ) : (
            <div className="text-[11px] text-[#8E8295]">
              No multi-module anomaly intersections flagged. System operating within baseline thresholds.
            </div>
          )}
        </div>
      </div>

      {/* Navigation View Switcher (Findings vs Feature Matrix) */}
      <div className="flex items-center justify-between gap-4 border-b border-[#443849] pb-2">
        <div className="flex items-center gap-2">
          <button
            onClick={() => setActiveTab("FINDINGS")}
            className={`px-3 py-1.5 text-xs font-semibold rounded cursor-pointer transition-colors ${
              activeTab === "FINDINGS"
                ? "bg-[#332B3C] text-[#F4EFF6] border-b-2 border-[#F18B78]"
                : "text-[#B7ACBD] hover:text-[#F4EFF6]"
            }`}
          >
            INVESTIGATION FINDINGS ({filteredFindings.length})
          </button>
          <button
            onClick={() => setActiveTab("FEATURE_MATRIX")}
            className={`px-3 py-1.5 text-xs font-semibold rounded cursor-pointer transition-colors ${
              activeTab === "FEATURE_MATRIX"
                ? "bg-[#332B3C] text-[#F4EFF6] border-b-2 border-[#F18B78]"
                : "text-[#B7ACBD] hover:text-[#F4EFF6]"
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
              className="px-2.5 py-1 bg-[#1C1824] border border-[#443849] text-xs text-[#F4EFF6] rounded focus:outline-none focus:border-[#F18B78] w-48 sm:w-60"
            />

            {/* Priority Filter */}
            <select
              value={priorityFilter}
              onChange={(e) => setPriorityFilter(e.target.value)}
              className="px-2 py-1 bg-[#1C1824] border border-[#443849] text-xs text-[#F4EFF6] rounded focus:outline-none"
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
              className="px-2 py-1 bg-[#1C1824] border border-[#443849] text-xs text-[#F4EFF6] rounded focus:outline-none"
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
            <div className="p-8 text-center bg-[#2A2432] border border-[#443849] rounded space-y-2">
              <div className="text-sm font-semibold text-[#B7ACBD]">NO CROSS-MODULE FINDINGS MATCH FILTER</div>
              <p className="text-xs text-[#8E8295]">
                Either no diagnostic anomalies satisfy the correlation threshold or active filters exclude them.
              </p>
            </div>
          ) : (
            filteredFindings.map((finding) => (
              <div
                key={finding.id}
                className="p-4 bg-[#2A2432] border border-[#443849] hover:border-[#F18B78]/50 rounded transition-colors space-y-3"
              >
                {/* Finding Header */}
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2 flex-wrap">
                    <span className={`px-2 py-0.5 text-[10px] rounded ${getPriorityBadgeClass(finding.priority)}`}>
                      {finding.priority} PRIORITY
                    </span>
                    <span className="text-[10px] px-2 py-0.5 bg-[#332B3C] text-[#B7ACBD] border border-[#443849] rounded">
                      SCORE: {finding.priorityScore}
                    </span>
                    <span className={`text-[10px] px-2 py-0.5 border rounded ${getSeverityBadgeClass(finding.severity)}`}>
                      {finding.severity}
                    </span>
                    <span className="text-[10px] px-2 py-0.5 bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/30 rounded">
                      CONFIDENCE: {finding.confidence}
                    </span>
                    <span className="text-[10px] text-[#8E8295]">{finding.ruleId}</span>
                  </div>

                  {finding.feature && (
                    <div className="flex items-center gap-1.5">
                      <span className="text-[10px] text-[#8E8295]">TARGET FEATURE:</span>
                      <span className="px-2 py-0.5 bg-[#332B3C] text-[#F18B78] border border-[#5A4B62] text-xs font-bold rounded">
                        {finding.feature}
                      </span>
                    </div>
                  )}
                </div>

                {/* Finding Summary */}
                <div className="space-y-1">
                  <div className="text-sm font-bold text-[#F4EFF6] tracking-wide">
                    {finding.summary}
                  </div>
                  {finding.whyItMatters && (
                    <div className="text-xs text-[#B7ACBD] leading-relaxed">
                      <span className="text-[#8E8295] font-semibold">Diagnostic Context: </span>
                      {finding.whyItMatters}
                    </div>
                  )}
                </div>

                {/* Investigation Guidance & Associative Disclaimer */}
                {finding.investigationDirection && (
                  <div className="p-2.5 bg-[#1C1824] border border-[#443849] rounded text-xs space-y-1">
                    <div className="text-[10px] font-bold text-[#F18B78] uppercase tracking-wide">
                      RECOMMENDED INVESTIGATION DIRECTIVE
                    </div>
                    <div className="text-[#B7ACBD]">{finding.investigationDirection}</div>
                    <div className="text-[10px] text-[#8E8295] pt-1 border-t border-[#443849] italic">
                      Finding represents deterministic associative evidence from {finding.sourceModules.join(", ")}. It does not assert direct causality.
                    </div>
                  </div>
                )}

                {/* Evidence Metrics Chips & Source Modules */}
                <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-[#443849]">
                  <div className="flex flex-wrap items-center gap-2">
                    <span className="text-[10px] text-[#8E8295] uppercase">SOURCE MODULES:</span>
                    {finding.sourceModules.map((mod) => {
                      const targetNav = MODULE_TO_SECTION_MAP[mod];
                      return (
                        <button
                          key={mod}
                          onClick={() => targetNav && onSelectSection && onSelectSection(targetNav)}
                          className="px-2 py-0.5 bg-[#332B3C] hover:bg-[#3D3448] text-[#F18B78] border border-[#443849] text-[10px] rounded cursor-pointer transition-colors"
                          title={`Navigate to ${mod} raw diagnostic view`}
                        >
                          {mod} →
                        </button>
                      );
                    })}
                  </div>

                  <button
                    onClick={() => setSelectedFinding(finding)}
                    className="px-3 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] text-xs rounded cursor-pointer transition-colors"
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
          <div className="p-3 bg-[#2A2432] border border-[#443849] rounded">
            <div className="text-xs font-bold text-[#F4EFF6] mb-1">
              CROSS-MODULE FEATURE FORENSIC MATRIX
            </div>
            <p className="text-[11px] text-[#B7ACBD]">
              Consolidates per-feature telemetry across Drift, Explainability, Robustness, Data Quality, and Leakage modules to pinpoint multi-dimensional risk.
            </p>
          </div>

          <div className="overflow-x-auto border border-[#443849] rounded bg-[#2A2432]">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="border-b border-[#443849] bg-[#1C1824] text-[10px] text-[#8E8295] uppercase tracking-wider">
                  <th className="p-2.5">FEATURE</th>
                  <th className="p-2.5">DRIFT (PSI)</th>
                  <th className="p-2.5">EXPLAINABILITY (SHAP)</th>
                  <th className="p-2.5">ROBUSTNESS (FLIP %)</th>
                  <th className="p-2.5">LEAKAGE (MI / R)</th>
                  <th className="p-2.5">QUALITY (MISSING %)</th>
                  <th className="p-2.5 text-right">CROSS-MODULE FINDINGS</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#443849] text-xs">
                {featureProfilesList.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="p-6 text-center text-[#8E8295]">
                      No feature profiles aggregated for this run.
                    </td>
                  </tr>
                ) : (
                  featureProfilesList.map((fp) => (
                    <tr key={fp.featureName} className="hover:bg-[#332B3C]/50 transition-colors">
                      <td className="p-2.5 font-bold text-[#F4EFF6]">
                        <button
                          onClick={() => onSelectFeature && onSelectFeature(fp.featureName)}
                          className="hover:text-[#F18B78] hover:underline text-left cursor-pointer"
                        >
                          {fp.featureName}
                        </button>
                      </td>
                      <td className="p-2.5">
                        {fp.driftPsi !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-[#F4EFF6] font-mono">{fp.driftPsi.toFixed(3)}</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.driftSeverity || "LOW")}`}>
                              {fp.driftSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#8E8295]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.importanceRank !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-[#F18B78] font-bold">#{fp.importanceRank}</span>
                            {fp.importanceScore !== undefined && (
                              <span className="text-[#B7ACBD] text-[10px]">({fp.importanceScore.toFixed(3)})</span>
                            )}
                          </div>
                        ) : (
                          <span className="text-[#8E8295]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.robustnessFlipRate !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-[#F4EFF6] font-mono">{(fp.robustnessFlipRate * 100).toFixed(1)}%</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.robustnessSeverity || "LOW")}`}>
                              {fp.robustnessSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#8E8295]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.leakageCorrelation !== undefined ? (
                          <div className="flex items-center gap-1.5">
                            <span className="text-[#F4EFF6] font-mono">{fp.leakageCorrelation.toFixed(3)}</span>
                            <span className={`text-[9px] px-1 py-0.2 border rounded ${getSeverityBadgeClass(fp.leakageSeverity || "LOW")}`}>
                              {fp.leakageSeverity || "LOW"}
                            </span>
                          </div>
                        ) : (
                          <span className="text-[#8E8295]">—</span>
                        )}
                      </td>
                      <td className="p-2.5">
                        {fp.missingPercentage !== undefined ? (
                          <span className="text-[#F4EFF6] font-mono">{(fp.missingPercentage * 100).toFixed(1)}%</span>
                        ) : (
                          <span className="text-[#8E8295]">—</span>
                        )}
                      </td>
                      <td className="p-2.5 text-right">
                        {fp.findingsCount > 0 ? (
                          <span className="px-2 py-0.5 bg-[#F18B78]/20 text-[#F18B78] border border-[#F18B78]/40 font-bold rounded">
                            {fp.findingsCount} FINDING{fp.findingsCount > 1 ? "S" : ""}
                          </span>
                        ) : (
                          <span className="text-[#B7D7A8] font-semibold text-[10px]">NOMINAL</span>
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
          <div className="bg-[#2A2432] border border-[#443849] rounded-lg max-w-3xl w-full max-h-[90vh] flex flex-col overflow-hidden shadow-2xl">
            {/* Modal Header */}
            <div className="p-4 border-b border-[#443849] flex items-center justify-between bg-[#332B3C]">
              <div className="space-y-1">
                <div className="flex items-center gap-2">
                  <span className={`px-2 py-0.5 text-[10px] rounded ${getPriorityBadgeClass(selectedFinding.priority)}`}>
                    {selectedFinding.priority} PRIORITY
                  </span>
                  <span className="text-xs font-bold text-[#F4EFF6] tracking-wide">
                    {selectedFinding.ruleId}
                  </span>
                </div>
                <div className="text-[11px] text-[#B7ACBD]">
                  Deterministic Finding ID: {selectedFinding.id}
                </div>
              </div>

              <button
                onClick={() => setSelectedFinding(null)}
                className="text-[#8E8295] hover:text-[#F4EFF6] text-lg font-bold px-2 py-1 cursor-pointer"
              >
                ✕
              </button>
            </div>

            {/* Modal Body */}
            <div className="p-4 overflow-y-auto space-y-4 flex-1">
              {/* Summary */}
              <div className="space-y-1">
                <div className="text-[10px] text-[#8E8295] uppercase">FINDING SUMMARY</div>
                <div className="text-sm font-bold text-[#F4EFF6]">{selectedFinding.summary}</div>
              </div>

              {/* Target Feature */}
              {selectedFinding.feature && (
                <div className="p-2.5 bg-[#332B3C] border border-[#443849] rounded flex items-center justify-between">
                  <span className="text-xs text-[#B7ACBD]">AFFECTED FEATURE:</span>
                  <span className="text-xs font-bold text-[#F18B78] bg-[#1C1824] px-2 py-0.5 rounded border border-[#5A4B62]">
                    {selectedFinding.feature}
                  </span>
                </div>
              )}

              {/* Provenance & Source Result Traceability */}
              <div className="p-3 bg-[#1C1824] border border-[#443849] rounded space-y-2">
                <div className="text-[10px] font-bold text-[#F18B78] uppercase tracking-wider">
                  SOURCE PROVENANCE &amp; RESULT TRACEABILITY
                </div>
                <div className="grid grid-cols-2 gap-2 text-xs">
                  <div>
                    <span className="text-[#8E8295]">Run ID: </span>
                    <span className="text-[#F4EFF6] font-mono">{selectedFinding.runId}</span>
                  </div>
                  <div>
                    <span className="text-[#8E8295]">Confidence: </span>
                    <span className="text-[#F18B78] font-bold">{selectedFinding.confidence}</span>
                  </div>
                  <div>
                    <span className="text-[#8E8295]">Priority Score: </span>
                    <span className="text-[#F4EFF6] font-bold">{selectedFinding.priorityScore} / 100</span>
                  </div>
                  <div>
                    <span className="text-[#8E8295]">Severity: </span>
                    <span className="text-[#E8B66D] font-bold">{selectedFinding.severity}</span>
                  </div>
                </div>

                <div className="pt-2 border-t border-[#443849] space-y-1">
                  <span className="text-[#8E8295] text-[10px] uppercase">SOURCE RESULT RECORD IDS:</span>
                  <div className="flex flex-wrap gap-1">
                    {selectedFinding.sourceResultIds.map((rid) => (
                      <span key={rid} className="px-2 py-0.5 bg-[#332B3C] text-[#F18B78] text-[10px] font-mono rounded">
                        {rid}
                      </span>
                    ))}
                  </div>
                </div>
              </div>

              {/* Structured Evidence Payload */}
              <div className="space-y-1">
                <div className="text-[10px] text-[#8E8295] uppercase">MATHEMATICAL EVIDENCE DATA</div>
                <pre className="p-3 bg-[#1C1824] border border-[#443849] rounded text-[11px] text-[#B7D7A8] overflow-x-auto">
                  {JSON.stringify(selectedFinding.evidence, null, 2)}
                </pre>
              </div>

              {/* Causality Disclaimer */}
              <div className="p-2.5 bg-[#332B3C] border border-[#E8B66D]/40 rounded text-[11px] text-[#E8B66D] space-y-1">
                <div className="font-bold uppercase tracking-wider">METHODOLOGY &amp; LIMITATIONS</div>
                <p>
                  This cross-module finding was evaluated via deterministic rule evaluation against persisted Phase 3 diagnostic outputs.
                  Model Doctor reports associative patterns and empirical relationships; these findings do not prove unobserved causality.
                </p>
              </div>
            </div>

            {/* Modal Footer */}
            <div className="p-3 border-t border-[#443849] flex justify-end bg-[#332B3C]">
              <button
                onClick={() => setSelectedFinding(null)}
                className="px-4 py-1.5 bg-[#2A2432] hover:bg-[#3D3448] text-[#F4EFF6] text-xs font-semibold rounded cursor-pointer border border-[#443849]"
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
