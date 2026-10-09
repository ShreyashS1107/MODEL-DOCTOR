"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  ModelReliabilityProfile,
  ReliabilityTrajectoryPoint,
  DiagnosticReliabilityEvent,
  FleetOverview,
  FleetRiskRank,
  FleetPattern,
  ModelComparison,
  ModelReliabilityState,
  ReliabilityTrend,
  GovernanceRecommendation,
  DecisionConfidence,
  ModelHealthState,
  NavSection,
} from "@/types/diagnostics";
import {
  getModelReliability,
  getModelReliabilityHistory,
  getModelReliabilityEvents,
  recalculateModelReliability,
  getFleetOverview,
  getFleetRisk,
  getFleetPatterns,
  compareModelReliability,
  RunSummary,
} from "@/lib/api";

export interface ModelReliabilityViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
}

type ViewMode = "DOSSIER" | "FLEET" | "COMPARE";

const STATE_STYLES: Record<ModelReliabilityState, { bg: string; text: string; border: string }> = {
  RELIABILITY_HEALTHY: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
  RELIABILITY_STABLE: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  RELIABILITY_DEGRADED: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  RELIABILITY_AT_RISK: { bg: "bg-[#f97316]/15", text: "text-[#fb923c]", border: "border-[#f97316]/40" },
  RELIABILITY_CRITICAL: { bg: "bg-[#ef4444]/20", text: "text-[#ef4444]", border: "border-[#ef4444]/50" },
  RELIABILITY_RECOVERING: { bg: "bg-[#06b6d4]/15", text: "text-[#22d3ee]", border: "border-[#06b6d4]/40" },
  RELIABILITY_UNKNOWN: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40" },
};

const TREND_STYLES: Record<ReliabilityTrend, { label: string; icon: string; color: string }> = {
  IMPROVING: { label: "IMPROVING", icon: "↑", color: "text-[#34d399]" },
  STABLE: { label: "STABLE", icon: "→", color: "text-[#60a5fa]" },
  DEGRADING: { label: "DEGRADING", icon: "↓", color: "text-[#ef4444]" },
  VOLATILE: { label: "VOLATILE", icon: "↕", color: "text-[#fb923c]" },
  INSUFFICIENT_DATA: { label: "INSUFFICIENT DATA", icon: "•", color: "text-[#94a3b8]" },
};

const RECOMMENDATION_STYLES: Record<GovernanceRecommendation, { bg: string; text: string; border: string }> = {
  NORMAL_OPERATION: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
  MONITOR: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  REVIEW_REQUIRED: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  PRIORITY_REVIEW: { bg: "bg-[#f97316]/15", text: "text-[#fb923c]", border: "border-[#f97316]/40" },
  ESCALATE: { bg: "bg-[#ef4444]/20", text: "text-[#ef4444]", border: "border-[#ef4444]/50" },
  INSUFFICIENT_EVIDENCE: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40" },
};

const GRADE_COLORS: Record<string, string> = {
  A: "text-[#34d399] border-[#10b981]/40 bg-[#10b981]/10",
  B: "text-[#60a5fa] border-[#3b82f6]/40 bg-[#3b82f6]/10",
  C: "text-[#fbbf24] border-[#f59e0b]/40 bg-[#f59e0b]/10",
  D: "text-[#fb923c] border-[#f97316]/40 bg-[#f97316]/10",
  F: "text-[#ef4444] border-[#ef4444]/40 bg-[#ef4444]/10",
  "N/A": "text-[#94a3b8] border-[#64748b]/40 bg-[#64748b]/10",
};

const RISK_TIER_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: { bg: "bg-[#ef4444]/20", text: "text-[#ef4444]", border: "border-[#ef4444]/50" },
  HIGH: { bg: "bg-[#f97316]/15", text: "text-[#fb923c]", border: "border-[#f97316]/40" },
  MEDIUM: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  LOW: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
};

export const ModelReliabilityView: React.FC<ModelReliabilityViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onRefresh,
  onNavigateSection,
}) => {
  const [activeMode, setActiveMode] = useState<ViewMode>("DOSSIER");
  const [profile, setProfile] = useState<ModelReliabilityProfile | null>(null);
  const [fleetOverview, setFleetOverview] = useState<FleetOverview | null>(null);
  const [comparison, setComparison] = useState<ModelComparison | null>(null);
  const [selectedLeftLineage, setSelectedLeftLineage] = useState<string>("");
  const [selectedRightLineage, setSelectedRightLineage] = useState<string>("");
  const [showExperimentOverlay, setShowExperimentOverlay] = useState<boolean>(false);

  const [loading, setLoading] = useState<boolean>(true);
  const [recalculating, setRecalculating] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const modelIdentifier = runId;

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [profData, fleetData] = await Promise.all([
        getModelReliability(modelIdentifier).catch(() => null),
        getFleetOverview().catch(() => null),
      ]);

      if (profData) {
        setProfile(profData);
        setSelectedLeftLineage(profData.modelLineageId);
      }
      if (fleetData) {
        setFleetOverview(fleetData);
        if (fleetData.rankedLineages && fleetData.rankedLineages.length > 1) {
          const secondLineage = fleetData.rankedLineages.find(
            (l) => l.modelLineageId !== (profData?.modelLineageId || modelIdentifier)
          );
          if (secondLineage) {
            setSelectedRightLineage(secondLineage.modelLineageId);
          }
        }
      }
    } catch (err: any) {
      setError(err.message || "Failed to load model reliability governance data.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [runId, modelIdentifier]);

  const handleRecalculate = async () => {
    setRecalculating(true);
    setError(null);
    try {
      await recalculateModelReliability(modelIdentifier);
      await loadData();
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setError(err.message || "Failed to recalculate reliability profile.");
    } finally {
      setRecalculating(false);
    }
  };

  const handleCompare = async () => {
    if (!selectedLeftLineage || !selectedRightLineage) return;
    try {
      const cmp = await compareModelReliability(selectedLeftLineage, selectedRightLineage);
      setComparison(cmp);
    } catch (err: any) {
      setError(err.message || "Failed to compare lineages.");
    }
  };

  useEffect(() => {
    if (activeMode === "COMPARE" && selectedLeftLineage && selectedRightLineage) {
      handleCompare();
    }
  }, [activeMode, selectedLeftLineage, selectedRightLineage]);

  if (loading && !profile && !fleetOverview) {
    return (
      <div className="flex-1 bg-[#0a0d14] text-[#e2e8f0] p-6 flex flex-col items-center justify-center space-y-4 font-mono">
        <div className="w-8 h-8 border-2 border-[#3b82f6] border-t-transparent rounded-full animate-spin"></div>
        <div className="text-xs text-[#94a3b8]">SYNTHESIZING MODEL RELIABILITY & FLEET INTELLIGENCE...</div>
      </div>
    );
  }

  const score = profile?.reliabilityScore ?? 0;
  const grade = profile?.grade ?? "N/A";
  const state = profile?.reliabilityState ?? "RELIABILITY_UNKNOWN";
  const trend = profile?.trend ?? "INSUFFICIENT_DATA";
  const trendInfo = TREND_STYLES[trend];
  const stateStyle = STATE_STYLES[state];
  const recStyle = profile?.governanceRecommendation
    ? RECOMMENDATION_STYLES[profile.governanceRecommendation]
    : RECOMMENDATION_STYLES.INSUFFICIENT_EVIDENCE;

  return (
    <div className="flex-1 bg-[#0a0d14] text-[#e2e8f0] flex flex-col min-h-0 select-none overflow-hidden font-mono">
      {/* TOP HEADER HUD */}
      <div className="bg-[#0f141f] border-b border-[#1f2533] px-6 py-3.5 flex flex-wrap items-center justify-between gap-4 shrink-0">
        <div className="flex items-center space-x-4">
          <div className="flex items-center space-x-2">
            <span className="text-[10px] bg-[#1e293b] text-[#94a3b8] px-2 py-0.5 rounded border border-[#334155] font-semibold">
              PHASE 12
            </span>
            <h1 className="text-sm font-bold text-white tracking-wider">
              MODEL RELIABILITY & FLEET INTELLIGENCE
            </h1>
          </div>
          <span className="text-xs text-[#64748b]">|</span>
          <div className="flex items-center space-x-2">
            <span className="text-xs text-[#94a3b8]">LINEAGE:</span>
            <span className="text-xs font-semibold text-white bg-[#1e293b]/70 px-2 py-0.5 rounded border border-[#334155]">
              {profile?.modelName || modelIdentifier}
            </span>
          </div>
        </div>

        {/* HUD METRICS & ACTION CONTROLS */}
        <div className="flex items-center space-x-3">
          {/* Reliability Score Badge */}
          <div className="flex items-center space-x-2 bg-[#161d2d] px-3 py-1 rounded border border-[#27354d]">
            <span className="text-[11px] text-[#94a3b8]">SCORE</span>
            <span className="text-sm font-bold text-white">{score}</span>
            <span className="text-[10px] text-[#64748b]">/100</span>
            <span className={`text-xs font-bold px-1.5 py-0.2 rounded border ${GRADE_COLORS[grade] || GRADE_COLORS["N/A"]}`}>
              {grade}
            </span>
          </div>

          {/* Reliability State */}
          <span className={`text-[11px] px-2.5 py-1 rounded font-semibold border ${stateStyle.bg} ${stateStyle.text} ${stateStyle.border}`}>
            {state.replace("RELIABILITY_", "")}
          </span>

          {/* Trend Indicator */}
          <div className="flex items-center space-x-1.5 bg-[#161d2d] px-2.5 py-1 rounded border border-[#27354d] text-[11px]">
            <span className={`font-bold ${trendInfo.color}`}>{trendInfo.icon}</span>
            <span className={trendInfo.color}>{trendInfo.label}</span>
            {profile?.trendSlope !== undefined && profile?.trendSlope !== null && (
              <span className="text-[10px] text-[#64748b] ml-1">
                β={profile.trendSlope.toFixed(3)}
              </span>
            )}
          </div>

          {/* Confidence */}
          <span className="text-[11px] text-[#94a3b8] bg-[#161d2d] px-2.5 py-1 rounded border border-[#27354d]">
            CONF: <strong className="text-white">{profile?.reliabilityConfidence || "MED"}</strong>
          </span>

          {/* Recalculate Button */}
          <button
            onClick={handleRecalculate}
            disabled={recalculating}
            className="px-3 py-1 text-xs font-semibold bg-[#2563eb] hover:bg-[#1d4ed8] text-white rounded transition-colors disabled:opacity-50 flex items-center space-x-1.5 cursor-pointer shadow-sm"
          >
            {recalculating ? (
              <>
                <span className="w-3 h-3 border-2 border-white border-t-transparent rounded-full animate-spin"></span>
                <span>RECALCULATING...</span>
              </>
            ) : (
              <>
                <span>⚡</span>
                <span>RECALCULATE</span>
              </>
            )}
          </button>
        </div>
      </div>

      {/* VIEW MODE NAVIGATION & CONTROLS */}
      <div className="bg-[#0c1018] border-b border-[#1f2533] px-6 py-2 flex items-center justify-between shrink-0">
        <div className="flex items-center space-x-1">
          <button
            onClick={() => setActiveMode("DOSSIER")}
            className={`px-3 py-1 text-xs font-semibold rounded transition-colors cursor-pointer ${
              activeMode === "DOSSIER"
                ? "bg-[#1e293b] text-white border border-[#3b82f6]"
                : "text-[#94a3b8] hover:text-white hover:bg-[#1e293b]/50"
            }`}
          >
            📊 MODEL RELIABILITY DOSSIER
          </button>
          <button
            onClick={() => setActiveMode("FLEET")}
            className={`px-3 py-1 text-xs font-semibold rounded transition-colors cursor-pointer ${
              activeMode === "FLEET"
                ? "bg-[#1e293b] text-white border border-[#3b82f6]"
                : "text-[#94a3b8] hover:text-white hover:bg-[#1e293b]/50"
            }`}
          >
            🌐 FLEET INTELLIGENCE & MATRIX
          </button>
          <button
            onClick={() => setActiveMode("COMPARE")}
            className={`px-3 py-1 text-xs font-semibold rounded transition-colors cursor-pointer ${
              activeMode === "COMPARE"
                ? "bg-[#1e293b] text-white border border-[#3b82f6]"
                : "text-[#94a3b8] hover:text-white hover:bg-[#1e293b]/50"
            }`}
          >
            ⚖️ CROSS-MODEL COMPARISON
          </button>
        </div>

        {/* Experiment Overlay Toggle */}
        <label className="flex items-center space-x-2 text-xs text-[#94a3b8] cursor-pointer">
          <input
            type="checkbox"
            checked={showExperimentOverlay}
            onChange={(e) => setShowExperimentOverlay(e.target.checked)}
            className="rounded border-[#334155] bg-[#1e293b] text-[#3b82f6] focus:ring-0 cursor-pointer"
          />
          <span>SHOW EXPERIMENT OVERLAY</span>
        </label>
      </div>

      {/* ERROR BANNER */}
      {error && (
        <div className="bg-[#ef4444]/15 border-b border-[#ef4444]/30 px-6 py-2 text-xs text-[#f87171] flex items-center justify-between">
          <span>⚠️ {error}</span>
          <button onClick={() => setError(null)} className="text-[#94a3b8] hover:text-white">✕</button>
        </div>
      )}

      {/* MAIN CONTENT AREA */}
      <div className="flex-1 overflow-y-auto p-6 space-y-6">
        {/* ========================================================================= */}
        {/* MODE 1: MODEL RELIABILITY DOSSIER */}
        {/* ========================================================================= */}
        {activeMode === "DOSSIER" && profile && (
          <div className="space-y-6">
            {/* GOVERNANCE RECOMMENDATION CONSOLE */}
            <div className={`p-4 rounded-lg border ${recStyle.bg} ${recStyle.border} flex flex-col md:flex-row items-start md:items-center justify-between gap-4`}>
              <div className="space-y-1">
                <div className="flex items-center space-x-2">
                  <span className="text-[10px] text-[#94a3b8] tracking-wider uppercase font-semibold">
                    GOVERNANCE DIRECTIVE
                  </span>
                  <span className={`text-xs px-2 py-0.5 rounded font-bold uppercase border ${recStyle.bg} ${recStyle.text} ${recStyle.border}`}>
                    {profile.governanceRecommendation.replace("_", " ")}
                  </span>
                  <span className="text-[10px] text-[#94a3b8]">
                    (CONFIDENCE: <strong>{profile.reliabilityConfidence}</strong>)
                  </span>
                </div>
                <p className="text-xs text-slate-200">
                  {profile.recommendationReason || "Operational reliability evaluated against baseline history."}
                </p>
              </div>

              <div className="flex items-center space-x-3 shrink-0">
                {onNavigateSection && profile.activeIncidentCount > 0 && (
                  <button
                    onClick={() => onNavigateSection("11_INCIDENTS")}
                    className="px-3 py-1.5 text-xs font-semibold bg-[#ef4444]/20 hover:bg-[#ef4444]/30 text-[#f87171] rounded border border-[#ef4444]/40 transition-colors cursor-pointer"
                  >
                    VIEW {profile.activeIncidentCount} ACTIVE INCIDENT(S) →
                  </button>
                )}
                {onNavigateSection && (
                  <button
                    onClick={() => onNavigateSection("10_MONITORING")}
                    className="px-3 py-1.5 text-xs font-semibold bg-[#1e293b] hover:bg-[#334155] text-slate-200 rounded border border-[#334155] transition-colors cursor-pointer"
                  >
                    CONTINUOUS HEALTH →
                  </button>
                )}
              </div>
            </div>

            {/* DUAL COLUMN: SCORE BREAKDOWN & RECOVERY / DURABILITY */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* SCORE BREAKDOWN TABLE */}
              <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                  <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                    RELIABILITY SCORE BREAKDOWN
                  </h2>
                  <div className="flex items-center space-x-2">
                    <span className="text-[11px] text-[#94a3b8]">NET SCORE:</span>
                    <span className="text-sm font-bold text-white">{profile.scoreBreakdown?.netScore ?? score} / 100</span>
                  </div>
                </div>

                <div className="space-y-1.5 text-xs">
                  <div className="flex justify-between py-1 px-2 bg-[#161d2d] rounded text-[#94a3b8]">
                    <span>BASE OPERATIONAL RELIABILITY</span>
                    <span className="font-bold text-white">+{profile.scoreBreakdown?.baseScore ?? 100}</span>
                  </div>

                  {profile.scoreBreakdown?.items && profile.scoreBreakdown.items.length > 0 ? (
                    profile.scoreBreakdown.items.map((item, idx) => (
                      <div
                        key={idx}
                        className={`flex items-center justify-between py-1.5 px-2 rounded border text-xs ${
                          item.points < 0
                            ? "bg-[#ef4444]/10 border-[#ef4444]/20 text-[#f87171]"
                            : "bg-[#10b981]/10 border-[#10b981]/20 text-[#34d399]"
                        }`}
                      >
                        <div className="space-y-0.5">
                          <div className="font-semibold">{item.description}</div>
                          {item.evidence && (
                            <div className="text-[10px] text-[#94a3b8]">{item.evidence}</div>
                          )}
                        </div>
                        <span className="font-bold text-sm shrink-0 ml-2">
                          {item.points > 0 ? `+${item.points}` : item.points}
                        </span>
                      </div>
                    ))
                  ) : (
                    <div className="text-center py-3 text-xs text-[#64748b]">
                      No active deductions or penalties recorded.
                    </div>
                  )}

                  <div className="flex justify-between py-1.5 px-2 bg-[#1e293b] rounded text-white font-bold border-t border-[#334155] mt-2">
                    <span>NET SYNTHESIZED RELIABILITY SCORE</span>
                    <span className="text-sm text-[#38bdf8]">{profile.scoreBreakdown?.netScore ?? score}</span>
                  </div>
                </div>
              </div>

              {/* RECOVERY PROFILE & REMEDIATION DURABILITY */}
              <div className="space-y-6">
                {/* RECOVERY PROFILE */}
                <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
                  <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                    <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                      LONGITUDINAL RECOVERY PROFILE
                    </h2>
                    <span className="text-xs font-bold text-[#34d399] bg-[#10b981]/15 px-2 py-0.5 rounded border border-[#10b981]/30">
                      RATE: {profile.recoveryProfile?.recoveryRate?.toFixed(1) ?? "100.0"}%
                    </span>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">DEGRADATIONS</div>
                      <div className="text-sm font-bold text-[#fbbf24]">{profile.recoveryProfile?.degradationEventsCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">RECOVERED</div>
                      <div className="text-sm font-bold text-[#34d399]">{profile.recoveryProfile?.recoveredEventsCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">UNRESOLVED</div>
                      <div className="text-sm font-bold text-[#f87171]">{profile.recoveryProfile?.unresolvedEventsCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">REGRESSIONS</div>
                      <div className="text-sm font-bold text-[#fb923c]">{profile.recoveryProfile?.regressionsAfterRecoveryCount ?? 0}</div>
                    </div>
                  </div>

                  <p className="text-[11px] text-[#94a3b8] italic">
                    {profile.recoveryProfile?.summary || "No operational degradation periods observed."}
                  </p>
                </div>

                {/* REMEDIATION DURABILITY */}
                <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
                  <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                    <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                      REMEDIATION DURABILITY HISTORY
                    </h2>
                    <span className="text-xs font-bold text-[#60a5fa] bg-[#3b82f6]/15 px-2 py-0.5 rounded border border-[#3b82f6]/30">
                      DURABILITY: {profile.remediationDurability?.durabilityRate?.toFixed(1) ?? "100.0"}%
                    </span>
                  </div>

                  <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 text-center">
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">PROPOSED</div>
                      <div className="text-sm font-bold text-white">{profile.remediationDurability?.proposedCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">SUSTAINED</div>
                      <div className="text-sm font-bold text-[#34d399]">{profile.remediationDurability?.sustainedCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">TEMPORARY</div>
                      <div className="text-sm font-bold text-[#fbbf24]">{profile.remediationDurability?.temporaryCount ?? 0}</div>
                    </div>
                    <div className="bg-[#161d2d] p-2 rounded border border-[#27354d]">
                      <div className="text-[10px] text-[#94a3b8]">FAILED</div>
                      <div className="text-sm font-bold text-[#f87171]">{profile.remediationDurability?.failedCount ?? 0}</div>
                    </div>
                  </div>

                  <p className="text-[11px] text-[#94a3b8] italic">
                    {profile.remediationDurability?.summary || "No remediation hypotheses evaluated."}
                  </p>
                </div>
              </div>
            </div>

            {/* LONGITUDINAL RELIABILITY TRAJECTORY */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                <div className="flex items-center space-x-3">
                  <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                    LONGITUDINAL RELIABILITY TRAJECTORY
                  </h2>
                  <span className="text-[10px] text-[#64748b]">
                    ({profile.trajectory?.length || 0} OPERATIONAL BASELINE RUNS)
                  </span>
                </div>
                {profile.trendSlope !== undefined && profile.trendSlope !== null && (
                  <div className="text-xs text-[#94a3b8] flex items-center space-x-3">
                    <span>TREND SLOPE: <strong className={trendInfo.color}>{profile.trendSlope.toFixed(3)}</strong></span>
                    <span>R²: <strong className="text-white">{profile.trendR2?.toFixed(2) ?? "1.00"}</strong></span>
                  </div>
                )}
              </div>

              {/* TRAJECTORY DATA TABLE */}
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                      <th className="py-2 px-3">RUN ID</th>
                      <th className="py-2 px-3">TYPE</th>
                      <th className="py-2 px-3">RELIABILITY SCORE</th>
                      <th className="py-2 px-3">RELIABILITY STATE</th>
                      <th className="py-2 px-3">HEALTH STATE</th>
                      <th className="py-2 px-3">ACTIVE INCIDENTS</th>
                      <th className="py-2 px-3">TIMESTAMP</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#161d2d]">
                    {profile.trajectory && profile.trajectory.length > 0 ? (
                      profile.trajectory.map((pt, idx) => {
                        const ptStateStyle = STATE_STYLES[pt.reliabilityState] || STATE_STYLES.RELIABILITY_UNKNOWN;
                        return (
                          <tr key={idx} className="hover:bg-[#161d2d]/50 transition-colors">
                            <td className="py-2 px-3 font-semibold text-white">{pt.runId}</td>
                            <td className="py-2 px-3">
                              <span className="text-[10px] px-1.5 py-0.5 rounded bg-[#1e293b] text-[#94a3b8] border border-[#334155]">
                                {pt.runType}
                              </span>
                            </td>
                            <td className="py-2 px-3 font-bold text-white">
                              <div className="flex items-center space-x-2">
                                <span>{pt.reliabilityScore}</span>
                                <div className="w-16 bg-[#1e293b] h-1.5 rounded-full overflow-hidden">
                                  <div
                                    className={`h-full ${
                                      pt.reliabilityScore >= 80
                                        ? "bg-[#10b981]"
                                        : pt.reliabilityScore >= 60
                                        ? "bg-[#f59e0b]"
                                        : "bg-[#ef4444]"
                                    }`}
                                    style={{ width: `${pt.reliabilityScore}%` }}
                                  ></div>
                                </div>
                              </div>
                            </td>
                            <td className="py-2 px-3">
                              <span className={`text-[10px] px-2 py-0.5 rounded font-semibold border ${ptStateStyle.bg} ${ptStateStyle.text} ${ptStateStyle.border}`}>
                                {pt.reliabilityState.replace("RELIABILITY_", "")}
                              </span>
                            </td>
                            <td className="py-2 px-3">
                              <span className="text-xs text-slate-300">{pt.healthState}</span>
                            </td>
                            <td className="py-2 px-3">
                              {pt.activeIncidentsCount > 0 ? (
                                <span className="text-[#f87171] font-semibold">{pt.activeIncidentsCount} active</span>
                              ) : (
                                <span className="text-[#64748b]">0</span>
                              )}
                            </td>
                            <td className="py-2 px-3 text-[#64748b] text-[10px]">
                              {new Date(pt.timestamp).toLocaleString()}
                            </td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan={7} className="py-4 text-center text-[#64748b]">
                          No trajectory points recorded.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* DUAL SECTION: RISK FACTORS & RELIABILITY STRENGTHS */}
            <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
              {/* RISK FACTORS */}
              <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                  <h2 className="text-xs font-bold text-[#f87171] tracking-wider uppercase flex items-center space-x-1.5">
                    <span>⚠️</span>
                    <span>CURRENT OPERATIONAL RISK FACTORS ({profile.riskFactors?.length || 0})</span>
                  </h2>
                </div>

                <div className="space-y-2">
                  {profile.riskFactors && profile.riskFactors.length > 0 ? (
                    profile.riskFactors.map((rf, idx) => (
                      <div key={idx} className="bg-[#161d2d] border border-[#27354d] p-3 rounded space-y-1 text-xs">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-white">{rf.title}</span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-[#ef4444]/15 text-[#f87171] border border-[#ef4444]/30">
                            {rf.severity}
                          </span>
                        </div>
                        <p className="text-[#94a3b8] text-[11px]">{rf.description}</p>
                        {rf.evidenceRef && (
                          <div className="text-[10px] text-[#64748b] flex items-center space-x-1">
                            <span>EVIDENCE SOURCE:</span>
                            <span className="text-[#38bdf8] font-semibold">{rf.evidenceRef}</span>
                          </div>
                        )}
                      </div>
                    ))
                  ) : (
                    <div className="text-center py-4 text-xs text-[#64748b]">
                      No elevated operational risk factors detected.
                    </div>
                  )}
                </div>
              </div>

              {/* RELIABILITY STRENGTHS */}
              <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
                <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                  <h2 className="text-xs font-bold text-[#34d399] tracking-wider uppercase flex items-center space-x-1.5">
                    <span>🛡️</span>
                    <span>OPERATIONAL STRENGTHS ({profile.strengths?.length || 0})</span>
                  </h2>
                </div>

                <div className="space-y-2">
                  {profile.strengths && profile.strengths.length > 0 ? (
                    profile.strengths.map((st, idx) => (
                      <div key={idx} className="bg-[#161d2d] border border-[#27354d] p-3 rounded space-y-1 text-xs">
                        <div className="flex items-center justify-between">
                          <span className="font-bold text-white">{st.title}</span>
                          <span className="text-[10px] px-1.5 py-0.5 rounded font-bold bg-[#10b981]/15 text-[#34d399] border border-[#10b981]/30">
                            {st.category}
                          </span>
                        </div>
                        <p className="text-[#94a3b8] text-[11px]">{st.description}</p>
                        {st.evidenceRef && (
                          <div className="text-[10px] text-[#64748b] flex items-center space-x-1">
                            <span>EVIDENCE REF:</span>
                            <span className="text-[#38bdf8] font-semibold">{st.evidenceRef}</span>
                          </div>
                        )}
                      </div>
                    ))
                  ) : (
                    <div className="text-center py-4 text-xs text-[#64748b]">
                      No positive evidence items logged yet.
                    </div>
                  )}
                </div>
              </div>
            </div>

            {/* RELIABILITY GOVERNANCE TIMELINE EVENTS */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                  HISTORICAL GOVERNANCE TIMELINE & AUDIT TRAIL
                </h2>
                <span className="text-[10px] text-[#64748b]">
                  {profile.recentEvents?.length || 0} GOVERNANCE EVENTS
                </span>
              </div>

              <div className="space-y-2">
                {profile.recentEvents && profile.recentEvents.length > 0 ? (
                  profile.recentEvents.map((ev, idx) => (
                    <div key={idx} className="flex items-start justify-between bg-[#161d2d] p-2.5 rounded border border-[#27354d] text-xs">
                      <div className="space-y-1">
                        <div className="flex items-center space-x-2">
                          <span className="text-[10px] font-bold px-1.5 py-0.2 rounded bg-[#1e293b] text-[#38bdf8] border border-[#334155]">
                            {ev.eventType}
                          </span>
                          <span className="text-[#e2e8f0] font-semibold">{ev.summary}</span>
                        </div>
                        <div className="text-[10px] text-[#64748b] flex items-center space-x-3">
                          <span>SOURCE: <strong className="text-slate-300">{ev.sourceType} ({ev.sourceId || "N/A"})</strong></span>
                          {ev.runId && <span>RUN: <strong className="text-slate-300">{ev.runId}</strong></span>}
                        </div>
                      </div>
                      <span className="text-[10px] text-[#64748b] shrink-0 ml-4">
                        {new Date(ev.timestamp).toLocaleString()}
                      </span>
                    </div>
                  ))
                ) : (
                  <div className="text-center py-4 text-xs text-[#64748b]">
                    No historical governance events recorded.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* MODE 2: FLEET INTELLIGENCE & 2D RISK MATRIX */}
        {/* ========================================================================= */}
        {activeMode === "FLEET" && fleetOverview && (
          <div className="space-y-6">
            {/* FLEET SUMMARY HUD */}
            <div className="grid grid-cols-2 sm:grid-cols-4 lg:grid-cols-7 gap-3">
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#94a3b8]">TOTAL FLEET</div>
                <div className="text-lg font-bold text-white">{fleetOverview.totalLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#34d399]">HEALTHY</div>
                <div className="text-lg font-bold text-[#34d399]">{fleetOverview.healthyLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#60a5fa]">STABLE</div>
                <div className="text-lg font-bold text-[#60a5fa]">{fleetOverview.stableLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#fbbf24]">DEGRADED</div>
                <div className="text-lg font-bold text-[#fbbf24]">{fleetOverview.degradedLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#fb923c]">AT RISK</div>
                <div className="text-lg font-bold text-[#fb923c]">{fleetOverview.atRiskLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#ef4444]">CRITICAL</div>
                <div className="text-lg font-bold text-[#ef4444]">{fleetOverview.criticalLineagesCount}</div>
              </div>
              <div className="bg-[#0f141f] border border-[#1f2533] p-3 rounded text-center">
                <div className="text-[10px] text-[#94a3b8]">AVG SCORE</div>
                <div className="text-lg font-bold text-[#38bdf8]">{fleetOverview.averageReliabilityScore}</div>
              </div>
            </div>

            {/* PRIORITIZED FLEET RISK RANKING TABLE */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                  PRIORITIZED FLEET RISK RANKING
                </h2>
                <span className="text-[10px] text-[#94a3b8]">
                  SORTED BY OPERATIONAL RISK PRIORITY
                </span>
              </div>

              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                      <th className="py-2 px-3">RANK</th>
                      <th className="py-2 px-3">MODEL LINEAGE</th>
                      <th className="py-2 px-3">SCORE</th>
                      <th className="py-2 px-3">STATE</th>
                      <th className="py-2 px-3">TREND</th>
                      <th className="py-2 px-3">INCIDENTS</th>
                      <th className="py-2 px-3">RISK TIER</th>
                      <th className="py-2 px-3">DIRECTIVE</th>
                      <th className="py-2 px-3">PRIMARY RISK REASONS</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#161d2d]">
                    {fleetOverview.rankedLineages && fleetOverview.rankedLineages.length > 0 ? (
                      fleetOverview.rankedLineages.map((rk, idx) => {
                        const rkStateStyle = STATE_STYLES[rk.reliabilityState] || STATE_STYLES.RELIABILITY_UNKNOWN;
                        const rkTrend = TREND_STYLES[rk.trend] || TREND_STYLES.INSUFFICIENT_DATA;
                        const tierStyle = RISK_TIER_STYLES[rk.riskTier] || RISK_TIER_STYLES.LOW;

                        return (
                          <tr
                            key={idx}
                            onClick={() => {
                              setSelectedLeftLineage(rk.modelLineageId);
                              getModelReliability(rk.modelLineageId).then((p) => {
                                setProfile(p);
                                setActiveMode("DOSSIER");
                              });
                            }}
                            className="hover:bg-[#161d2d] transition-colors cursor-pointer"
                          >
                            <td className="py-2.5 px-3 font-bold text-[#38bdf8]">
                              #{String(rk.rank).padStart(2, "0")}
                            </td>
                            <td className="py-2.5 px-3 font-semibold text-white">
                              {rk.modelName || rk.modelLineageId}
                            </td>
                            <td className="py-2.5 px-3">
                              <span className="font-bold text-white">{rk.reliabilityScore}</span>
                              <span className={`ml-1.5 text-[10px] px-1 py-0.2 rounded border ${GRADE_COLORS[rk.grade] || GRADE_COLORS["N/A"]}`}>
                                {rk.grade}
                              </span>
                            </td>
                            <td className="py-2.5 px-3">
                              <span className={`text-[10px] px-2 py-0.5 rounded font-semibold border ${rkStateStyle.bg} ${rkStateStyle.text} ${rkStateStyle.border}`}>
                                {rk.reliabilityState.replace("RELIABILITY_", "")}
                              </span>
                            </td>
                            <td className="py-2.5 px-3">
                              <span className={`font-semibold ${rkTrend.color}`}>
                                {rkTrend.icon} {rkTrend.label}
                              </span>
                            </td>
                            <td className="py-2.5 px-3">
                              {rk.criticalIncidentsCount > 0 ? (
                                <span className="text-[#ef4444] font-bold">{rk.criticalIncidentsCount} CRITICAL</span>
                              ) : rk.activeIncidentsCount > 0 ? (
                                <span className="text-[#f59e0b] font-semibold">{rk.activeIncidentsCount} active</span>
                              ) : (
                                <span className="text-[#64748b]">0</span>
                              )}
                            </td>
                            <td className="py-2.5 px-3">
                              <span className={`text-[10px] px-2 py-0.5 rounded font-bold border ${tierStyle.bg} ${tierStyle.text} ${tierStyle.border}`}>
                                {rk.riskTier}
                              </span>
                            </td>
                            <td className="py-2.5 px-3">
                              <span className="text-[10px] text-slate-300 font-semibold">
                                {rk.governanceRecommendation}
                              </span>
                            </td>
                            <td className="py-2.5 px-3 text-[#94a3b8] text-[11px]">
                              {rk.rankingReasons?.join(" • ") || "Normal operation"}
                            </td>
                          </tr>
                        );
                      })
                    ) : (
                      <tr>
                        <td colSpan={9} className="py-4 text-center text-[#64748b]">
                          No model lineages discovered across fleet.
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              </div>
            </div>

            {/* 2D FLEET RISK MATRIX (CURRENT HEALTH vs RELIABILITY TREND) */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                <h2 className="text-xs font-bold text-white tracking-wider uppercase">
                  2D FLEET RISK MATRIX: CURRENT HEALTH vs RELIABILITY TREND
                </h2>
                <span className="text-[10px] text-[#94a3b8]">
                  LONGITUDINAL RISK DISTRIBUTION
                </span>
              </div>

              <div className="grid grid-cols-4 gap-2 text-xs">
                {/* Header row */}
                <div className="p-2 font-bold text-[#64748b] text-[10px] text-center">HEALTH \ TREND</div>
                <div className="p-2 font-bold text-[#34d399] text-center bg-[#161d2d] rounded">IMPROVING ↑</div>
                <div className="p-2 font-bold text-[#60a5fa] text-center bg-[#161d2d] rounded">STABLE →</div>
                <div className="p-2 font-bold text-[#ef4444] text-center bg-[#161d2d] rounded">DEGRADING ↓</div>

                {/* HEALTHY ROW */}
                <div className="p-2 font-bold text-[#34d399] bg-[#161d2d] rounded flex items-center justify-center">
                  HEALTHY
                </div>
                {["IMPROVING", "STABLE", "DEGRADING"].map((trendKey) => {
                  const cell = fleetOverview.riskMatrix?.find(
                    (c) => c.currentHealth === "HEALTHY" && c.trend === trendKey
                  );
                  return (
                    <div key={trendKey} className="bg-[#121824] p-2.5 rounded border border-[#1f2533] space-y-1 min-h-[60px]">
                      <div className="flex justify-between text-[10px]">
                        <span className="text-[#64748b]">COUNT: {cell?.modelLineageIds?.length || 0}</span>
                        <span className="font-bold text-[#34d399]">{cell?.riskLevel}</span>
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {cell?.modelLineageIds?.map((lId) => (
                          <span key={lId} className="text-[10px] bg-[#1e293b] px-1.5 py-0.5 rounded text-white border border-[#334155]">
                            {lId}
                          </span>
                        ))}
                      </div>
                    </div>
                  );
                })}

                {/* DEGRADED ROW */}
                <div className="p-2 font-bold text-[#fbbf24] bg-[#161d2d] rounded flex items-center justify-center">
                  DEGRADED
                </div>
                {["IMPROVING", "STABLE", "DEGRADING"].map((trendKey) => {
                  const cell = fleetOverview.riskMatrix?.find(
                    (c) => c.currentHealth === "DEGRADED" && c.trend === trendKey
                  );
                  return (
                    <div key={trendKey} className="bg-[#121824] p-2.5 rounded border border-[#1f2533] space-y-1 min-h-[60px]">
                      <div className="flex justify-between text-[10px]">
                        <span className="text-[#64748b]">COUNT: {cell?.modelLineageIds?.length || 0}</span>
                        <span className="font-bold text-[#fbbf24]">{cell?.riskLevel}</span>
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {cell?.modelLineageIds?.map((lId) => (
                          <span key={lId} className="text-[10px] bg-[#1e293b] px-1.5 py-0.5 rounded text-white border border-[#334155]">
                            {lId}
                          </span>
                        ))}
                      </div>
                    </div>
                  );
                })}

                {/* CRITICAL ROW */}
                <div className="p-2 font-bold text-[#ef4444] bg-[#161d2d] rounded flex items-center justify-center">
                  CRITICAL
                </div>
                {["IMPROVING", "STABLE", "DEGRADING"].map((trendKey) => {
                  const cell = fleetOverview.riskMatrix?.find(
                    (c) => c.currentHealth === "CRITICAL" && c.trend === trendKey
                  );
                  return (
                    <div key={trendKey} className="bg-[#121824] p-2.5 rounded border border-[#1f2533] space-y-1 min-h-[60px]">
                      <div className="flex justify-between text-[10px]">
                        <span className="text-[#64748b]">COUNT: {cell?.modelLineageIds?.length || 0}</span>
                        <span className="font-bold text-[#ef4444]">{cell?.riskLevel}</span>
                      </div>
                      <div className="flex flex-wrap gap-1">
                        {cell?.modelLineageIds?.map((lId) => (
                          <span key={lId} className="text-[10px] bg-[#ef4444]/20 text-[#f87171] px-1.5 py-0.5 rounded border border-[#ef4444]/40 font-bold">
                            {lId}
                          </span>
                        ))}
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>

            {/* DISCOVERED CROSS-MODEL RECURRING PATTERNS */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-3">
              <div className="flex items-center justify-between border-b border-[#1f2533] pb-2">
                <h2 className="text-xs font-bold text-white tracking-wider uppercase flex items-center space-x-2">
                  <span>🔄</span>
                  <span>DISCOVERED CROSS-MODEL RECURRING RISK PATTERNS ({fleetOverview.recurringPatterns?.length || 0})</span>
                </h2>
                <span className="text-[10px] text-[#94a3b8]">
                  NON-CAUSAL EMPIRICAL CORRELATIONS
                </span>
              </div>

              <div className="space-y-3">
                {fleetOverview.recurringPatterns && fleetOverview.recurringPatterns.length > 0 ? (
                  fleetOverview.recurringPatterns.map((pat, idx) => (
                    <div key={idx} className="bg-[#161d2d] border border-[#27354d] p-3 rounded space-y-2 text-xs">
                      <div className="flex items-center justify-between">
                        <div className="flex items-center space-x-2">
                          <span className="text-[10px] font-bold px-2 py-0.5 rounded bg-[#f59e0b]/15 text-[#fbbf24] border border-[#f59e0b]/30">
                            {pat.patternType}
                          </span>
                          <span className="font-bold text-white text-sm">{pat.patternTitle}</span>
                        </div>
                        <span className="text-[10px] text-[#94a3b8] bg-[#1e293b] px-2 py-0.5 rounded border border-[#334155]">
                          {pat.affectedLineagesCount} LINEAGES AFFECTED ({pat.totalIncidentsCount} INCIDENTS)
                        </span>
                      </div>

                      <p className="text-slate-300 text-xs">{pat.description}</p>

                      <div className="flex items-center space-x-2">
                        <span className="text-[10px] text-[#64748b]">AFFECTED LINEAGES:</span>
                        <div className="flex flex-wrap gap-1">
                          {pat.affectedLineages?.map((lin) => (
                            <span key={lin} className="text-[10px] bg-[#1e293b] text-white px-2 py-0.5 rounded border border-[#334155]">
                              {lin}
                            </span>
                          ))}
                        </div>
                      </div>

                      {/* Prominent Non-Causal Disclaimer */}
                      <div className="text-[10px] text-[#94a3b8] bg-[#0c1018] p-2 rounded border border-[#1f2533] italic">
                        ℹ️ {pat.nonCausalDisclaimer || "A recurring operational pattern exists across multiple model lineages. Causal relationship has not been established."}
                      </div>
                    </div>
                  ))
                ) : (
                  <div className="text-center py-4 text-xs text-[#64748b]">
                    No cross-model recurring patterns detected across current fleet history.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        {/* ========================================================================= */}
        {/* MODE 3: CROSS-MODEL LINEAGE COMPARISON */}
        {/* ========================================================================= */}
        {activeMode === "COMPARE" && (
          <div className="space-y-6">
            {/* COMPARISON SELECTOR BAR */}
            <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 flex flex-wrap items-center justify-between gap-4">
              <div className="flex items-center space-x-4">
                <div className="space-y-1">
                  <label className="text-[10px] text-[#94a3b8] uppercase font-semibold">MODEL A (LEFT)</label>
                  <select
                    value={selectedLeftLineage}
                    onChange={(e) => setSelectedLeftLineage(e.target.value)}
                    className="bg-[#161d2d] border border-[#27354d] text-white text-xs px-3 py-1.5 rounded focus:outline-none focus:border-[#3b82f6] cursor-pointer"
                  >
                    {fleetOverview?.rankedLineages?.map((rk) => (
                      <option key={rk.modelLineageId} value={rk.modelLineageId}>
                        {rk.modelName || rk.modelLineageId} (Score: {rk.reliabilityScore})
                      </option>
                    ))}
                  </select>
                </div>

                <span className="text-lg font-bold text-[#64748b] mt-4">VS</span>

                <div className="space-y-1">
                  <label className="text-[10px] text-[#94a3b8] uppercase font-semibold">MODEL B (RIGHT)</label>
                  <select
                    value={selectedRightLineage}
                    onChange={(e) => setSelectedRightLineage(e.target.value)}
                    className="bg-[#161d2d] border border-[#27354d] text-white text-xs px-3 py-1.5 rounded focus:outline-none focus:border-[#3b82f6] cursor-pointer"
                  >
                    {fleetOverview?.rankedLineages?.map((rk) => (
                      <option key={rk.modelLineageId} value={rk.modelLineageId}>
                        {rk.modelName || rk.modelLineageId} (Score: {rk.reliabilityScore})
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              <button
                onClick={handleCompare}
                className="px-4 py-2 bg-[#2563eb] hover:bg-[#1d4ed8] text-white text-xs font-semibold rounded transition-colors cursor-pointer"
              >
                COMPARE LINEAGES
              </button>
            </div>

            {/* COMPARISON RESULT MATRIX */}
            {comparison ? (
              <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-4 space-y-4">
                {/* SUMMARY DIRECTIVE */}
                <div className="bg-[#161d2d] border border-[#27354d] p-3 rounded text-xs space-y-1">
                  <div className="text-[10px] text-[#94a3b8] font-semibold tracking-wider uppercase">
                    GOVERNANCE COMPARISON SUMMARY
                  </div>
                  <p className="text-white font-medium">{comparison.governanceComparisonSummary}</p>
                </div>

                {/* SIDE BY SIDE TABLE */}
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-[#1f2533] text-[#64748b] text-[10px] uppercase">
                        <th className="py-2 px-3 w-1/3">DIMENSION</th>
                        <th className="py-2 px-3 w-1/3 text-white font-bold">{comparison.left?.modelName || comparison.left?.modelLineageId}</th>
                        <th className="py-2 px-3 w-1/3 text-white font-bold">{comparison.right?.modelName || comparison.right?.modelLineageId}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#161d2d]">
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">RELIABILITY SCORE</td>
                        <td className="py-2.5 px-3 font-bold text-lg text-white">{comparison.left?.reliabilityScore} / 100</td>
                        <td className="py-2.5 px-3 font-bold text-lg text-white">{comparison.right?.reliabilityScore} / 100</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">RELIABILITY GRADE</td>
                        <td className="py-2.5 px-3 font-bold">{comparison.left?.grade}</td>
                        <td className="py-2.5 px-3 font-bold">{comparison.right?.grade}</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">RELIABILITY STATE</td>
                        <td className="py-2.5 px-3 font-semibold text-slate-200">{comparison.left?.reliabilityState?.replace("RELIABILITY_", "")}</td>
                        <td className="py-2.5 px-3 font-semibold text-slate-200">{comparison.right?.reliabilityState?.replace("RELIABILITY_", "")}</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">TRAJECTORY TREND</td>
                        <td className="py-2.5 px-3 font-semibold text-slate-200">{comparison.left?.trend}</td>
                        <td className="py-2.5 px-3 font-semibold text-slate-200">{comparison.right?.trend}</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">ACTIVE INCIDENTS</td>
                        <td className="py-2.5 px-3 font-bold text-[#f87171]">{comparison.left?.activeIncidentCount}</td>
                        <td className="py-2.5 px-3 font-bold text-[#f87171]">{comparison.right?.activeIncidentCount}</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">CRITICAL INCIDENTS</td>
                        <td className="py-2.5 px-3 font-bold text-[#ef4444]">{comparison.left?.criticalIncidentCount}</td>
                        <td className="py-2.5 px-3 font-bold text-[#ef4444]">{comparison.right?.criticalIncidentCount}</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">RECOVERY RATE</td>
                        <td className="py-2.5 px-3 text-[#34d399] font-bold">{comparison.left?.recoveryProfile?.recoveryRate?.toFixed(1) ?? "100.0"}%</td>
                        <td className="py-2.5 px-3 text-[#34d399] font-bold">{comparison.right?.recoveryProfile?.recoveryRate?.toFixed(1) ?? "100.0"}%</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">REMEDIATION DURABILITY</td>
                        <td className="py-2.5 px-3 text-[#60a5fa] font-bold">{comparison.left?.remediationDurability?.durabilityRate?.toFixed(1) ?? "100.0"}%</td>
                        <td className="py-2.5 px-3 text-[#60a5fa] font-bold">{comparison.right?.remediationDurability?.durabilityRate?.toFixed(1) ?? "100.0"}%</td>
                      </tr>
                      <tr>
                        <td className="py-2.5 px-3 text-[#94a3b8]">GOVERNANCE DIRECTIVE</td>
                        <td className="py-2.5 px-3 font-semibold text-white">{comparison.left?.governanceRecommendation}</td>
                        <td className="py-2.5 px-3 font-semibold text-white">{comparison.right?.governanceRecommendation}</td>
                      </tr>
                    </tbody>
                  </table>
                </div>

                {/* KEY DIFFERENCES LIST */}
                <div className="space-y-1.5 pt-2 border-t border-[#1f2533]">
                  <div className="text-[10px] text-[#94a3b8] font-semibold uppercase">EXPLICIT GOVERNANCE DELTAS</div>
                  <ul className="space-y-1 text-xs text-slate-300 list-disc list-inside">
                    {comparison.keyDifferences?.map((diff, idx) => (
                      <li key={idx}>{diff}</li>
                    ))}
                  </ul>
                </div>
              </div>
            ) : (
              <div className="bg-[#0f141f] border border-[#1f2533] rounded-lg p-8 text-center text-xs text-[#64748b]">
                Select two model lineages and click Compare Lineages.
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
