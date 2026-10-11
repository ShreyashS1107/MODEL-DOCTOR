"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  DiagnosticRemediation,
  DiagnosticComparison,
  MetricComparisonItem,
  NavSection,
} from "@/types/diagnostics";
import {
  getDiagnosticRemediations,
  getDiagnosticRemediationById,
  recalculateDiagnosticRemediations,
  selectDiagnosticRemediation,
  rejectDiagnosticRemediation,
  compareDiagnosticRuns,
  getDiagnosticRuns,
  DiagnosticRunRecordResponse,
  RunSummary,
} from "@/lib/api";

export interface RemediationViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
}

const MODULE_COLORS: Record<string, { bg: string; text: string; border: string }> = {
  DRIFT: { bg: "bg-[#F18B78]/15", text: "text-[#F18B78]", border: "border-[#F18B78]/30" },
  EXPLAINABILITY: { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]" },
  ERROR_FORENSICS: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  ROBUSTNESS: { bg: "bg-[#E8B66D]/20", text: "text-[#E8B66D]", border: "border-[#E8B66D]/40" },
  DATA_QUALITY: { bg: "bg-[#E8B66D]/15", text: "text-[#E8B66D]", border: "border-[#E8B66D]/30" },
  LEAKAGE: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  BIAS: { bg: "bg-[#B7D7A8]/15", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/30" },
  PERFORMANCE: { bg: "bg-[#332B3C]", text: "text-[#F4EFF6]", border: "border-[#443849]" },
};

const PRIORITY_BADGES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/50" },
  HIGH: { bg: "bg-[#E8B66D]/20", text: "text-[#E8B66D]", border: "border-[#E8B66D]/50" },
  MEDIUM: { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]" },
  LOW: { bg: "bg-[#2A2432]", text: "text-[#8E8295]", border: "border-[#443849]" },
  INFO: { bg: "bg-[#2A2432]", text: "text-[#8E8295]", border: "border-[#443849]" },
};

const STATUS_BADGES: Record<string, { bg: string; text: string; border: string }> = {
  PROPOSED: { bg: "bg-[#332B3C]", text: "text-[#B7ACBD]", border: "border-[#443849]" },
  SELECTED: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/50" },
  VALIDATING: { bg: "bg-[#E8B66D]/20", text: "text-[#E8B66D]", border: "border-[#E8B66D]/50" },
  VALIDATED: { bg: "bg-[#B7D7A8]/20", text: "text-[#B7D7A8]", border: "border-[#B7D7A8]/50" },
  REJECTED: { bg: "bg-[#F18B78]/20", text: "text-[#F18B78]", border: "border-[#F18B78]/40" },
  SUPERSEDED: { bg: "bg-[#1C1824]", text: "text-[#8E8295]", border: "border-[#443849]" },
};

export const RemediationView: React.FC<RemediationViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
}) => {
  const [remediations, setRemediations] = useState<DiagnosticRemediation[]>([]);
  const [selectedRemediationId, setSelectedRemediationId] = useState<number | null>(null);
  const [activeTab, setActiveTab] = useState<"QUEUE" | "COMPARISON">("QUEUE");
  const [statusFilter, setStatusFilter] = useState<string>("ALL");
  const [priorityFilter, setPriorityFilter] = useState<string>("ALL");
  const [typeFilter, setTypeFilter] = useState<string>("ALL");
  const [searchQuery, setSearchQuery] = useState<string>("");
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [isActionLoading, setIsActionLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState<string>("");
  const [showRejectModal, setShowRejectModal] = useState<boolean>(false);

  // Comparison State
  const [recentRuns, setRecentRuns] = useState<DiagnosticRunRecordResponse[]>([]);
  const [candidateRunId, setCandidateRunId] = useState<string>("");
  const [comparison, setComparison] = useState<DiagnosticComparison | null>(null);
  const [isComparing, setIsComparing] = useState<boolean>(false);
  const [comparisonError, setComparisonError] = useState<string | null>(null);
  const [comparisonModuleFilter, setComparisonModuleFilter] = useState<string>("ALL");

  const fetchData = async () => {
    if (!runId) return;
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const [rems, runs] = await Promise.all([
        getDiagnosticRemediations(runId).catch(() => []),
        getDiagnosticRuns().catch(() => []),
      ]);
      setRemediations(rems || []);
      setRecentRuns(runs || []);
      if (rems && rems.length > 0 && !selectedRemediationId) {
        setSelectedRemediationId(rems[0].id);
      }
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to load remediation data");
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, [runId]);

  const handleRecalculate = async () => {
    if (!runId) return;
    setIsRecalculating(true);
    setErrorMessage(null);
    try {
      const updated = await recalculateDiagnosticRemediations(runId);
      setRemediations(updated || []);
      if (updated && updated.length > 0) {
        setSelectedRemediationId(updated[0].id);
      }
      if (onRefresh) onRefresh();
    } catch (err: any) {
      setErrorMessage(err.message || "Failed to recalculate remediations");
    } finally {
      setIsRecalculating(false);
    }
  };

  const handleSelectRemediation = async (id: number) => {
    setIsActionLoading(true);
    try {
      const updated = await selectDiagnosticRemediation(runId, id);
      setRemediations((prev) => prev.map((r) => (r.id === id ? updated : r)));
    } catch (err: any) {
      alert("Failed to select remediation: " + err.message);
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleRejectRemediation = async (id: number) => {
    setIsActionLoading(true);
    try {
      const updated = await rejectDiagnosticRemediation(runId, id, rejectReason);
      setRemediations((prev) => prev.map((r) => (r.id === id ? updated : r)));
      setShowRejectModal(false);
      setRejectReason("");
    } catch (err: any) {
      alert("Failed to reject remediation: " + err.message);
    } finally {
      setIsActionLoading(false);
    }
  };

  const handleRunComparison = async (candId: string) => {
    if (!candId) return;
    setIsComparing(true);
    setComparisonError(null);
    try {
      const res = await compareDiagnosticRuns(runId, candId);
      setComparison(res);
    } catch (err: any) {
      setComparisonError(err.message || "Failed to compare diagnostic runs");
    } finally {
      setIsComparing(false);
    }
  };

  const selectedRemediation = useMemo(() => {
    return remediations.find((r) => r.id === selectedRemediationId) || remediations[0] || null;
  }, [remediations, selectedRemediationId]);

  const filteredRemediations = useMemo(() => {
    return remediations.filter((r) => {
      if (statusFilter !== "ALL" && r.status !== statusFilter) return false;
      if (priorityFilter !== "ALL" && r.priority !== priorityFilter) return false;
      if (typeFilter !== "ALL" && r.remediationType !== typeFilter) return false;
      if (searchQuery.trim()) {
        const q = searchQuery.toLowerCase();
        const matchTitle = r.title.toLowerCase().includes(q);
        const matchTarget = r.targetKey.toLowerCase().includes(q);
        const matchType = r.remediationType.toLowerCase().includes(q);
        const matchHyp = r.hypothesis.toLowerCase().includes(q);
        if (!matchTitle && !matchTarget && !matchType && !matchHyp) return false;
      }
      return true;
    });
  }, [remediations, statusFilter, priorityFilter, typeFilter, searchQuery]);

  const counts = useMemo(() => {
    return {
      total: remediations.length,
      critical: remediations.filter((r) => r.priority === "CRITICAL").length,
      high: remediations.filter((r) => r.priority === "HIGH").length,
      selected: remediations.filter((r) => r.status === "SELECTED").length,
      validated: remediations.filter((r) => r.status === "VALIDATED").length,
    };
  }, [remediations]);

  const uniqueTypes = useMemo(() => {
    return Array.from(new Set(remediations.map((r) => r.remediationType))).sort();
  }, [remediations]);

  return (
    <div className="flex flex-col h-full bg-[#211D29] text-[#F4EFF6] font-mono text-xs select-none">
      {/* 1. TOP ENGINEERING HUD HEADER */}
      <div className="border-b border-[#443849] bg-[#2A2432] px-4 py-3 shrink-0">
        <div className="flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center space-x-3">
            <div className="flex items-center space-x-2">
              <span className="w-2.5 h-2.5 rounded-full bg-[#B7D7A8] animate-pulse" />
              <h1 className="text-sm font-bold tracking-wider text-[#F4EFF6] uppercase">
                07 REMEDIATION DECISION SUPPORT & VALIDATION
              </h1>
            </div>
            <span className="text-[#5A4B62] text-[11px]">|</span>
            <span className="text-[#B7ACBD] text-[11px]">RUN: {runId}</span>
          </div>

          {/* Metric Stats Counters */}
          <div className="flex items-center space-x-3 text-[11px]">
            <div className="px-2.5 py-1 rounded bg-[#332B3C] border border-[#443849]">
              <span className="text-[#8E8295]">TOTAL: </span>
              <span className="font-bold text-[#F4EFF6]">{counts.total}</span>
            </div>
            <div className="px-2.5 py-1 rounded bg-[#F18B78]/15 border border-[#F18B78]/40 text-[#F18B78]">
              <span>CRITICAL: </span>
              <span className="font-bold">{counts.critical}</span>
            </div>
            <div className="px-2.5 py-1 rounded bg-[#E8B66D]/15 border border-[#E8B66D]/40 text-[#E8B66D]">
              <span>HIGH: </span>
              <span className="font-bold">{counts.high}</span>
            </div>
            <div className="px-2.5 py-1 rounded bg-[#332B3C] border border-[#F18B78]/40 text-[#F18B78]">
              <span>SELECTED: </span>
              <span className="font-bold">{counts.selected}</span>
            </div>
            <div className="px-2.5 py-1 rounded bg-[#B7D7A8]/15 border border-[#B7D7A8]/40 text-[#B7D7A8]">
              <span>VALIDATED: </span>
              <span className="font-bold">{counts.validated}</span>
            </div>

            <button
              onClick={handleRecalculate}
              disabled={isRecalculating}
              className="px-3 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded font-bold transition disabled:opacity-50 flex items-center space-x-1 cursor-pointer"
            >
              {isRecalculating ? <span>RECALCULATING...</span> : <span>RECALCULATE</span>}
            </button>
          </div>
        </div>

        {/* 2. NON-CAUSALITY & SAFETY DIRECTIVE BANNER */}
        <div className="mt-2.5 px-3 py-1.5 bg-[#E8B66D]/10 border border-[#E8B66D]/30 rounded flex items-center justify-between text-[11px] text-[#E8B66D]">
          <div className="flex items-center space-x-2">
            <span className="text-[#E8B66D] font-bold">SAFETY DIRECTIVE:</span>
            <span>
              Remediation recommendations are evidence-driven hypotheses. The system does not establish causality or guarantee performance improvements. Models are not automatically modified.
            </span>
          </div>
          <span className="text-[10px] text-[#E8B66D]/80 uppercase font-semibold">NON-AUTONOMOUS SYSTEM</span>
        </div>

        {/* View Switcher Tabs */}
        <div className="flex items-center space-x-2 mt-3 pt-2 border-t border-[#443849]">
          <button
            onClick={() => setActiveTab("QUEUE")}
            className={`px-3 py-1 rounded text-[11px] font-semibold transition cursor-pointer ${
              activeTab === "QUEUE"
                ? "bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/50 shadow-sm"
                : "bg-[#2A2432] text-[#B7ACBD] hover:text-[#F4EFF6] border border-transparent"
            }`}
          >
            01 REMEDIATION QUEUE & DOSSIER
          </button>
          <button
            onClick={() => setActiveTab("COMPARISON")}
            className={`px-3 py-1 rounded text-[11px] font-semibold transition cursor-pointer ${
              activeTab === "COMPARISON"
                ? "bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/50 shadow-sm"
                : "bg-[#2A2432] text-[#B7ACBD] hover:text-[#F4EFF6] border border-transparent"
            }`}
          >
            02 BEFORE / AFTER RUN COMPARISON
          </button>
        </div>
      </div>

      {/* 3. MAIN WORKSPACE VIEW */}
      {activeTab === "QUEUE" ? (
        <div className="flex-1 flex overflow-hidden">
          {/* LEFT: REMEDIATION QUEUE */}
          <div className="w-1/3 border-r border-[#443849] flex flex-col bg-[#2A2432]/60">
            {/* Filters */}
            <div className="p-3 border-b border-[#443849] bg-[#2A2432] space-y-2">
              <input
                type="text"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Filter by target, title, or rule..."
                className="w-full px-2.5 py-1 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] placeholder-[#8E8295] focus:outline-none focus:border-[#F18B78] text-xs"
              />

              <div className="grid grid-cols-3 gap-1.5 text-[10px]">
                <select
                  value={statusFilter}
                  onChange={(e) => setStatusFilter(e.target.value)}
                  className="bg-[#1C1824] border border-[#443849] rounded px-1.5 py-0.5 text-[#B7ACBD]"
                >
                  <option value="ALL">Status: ALL</option>
                  <option value="PROPOSED">PROPOSED</option>
                  <option value="SELECTED">SELECTED</option>
                  <option value="VALIDATED">VALIDATED</option>
                  <option value="REJECTED">REJECTED</option>
                </select>

                <select
                  value={priorityFilter}
                  onChange={(e) => setPriorityFilter(e.target.value)}
                  className="bg-[#1C1824] border border-[#443849] rounded px-1.5 py-0.5 text-[#B7ACBD]"
                >
                  <option value="ALL">Priority: ALL</option>
                  <option value="CRITICAL">CRITICAL</option>
                  <option value="HIGH">HIGH</option>
                  <option value="MEDIUM">MEDIUM</option>
                  <option value="LOW">LOW</option>
                </select>

                <select
                  value={typeFilter}
                  onChange={(e) => setTypeFilter(e.target.value)}
                  className="bg-[#1C1824] border border-[#443849] rounded px-1.5 py-0.5 text-[#B7ACBD]"
                >
                  <option value="ALL">Type: ALL</option>
                  {uniqueTypes.map((t) => (
                    <option key={t} value={t}>
                      {t.replace(/_/g, " ")}
                    </option>
                  ))}
                </select>
              </div>
            </div>

            {/* Candidate List */}
            <div className="flex-1 overflow-y-auto divide-y divide-[#443849]/60 p-2 space-y-1.5">
              {isLoading ? (
                <div className="p-8 text-center text-[#8E8295]">Loading remediation queue...</div>
              ) : filteredRemediations.length === 0 ? (
                <div className="p-8 text-center text-[#8E8295]">No remediation candidates match filters.</div>
              ) : (
                filteredRemediations.map((rem) => {
                  const isSelected = selectedRemediation?.id === rem.id;
                  const pBadge = PRIORITY_BADGES[rem.priority] || PRIORITY_BADGES.INFO;
                  const sBadge = STATUS_BADGES[rem.status] || STATUS_BADGES.PROPOSED;

                  return (
                    <div
                      key={rem.id}
                      onClick={() => setSelectedRemediationId(rem.id)}
                      className={`p-3 rounded border cursor-pointer transition ${
                        isSelected
                          ? "bg-[#332B3C] border-[#F18B78] shadow-md ring-1 ring-[#F18B78]/40"
                          : "bg-[#2A2432] border-[#443849] hover:bg-[#332B3C]/50"
                      }`}
                    >
                      <div className="flex items-center justify-between gap-2 mb-1.5">
                        <div className="flex items-center space-x-1.5">
                          <span
                            className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase ${pBadge.bg} ${pBadge.text} border ${pBadge.border}`}
                          >
                            {rem.priority} ({rem.priorityScore.toFixed(0)})
                          </span>
                          <span
                            className={`px-1.5 py-0.5 rounded text-[9px] font-bold uppercase ${sBadge.bg} ${sBadge.text} border ${sBadge.border}`}
                          >
                            {rem.status}
                          </span>
                        </div>
                        <span className="text-[10px] text-[#8E8295] font-semibold truncate max-w-[120px]">
                          {rem.targetKey}
                        </span>
                      </div>

                      <h3 className="font-bold text-[#F4EFF6] text-xs line-clamp-1 mb-1">{rem.title}</h3>
                      <p className="text-[#B7ACBD] text-[11px] line-clamp-2 mb-2">{rem.description}</p>

                      <div className="flex items-center justify-between text-[10px]">
                        <span className="text-[#8E8295]">{rem.remediationType.replace(/_/g, " ")}</span>
                        <div className="flex items-center space-x-1">
                          {rem.requiredModules.slice(0, 3).map((m) => {
                            const modColor = MODULE_COLORS[m] || { bg: "bg-[#332B3C]", text: "#B7ACBD", border: "border-[#443849]" };
                            return (
                              <span
                                key={m}
                                className={`px-1 py-0.5 rounded text-[8px] font-semibold ${modColor.bg} ${modColor.text} border ${modColor.border}`}
                              >
                                {m}
                              </span>
                            );
                          })}
                        </div>
                      </div>
                    </div>
                  );
                })
              )}
            </div>
          </div>

          {/* RIGHT: REMEDIATION DOSSIER */}
          <div className="w-2/3 flex flex-col bg-[#211D29] overflow-y-auto">
            {selectedRemediation ? (
              <div className="p-5 space-y-6 max-w-4xl">
                {/* Header Profile */}
                <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-3">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <div className="flex items-center space-x-2 mb-1">
                        <span className="px-2 py-0.5 rounded bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/40 text-[10px] font-bold">
                          {selectedRemediation.targetType}: {selectedRemediation.targetKey}
                        </span>
                        <span className="px-2 py-0.5 rounded bg-[#332B3C] text-[#B7ACBD] border border-[#443849] text-[10px]">
                          RULE: {selectedRemediation.remediationType}
                        </span>
                      </div>
                      <h2 className="text-base font-bold text-[#F4EFF6]">{selectedRemediation.title}</h2>
                      <p className="text-[#B7ACBD] text-xs mt-1">{selectedRemediation.description}</p>
                    </div>

                    {/* Action Controls */}
                    <div className="flex flex-col items-end space-y-2 shrink-0">
                      <div className="flex items-center space-x-2">
                        {selectedRemediation.status === "PROPOSED" && (
                          <>
                            <button
                              onClick={() => handleSelectRemediation(selectedRemediation.id)}
                              disabled={isActionLoading}
                              className="px-3 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded font-bold text-xs cursor-pointer transition-colors"
                            >
                              SELECT FOR ACTION
                            </button>
                            <button
                              onClick={() => setShowRejectModal(true)}
                              disabled={isActionLoading}
                              className="px-3 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#F18B78] border border-[#F18B78]/40 rounded font-bold text-xs cursor-pointer transition-colors"
                            >
                              REJECT
                            </button>
                          </>
                        )}
                        {selectedRemediation.status === "SELECTED" && (
                          <span className="px-3 py-1 bg-[#F18B78]/15 text-[#F18B78] border border-[#F18B78]/40 rounded font-bold text-xs">
                            SELECTED BY ENGINEER
                          </span>
                        )}
                        {selectedRemediation.status === "REJECTED" && (
                          <span className="px-3 py-1 bg-[#332B3C] text-[#F18B78] border border-[#F18B78]/40 rounded font-bold text-xs">
                            REJECTED
                          </span>
                        )}
                      </div>

                      <div className="text-[10px] text-[#8E8295]">
                        Priority Score: <span className="font-bold text-[#F4EFF6]">{selectedRemediation.priorityScore.toFixed(1)} / 100</span>
                      </div>
                    </div>
                  </div>

                  {selectedRemediation.rejectionReason && (
                    <div className="p-2 bg-[#F18B78]/10 border border-[#F18B78]/30 rounded text-[#F18B78] text-[11px]">
                      <span className="font-bold">Rejection Rationale: </span>
                      {selectedRemediation.rejectionReason}
                    </div>
                  )}
                </div>

                {/* Section 1: Validation Hypothesis */}
                <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-2">
                  <div className="flex items-center space-x-2">
                    <span className="w-2 h-2 bg-[#F18B78] rounded-sm" />
                    <h3 className="font-bold text-[#F4EFF6] uppercase text-xs">1. Validation Hypothesis (Non-Causal)</h3>
                  </div>
                  <div className="p-3 bg-[#1C1824] border border-[#443849] rounded text-[#B7ACBD] text-xs leading-relaxed">
                    {selectedRemediation.hypothesis}
                  </div>
                  <div className="text-[10px] text-[#8E8295] italic">
                    Note: Expected outcomes are deterministic empirical hypotheses that require re-evaluation in a future diagnostic run.
                  </div>
                </div>

                {/* Section 2: Expected Diagnostic Impact */}
                <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-3">
                  <div className="flex items-center space-x-2">
                    <span className="w-2 h-2 bg-[#B7D7A8] rounded-sm" />
                    <h3 className="font-bold text-[#F4EFF6] uppercase text-xs">2. Expected Diagnostic Impact (Hypotheses)</h3>
                  </div>
                  <div className="overflow-x-auto">
                    <table className="w-full text-left border-collapse">
                      <thead>
                        <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] bg-[#1C1824]">
                          <th className="py-2 px-2.5">METRIC</th>
                          <th className="py-2 px-2.5">EXPECTED DIRECTION</th>
                          <th className="py-2 px-2.5">RATIONALE</th>
                          <th className="py-2 px-2.5">CONFIDENCE</th>
                          <th className="py-2 px-2.5">EST. MAGNITUDE</th>
                        </tr>
                      </thead>
                      <tbody className="divide-y divide-[#443849]/60 text-xs">
                        {selectedRemediation.expectedImpact && selectedRemediation.expectedImpact.length > 0 ? (
                          selectedRemediation.expectedImpact.map((imp, idx) => (
                            <tr key={idx} className="hover:bg-[#332B3C]/40 transition-colors">
                              <td className="py-2 px-2.5 font-bold text-[#F4EFF6]">{imp.metric}</td>
                              <td className="py-2 px-2.5">
                                <span
                                  className={`px-1.5 py-0.5 rounded text-[10px] font-bold ${
                                    imp.expectedDirection === "DECREASE"
                                      ? "bg-[#B7D7A8]/15 text-[#B7D7A8] border border-[#B7D7A8]/40"
                                      : imp.expectedDirection === "INCREASE"
                                      ? "bg-[#F18B78]/15 text-[#F18B78] border border-[#F18B78]/40"
                                      : "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]"
                                  }`}
                                >
                                  {imp.expectedDirection}
                                </span>
                              </td>
                              <td className="py-2 px-2.5 text-[#B7ACBD] text-[11px]">{imp.rationale}</td>
                              <td className="py-2 px-2.5 text-[#8E8295] text-[10px] font-semibold">{imp.confidence}</td>
                              <td className="py-2 px-2.5 text-[#8E8295] text-[10px] uppercase">{imp.expectedMagnitude || "UNKNOWN"}</td>
                            </tr>
                          ))
                        ) : (
                          <tr>
                            <td colSpan={5} className="py-2 px-2.5 text-[#8E8295] italic">No specific metric impacts defined.</td>
                          </tr>
                        )}
                      </tbody>
                    </table>
                  </div>
                </div>

                {/* Section 3: Validation Plan & Acceptance Criteria */}
                <div className="grid grid-cols-2 gap-4">
                  <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-2">
                    <h3 className="font-bold text-[#F4EFF6] uppercase text-xs">3. Validation Strategy</h3>
                    <p className="text-[#B7ACBD] text-xs leading-relaxed">{selectedRemediation.validationStrategy}</p>

                    <div className="pt-2 border-t border-[#443849] space-y-1">
                      <span className="text-[10px] text-[#8E8295] font-bold">REQUIRED MODULES:</span>
                      <div className="flex flex-wrap gap-1">
                        {selectedRemediation.requiredModules.map((m) => (
                          <span
                            key={m}
                            className="px-1.5 py-0.5 bg-[#332B3C] text-[#B7ACBD] border border-[#443849] rounded text-[10px]"
                          >
                            {m}
                          </span>
                        ))}
                      </div>
                    </div>

                    <div className="pt-2 space-y-1">
                      <span className="text-[10px] text-[#8E8295] font-bold">REGRESSION GUARDS:</span>
                      <div className="flex flex-wrap gap-1">
                        {selectedRemediation.regressionGuards.map((g) => (
                          <span
                            key={g}
                            className="px-1.5 py-0.5 bg-[#F18B78]/15 text-[#F18B78] border border-[#F18B78]/40 rounded text-[10px]"
                          >
                            {g}
                          </span>
                        ))}
                      </div>
                    </div>
                  </div>

                  <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-2">
                    <h3 className="font-bold text-[#F4EFF6] uppercase text-xs">4. Measurable Acceptance Criteria</h3>
                    <ul className="space-y-1.5 text-xs text-[#B7ACBD]">
                      {selectedRemediation.acceptanceCriteria.map((crit, idx) => (
                        <li key={idx} className="flex items-start space-x-2">
                          <span className="text-[#B7D7A8] font-bold">✓</span>
                          <span>{crit}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                {/* Section 4: Provenance & Diagnostic Trace */}
                <div className="border border-[#443849] bg-[#2A2432] p-4 rounded space-y-2">
                  <h3 className="font-bold text-[#F4EFF6] uppercase text-xs">5. Provenance & Observational Trace</h3>
                  <div className="text-[11px] text-[#8E8295] space-y-1">
                    <div>
                      <span className="text-[#8E8295] font-bold">SOURCE RUN ID: </span>
                      <span className="text-[#F4EFF6] font-mono">{selectedRemediation.runId}</span>
                    </div>
                    {selectedRemediation.sourceInvestigationTarget && (
                      <div>
                        <span className="text-[#8E8295] font-bold">INVESTIGATION TARGET: </span>
                        <span className="text-[#F18B78] cursor-pointer hover:underline" onClick={() => onSelectSection && onSelectSection("06_INVESTIGATION")}>
                          {selectedRemediation.sourceInvestigationTarget}
                        </span>
                      </div>
                    )}
                    <div>
                      <span className="text-[#8E8295] font-bold">SOURCE RESULT IDS: </span>
                      <span className="text-[#B7ACBD] font-mono">{JSON.stringify(selectedRemediation.sourceResultIds)}</span>
                    </div>
                  </div>

                  {/* Navigation Shortcuts */}
                  <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-[#443849]">
                    <button
                      onClick={() => onSelectSection && onSelectSection("08_EXPERIMENT")}
                      className="px-2.5 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded text-[10px] font-bold transition-colors cursor-pointer"
                    >
                      → TEST IN 08 EXPERIMENTAL VALIDATION
                    </button>
                    <button
                      onClick={() => onSelectSection && onSelectSection("06_INVESTIGATION")}
                      className="px-2.5 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded text-[10px] transition-colors cursor-pointer"
                    >
                      → JUMP TO 06 ROOT-CAUSE INVESTIGATION
                    </button>
                    <button
                      onClick={() => onSelectSection && onSelectSection("00_INTELLIGENCE")}
                      className="px-2.5 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded text-[10px] transition-colors cursor-pointer"
                    >
                      → JUMP TO 00 DIAGNOSTIC INTEL
                    </button>
                    <button
                      onClick={() => onSelectSection && onSelectSection("05_ERROR_FORENSICS")}
                      className="px-2.5 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#F4EFF6] border border-[#443849] rounded text-[10px] transition-colors cursor-pointer"
                    >
                      → JUMP TO 05 ERROR FORENSICS
                    </button>
                  </div>
                </div>
              </div>
            ) : (
              <div className="p-12 text-center text-[#8E8295]">No remediation candidate selected.</div>
            )}
          </div>
        </div>
      ) : (
        /* 4. BEFORE / AFTER RUN COMPARISON VIEW */
        <div className="flex-1 flex flex-col p-5 overflow-y-auto space-y-5">
          {/* Candidate Selection Bar */}
          <div className="border border-[#443849] bg-[#2A2432] p-4 rounded flex flex-wrap items-center justify-between gap-4">
            <div className="flex items-center space-x-3">
              <span className="text-[#8E8295] font-bold">BASELINE RUN:</span>
              <span className="px-2 py-1 bg-[#1C1824] border border-[#443849] rounded font-bold text-[#F4EFF6] font-mono">
                {runId}
              </span>
              <span className="text-[#5A4B62]">VS</span>
              <span className="text-[#8E8295] font-bold">CANDIDATE RUN:</span>
              <select
                value={candidateRunId}
                onChange={(e) => setCandidateRunId(e.target.value)}
                className="px-2.5 py-1 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] text-xs font-mono"
              >
                <option value="">Select Candidate Run...</option>
                {recentRuns
                  .filter((r) => r.id !== runId)
                  .map((r) => (
                    <option key={r.id} value={r.id}>
                      {r.id} ({r.model?.name || "Model"} - {r.status})
                    </option>
                  ))}
              </select>
              <button
                onClick={() => handleRunComparison(candidateRunId)}
                disabled={!candidateRunId || isComparing}
                className="px-4 py-1.5 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] rounded font-bold text-xs disabled:opacity-50 cursor-pointer transition-colors"
              >
                {isComparing ? "COMPARING..." : "COMPARE RUNS"}
              </button>
            </div>

            {comparison && (
              <div className="flex items-center space-x-2 text-[11px]">
                <span
                  className={`px-3 py-1 rounded font-bold ${
                    comparison.overallAssessment === "IMPROVED"
                      ? "bg-[#B7D7A8]/15 text-[#B7D7A8] border border-[#B7D7A8]/40"
                      : comparison.overallAssessment === "REGRESSED"
                      ? "bg-[#F18B78]/15 text-[#F18B78] border border-[#F18B78]/40"
                      : comparison.overallAssessment === "MIXED"
                      ? "bg-[#E8B66D]/15 text-[#E8B66D] border border-[#E8B66D]/40"
                      : "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]"
                  }`}
                >
                  ASSESSMENT: {comparison.overallAssessment}
                </span>
              </div>
            )}
          </div>

          {comparisonError && (
            <div className="p-3 bg-[#F18B78]/15 border border-[#F18B78]/40 rounded text-[#F18B78] text-xs">
              {comparisonError}
            </div>
          )}

          {comparison ? (
            <div className="space-y-4">
              {/* Assessment Rationale Card */}
              <div className="p-4 bg-[#2A2432] border border-[#443849] rounded space-y-1">
                <div className="text-[10px] text-[#8E8295] font-bold uppercase tracking-wider">Assessment Rationale</div>
                <p className="text-[#F4EFF6] text-xs">{comparison.assessmentRationale}</p>
                <div className="flex items-center space-x-4 pt-2 text-[11px]">
                  <span className="text-[#B7D7A8] font-bold">Improved: {comparison.improvedMetricCount}</span>
                  <span className="text-[#F18B78] font-bold">Regressed: {comparison.regressedMetricCount}</span>
                  <span className="text-[#B7ACBD]">Unchanged: {comparison.unchangedMetricCount}</span>
                  {comparison.missingMetricCount > 0 && (
                    <span className="text-[#E8B66D] font-bold">Missing Metrics: {comparison.missingMetricCount}</span>
                  )}
                </div>
              </div>

              {/* Metric Delta Comparison Table */}
              <div className="border border-[#443849] bg-[#2A2432] rounded overflow-hidden">
                <div className="px-4 py-2 border-b border-[#443849] bg-[#332B3C] flex items-center justify-between">
                  <h3 className="font-bold text-[#F4EFF6] text-xs uppercase">Observed Metric Deltas</h3>
                  <div className="text-[10px] text-[#B7ACBD]">
                    Baseline: <span className="text-[#F4EFF6] font-bold">{comparison.baselineModelName}</span> | Candidate:{" "}
                    <span className="text-[#F4EFF6] font-bold">{comparison.candidateModelName}</span>
                  </div>
                </div>

                <div className="overflow-x-auto">
                  <table className="w-full text-left border-collapse">
                    <thead>
                      <tr className="border-b border-[#443849] text-[#8E8295] text-[10px] bg-[#1C1824]">
                        <th className="py-2 px-3">METRIC</th>
                        <th className="py-2 px-3">MODULE</th>
                        <th className="py-2 px-3">BASELINE</th>
                        <th className="py-2 px-3">CANDIDATE</th>
                        <th className="py-2 px-3">DELTA</th>
                        <th className="py-2 px-3">% CHANGE</th>
                        <th className="py-2 px-3">ASSESSMENT</th>
                        <th className="py-2 px-3">INTERPRETATION</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#443849]/60 text-xs">
                      {comparison.metricComparisons.map((mc, idx) => (
                        <tr key={idx} className="hover:bg-[#332B3C]/50 transition-colors">
                          <td className="py-2 px-3 font-bold text-[#F4EFF6]">{mc.metricName}</td>
                          <td className="py-2 px-3 text-[#B7ACBD] text-[10px]">{mc.module}</td>
                          <td className="py-2 px-3 text-[#B7ACBD] font-mono">{mc.baselineValue.toFixed(4)}</td>
                          <td className="py-2 px-3 text-[#B7ACBD] font-mono">{mc.candidateValue.toFixed(4)}</td>
                          <td
                            className={`py-2 px-3 font-mono font-bold ${
                              mc.assessment === "IMPROVED"
                                ? "text-[#B7D7A8]"
                                : mc.assessment === "REGRESSED"
                                ? "text-[#F18B78]"
                                : "text-[#B7ACBD]"
                            }`}
                          >
                            {mc.delta > 0 ? `+${mc.delta.toFixed(4)}` : mc.delta.toFixed(4)}
                          </td>
                          <td className="py-2 px-3 font-mono text-[#8E8295] text-[11px]">
                            {mc.relativeChangePct > 0 ? `+${mc.relativeChangePct.toFixed(1)}%` : `${mc.relativeChangePct.toFixed(1)}%`}
                          </td>
                          <td className="py-2 px-3">
                            <span
                              className={`px-1.5 py-0.5 rounded text-[9px] font-bold ${
                                mc.assessment === "IMPROVED"
                                  ? "bg-[#B7D7A8]/15 text-[#B7D7A8] border border-[#B7D7A8]/40"
                                  : mc.assessment === "REGRESSED"
                                  ? "bg-[#F18B78]/15 text-[#F18B78] border border-[#F18B78]/40"
                                  : "bg-[#332B3C] text-[#B7ACBD] border border-[#443849]"
                              }`}
                            >
                              {mc.assessment}
                            </span>
                          </td>
                          <td className="py-2 px-3 text-[#B7ACBD] text-[11px]">{mc.interpretation}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* Missing Metrics Warning Card */}
              {comparison.missingMetrics && comparison.missingMetrics.length > 0 && (
                <div className="p-3 bg-[#E8B66D]/10 border border-[#E8B66D]/30 rounded space-y-1">
                  <div className="text-[10px] text-[#E8B66D] font-bold uppercase tracking-wider">Missing Metrics (Not in both runs)</div>
                  <div className="flex flex-wrap gap-1">
                    {comparison.missingMetrics.map((m, idx) => (
                      <span key={idx} className="px-2 py-0.5 bg-[#E8B66D]/15 text-[#E8B66D] border border-[#E8B66D]/30 rounded text-[10px]">
                        {m}
                      </span>
                    ))}
                  </div>
                </div>
              )}
            </div>
          ) : (
            <div className="p-16 text-center text-[#8E8295] border border-dashed border-[#443849] rounded">
              Select a candidate run above and click &quot;COMPARE RUNS&quot; to perform deterministic metric comparison.
            </div>
          )}
        </div>
      )}

      {/* Reject Modal */}
      {showRejectModal && selectedRemediation && (
        <div className="fixed inset-0 bg-[#211D29]/80 backdrop-blur-sm flex items-center justify-center z-50 p-4">
          <div className="bg-[#2A2432] border border-[#443849] rounded p-5 max-w-md w-full space-y-4 shadow-xl">
            <h3 className="text-sm font-bold text-[#F4EFF6] uppercase">Reject Remediation Candidate</h3>
            <p className="text-xs text-[#B7ACBD]">
              Provide an optional rationale for rejecting candidate &quot;{selectedRemediation.title}&quot;.
            </p>
            <textarea
              value={rejectReason}
              onChange={(e) => setRejectReason(e.target.value)}
              placeholder="e.g. Risk of revenue degradation in high-value transactions..."
              rows={3}
              className="w-full px-3 py-2 bg-[#1C1824] border border-[#443849] rounded text-[#F4EFF6] placeholder-[#8E8295] text-xs focus:outline-none focus:border-[#F18B78]"
            />
            <div className="flex items-center justify-end space-x-2 pt-2">
              <button
                onClick={() => setShowRejectModal(false)}
                className="px-3 py-1 bg-[#332B3C] hover:bg-[#3D3448] text-[#B7ACBD] hover:text-[#F4EFF6] border border-[#443849] rounded text-xs cursor-pointer transition-colors"
              >
                Cancel
              </button>
              <button
                onClick={() => handleRejectRemediation(selectedRemediation.id)}
                disabled={isActionLoading}
                className="px-4 py-1 bg-[#F18B78] hover:bg-[#F39C8B] text-[#211D29] font-bold rounded text-xs cursor-pointer transition-colors"
              >
                Confirm Rejection
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
