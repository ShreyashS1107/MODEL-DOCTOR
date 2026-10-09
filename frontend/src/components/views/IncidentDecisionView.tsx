"use client";

import React, { useState, useEffect, useMemo } from "react";
import {
  DiagnosticIncident,
  IncidentEvidenceDossier,
  IncidentLifecycleState,
  IncidentDecisionState,
  DecisionConfidence,
  NavSection,
} from "@/types/diagnostics";
import {
  getIncidents,
  getIncidentDossier,
  recalculateIncidents,
  acknowledgeIncident,
  investigateIncident,
  planIncidentRemediation,
  startIncidentValidation,
  resolveIncident,
  suppressIncident,
  RunSummary,
} from "@/lib/api";

export interface IncidentDecisionViewProps {
  runId: string;
  runSummary?: RunSummary | null;
  onSelectSection?: (section: NavSection) => void;
  onSelectFeature?: (featureName: string) => void;
  onRefresh?: () => void;
  onNavigateSection?: (section: NavSection) => void;
  onSelectInvestigationTarget?: (targetKey: string) => void;
}

const SEVERITY_STYLES: Record<string, { bg: string; text: string; border: string }> = {
  CRITICAL: { bg: "bg-[#ef4444]/15", text: "text-[#f87171]", border: "border-[#ef4444]/40" },
  HIGH: { bg: "bg-[#f97316]/15", text: "text-[#fb923c]", border: "border-[#f97316]/40" },
  MEDIUM: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  WARNING: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  LOW: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  INFO: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
};

const LIFECYCLE_STYLES: Record<IncidentLifecycleState, { bg: string; text: string; border: string }> = {
  OPEN: { bg: "bg-[#ef4444]/15", text: "text-[#f87171]", border: "border-[#ef4444]/40" },
  ACKNOWLEDGED: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  INVESTIGATING: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  MITIGATION_PLANNED: { bg: "bg-[#8b5cf6]/15", text: "text-[#a78bfa]", border: "border-[#8b5cf6]/40" },
  VALIDATING: { bg: "bg-[#06b6d4]/15", text: "text-[#22d3ee]", border: "border-[#06b6d4]/40" },
  MONITORING: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
  RESOLVED: { bg: "bg-[#10b981]/20", text: "text-[#10b981]", border: "border-[#10b981]/50" },
  REOPENED: { bg: "bg-[#ec4899]/15", text: "text-[#f472b6]", border: "border-[#ec4899]/40" },
  SUPPRESSED: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40" },
};

const DECISION_STYLES: Record<IncidentDecisionState, { bg: string; text: string; border: string }> = {
  NO_ACTION: { bg: "bg-[#10b981]/15", text: "text-[#34d399]", border: "border-[#10b981]/40" },
  INVESTIGATE: { bg: "bg-[#3b82f6]/15", text: "text-[#60a5fa]", border: "border-[#3b82f6]/40" },
  REVIEW_REMEDIATION: { bg: "bg-[#8b5cf6]/15", text: "text-[#a78bfa]", border: "border-[#8b5cf6]/40" },
  VALIDATE_REMEDIATION: { bg: "bg-[#06b6d4]/15", text: "text-[#22d3ee]", border: "border-[#06b6d4]/40" },
  MONITOR: { bg: "bg-[#f59e0b]/15", text: "text-[#fbbf24]", border: "border-[#f59e0b]/40" },
  REOPEN_INVESTIGATION: { bg: "bg-[#ec4899]/15", text: "text-[#f472b6]", border: "border-[#ec4899]/40" },
  ESCALATE: { bg: "bg-[#ef4444]/20", text: "text-[#ef4444]", border: "border-[#ef4444]/50" },
  INSUFFICIENT_EVIDENCE: { bg: "bg-[#64748b]/15", text: "text-[#94a3b8]", border: "border-[#64748b]/40" },
};

const CONFIDENCE_BADGES: Record<DecisionConfidence, { bg: string; text: string; border: string }> = {
  HIGH: { bg: "bg-[#10b981]/20", text: "text-[#34d399]", border: "border-[#10b981]/50" },
  MEDIUM: { bg: "bg-[#f59e0b]/20", text: "text-[#fbbf24]", border: "border-[#f59e0b]/50" },
  LOW: { bg: "bg-[#ef4444]/20", text: "text-[#f87171]", border: "border-[#ef4444]/50" },
  INSUFFICIENT: { bg: "bg-[#64748b]/20", text: "text-[#94a3b8]", border: "border-[#64748b]/50" },
};

export const IncidentDecisionView: React.FC<IncidentDecisionViewProps> = ({
  runId,
  runSummary,
  onSelectSection,
  onSelectFeature,
  onRefresh,
  onNavigateSection,
  onSelectInvestigationTarget,
}) => {
  const navigate = onSelectSection || onNavigateSection;

  // Data states
  const [incidents, setIncidents] = useState<DiagnosticIncident[]>([]);
  const [selectedIncidentId, setSelectedIncidentId] = useState<number | null>(null);
  const [dossier, setDossier] = useState<IncidentEvidenceDossier | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(true);
  const [isDossierLoading, setIsDossierLoading] = useState<boolean>(false);
  const [isRecalculating, setIsRecalculating] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [statusNotification, setStatusNotification] = useState<string | null>(null);

  // Filters
  const [severityFilter, setSeverityFilter] = useState<string>("ALL");
  const [lifecycleFilter, setLifecycleFilter] = useState<string>("ALL");
  const [searchTarget, setSearchTarget] = useState<string>("");

  // Modals for operator actions
  const [activeModal, setActiveModal] = useState<string | null>(null);
  const [modalActor, setModalActor] = useState<string>("USER");
  const [modalText, setModalText] = useState<string>("");
  const [modalDuration, setModalDuration] = useState<number>(24);

  // Load incidents list
  const fetchIncidents = async () => {
    setIsLoading(true);
    setErrorMessage(null);
    try {
      const data = await getIncidents(runId, true);
      setIncidents(data);
      if (data.length > 0) {
        if (!selectedIncidentId || !data.some((i) => i.id === selectedIncidentId)) {
          setSelectedIncidentId(data[0].id);
        }
      } else {
        setSelectedIncidentId(null);
        setDossier(null);
      }
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to load operational incidents");
    } finally {
      setIsLoading(false);
    }
  };

  // Load selected incident dossier
  const fetchDossier = async (incId: number) => {
    setIsDossierLoading(true);
    try {
      const data = await getIncidentDossier(runId, incId);
      setDossier(data);
    } catch (err: any) {
      setErrorMessage(err?.message || `Failed to load dossier for incident #${incId}`);
    } finally {
      setIsDossierLoading(false);
    }
  };

  useEffect(() => {
    fetchIncidents();
  }, [runId]);

  useEffect(() => {
    if (selectedIncidentId) {
      fetchDossier(selectedIncidentId);
    }
  }, [selectedIncidentId, runId]);

  const handleRecalculate = async () => {
    setIsRecalculating(true);
    setStatusNotification(null);
    setErrorMessage(null);
    try {
      const res = await recalculateIncidents(runId);
      setStatusNotification(res.message);
      await fetchIncidents();
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to recalculate incidents");
    } finally {
      setIsRecalculating(false);
    }
  };

  // Operator Action Handlers
  const handleAcknowledge = async () => {
    if (!selectedIncidentId) return;
    try {
      await acknowledgeIncident(runId, selectedIncidentId, modalActor, modalText || "Acknowledged by operator");
      setStatusNotification(`Incident #${selectedIncidentId} acknowledged`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to acknowledge incident");
    }
  };

  const handleInvestigate = async () => {
    if (!selectedIncidentId) return;
    try {
      await investigateIncident(runId, selectedIncidentId, modalActor, modalText || "Investigation initiated");
      setStatusNotification(`Incident #${selectedIncidentId} set to INVESTIGATING`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to start investigation");
    }
  };

  const handlePlanMitigation = async () => {
    if (!selectedIncidentId) return;
    try {
      await planIncidentRemediation(runId, selectedIncidentId, undefined, modalActor, modalText || "Mitigation planned");
      setStatusNotification(`Incident #${selectedIncidentId} set to MITIGATION_PLANNED`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to plan mitigation");
    }
  };

  const handleStartValidation = async () => {
    if (!selectedIncidentId) return;
    try {
      await startIncidentValidation(runId, selectedIncidentId, undefined, modalActor, modalText || "Validation experiment launched");
      setStatusNotification(`Incident #${selectedIncidentId} set to VALIDATING`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to start validation");
    }
  };

  const handleResolve = async () => {
    if (!selectedIncidentId) return;
    try {
      await resolveIncident(runId, selectedIncidentId, modalText || "Resolved by operator", modalActor);
      setStatusNotification(`Incident #${selectedIncidentId} RESOLVED`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to resolve incident");
    }
  };

  const handleSuppress = async () => {
    if (!selectedIncidentId) return;
    try {
      await suppressIncident(runId, selectedIncidentId, modalText || "Suppressed by operator", modalDuration, modalActor);
      setStatusNotification(`Incident #${selectedIncidentId} SUPPRESSED for ${modalDuration}h`);
      setActiveModal(null);
      await fetchIncidents();
      await fetchDossier(selectedIncidentId);
    } catch (err: any) {
      setErrorMessage(err?.message || "Failed to suppress incident");
    }
  };

  // Filtered incidents
  const filteredIncidents = useMemo(() => {
    return incidents.filter((inc) => {
      if (severityFilter !== "ALL" && inc.currentSeverity !== severityFilter) return false;
      if (lifecycleFilter !== "ALL" && inc.lifecycleState !== lifecycleFilter) return false;
      if (searchTarget && !inc.primaryTarget.toLowerCase().includes(searchTarget.toLowerCase())) return false;
      return true;
    });
  }, [incidents, severityFilter, lifecycleFilter, searchTarget]);

  // Incident summary stats
  const stats = useMemo(() => {
    const total = incidents.length;
    const critical = incidents.filter((i) => i.currentSeverity === "CRITICAL").length;
    const high = incidents.filter((i) => i.currentSeverity === "HIGH").length;
    const active = incidents.filter((i) => i.lifecycleState !== "RESOLVED" && i.lifecycleState !== "SUPPRESSED").length;
    const resolved = incidents.filter((i) => i.lifecycleState === "RESOLVED").length;
    return { total, critical, high, active, resolved };
  }, [incidents]);

  return (
    <div className="flex flex-col h-full bg-[#0a0c10] text-[#e2e8f0] font-mono select-none overflow-hidden">
      {/* 1. TOP DENSE HUD */}
      <div className="flex items-center justify-between px-4 py-2 bg-[#0d1117] border-b border-[#1f2533] shrink-0">
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-[#3b82f6] animate-pulse" />
            <span className="text-xs font-bold tracking-wider text-white">INCIDENT DECISION WORKSPACE</span>
            <span className="px-1.5 py-0.5 text-[10px] font-bold bg-[#1e293b] text-[#94a3b8] rounded border border-[#334155]">
              PHASE 11
            </span>
          </div>

          <span className="text-[#334155]">|</span>

          <div className="flex items-center gap-3 text-xs">
            <span className="text-[#64748b]">LINEAGE:</span>
            <span className="text-white font-bold">{runSummary?.runId || runId}</span>
          </div>

          <span className="text-[#334155]">|</span>

          {/* HUD Counters */}
          <div className="flex items-center gap-2 text-[11px]">
            <span className="text-[#64748b]">ACTIVE:</span>
            <span className="font-bold text-white bg-[#1e293b] px-1.5 py-0.2 rounded border border-[#334155]">
              {stats.active}
            </span>

            <span className="text-[#64748b] ml-1">CRITICAL:</span>
            <span className="font-bold text-[#f87171] bg-[#ef4444]/15 px-1.5 py-0.2 rounded border border-[#ef4444]/30">
              {stats.critical}
            </span>

            <span className="text-[#64748b] ml-1">HIGH:</span>
            <span className="font-bold text-[#fb923c] bg-[#f97316]/15 px-1.5 py-0.2 rounded border border-[#f97316]/30">
              {stats.high}
            </span>

            <span className="text-[#64748b] ml-1">RESOLVED:</span>
            <span className="font-bold text-[#34d399] bg-[#10b981]/15 px-1.5 py-0.2 rounded border border-[#10b981]/30">
              {stats.resolved}
            </span>
          </div>
        </div>

        <div className="flex items-center gap-2">
          {statusNotification && (
            <span className="text-[11px] text-[#34d399] bg-[#10b981]/10 px-2 py-0.5 rounded border border-[#10b981]/30 animate-fade-in">
              {statusNotification}
            </span>
          )}

          <button
            onClick={handleRecalculate}
            disabled={isRecalculating}
            className="flex items-center gap-1.5 px-2.5 py-1 text-xs font-semibold bg-[#1e293b] hover:bg-[#334155] text-[#38bdf8] border border-[#0284c7]/40 rounded transition-colors cursor-pointer disabled:opacity-50"
          >
            <span className={isRecalculating ? "animate-spin" : ""}>↻</span>
            {isRecalculating ? "RECALCULATING..." : "RECALCULATE INCIDENTS"}
          </button>
        </div>
      </div>

      {errorMessage && (
        <div className="px-4 py-2 bg-[#ef4444]/10 border-b border-[#ef4444]/30 text-xs text-[#f87171] flex justify-between items-center shrink-0">
          <span>⚠️ {errorMessage}</span>
          <button onClick={() => setErrorMessage(null)} className="text-[#94a3b8] hover:text-white">✕</button>
        </div>
      )}

      {/* 2. MAIN SPLIT CONSOLE */}
      <div className="flex flex-1 min-h-0">
        {/* LEFT COLUMN: INCIDENT QUEUE */}
        <div className="w-[420px] flex flex-col border-r border-[#1f2533] bg-[#0c0f16] shrink-0">
          {/* Queue Filter Bar */}
          <div className="p-2 border-b border-[#1f2533] space-y-1.5">
            <div className="flex items-center justify-between">
              <span className="text-[10px] text-[#64748b] uppercase tracking-wider font-bold">
                INCIDENT QUEUE ({filteredIncidents.length})
              </span>
              <div className="flex items-center gap-1 text-[10px]">
                {["ALL", "CRITICAL", "HIGH"].map((sev) => (
                  <button
                    key={sev}
                    onClick={() => setSeverityFilter(sev)}
                    className={`px-1.5 py-0.5 rounded text-[10px] ${
                      severityFilter === sev
                        ? "bg-[#3b82f6] text-white font-bold"
                        : "bg-[#181d28] text-[#94a3b8] hover:bg-[#232938]"
                    }`}
                  >
                    {sev}
                  </button>
                ))}
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <input
                type="text"
                value={searchTarget}
                onChange={(e) => setSearchTarget(e.target.value)}
                placeholder="Filter target (e.g. FEATURE::income)..."
                className="w-full bg-[#141822] text-xs px-2 py-1 border border-[#263044] rounded text-white focus:outline-none focus:border-[#3b82f6]"
              />
              {searchTarget && (
                <button
                  onClick={() => setSearchTarget("")}
                  className="text-xs text-[#64748b] hover:text-white px-1"
                >
                  ✕
                </button>
              )}
            </div>

            {/* Lifecycle State Pills */}
            <div className="flex items-center gap-1 overflow-x-auto pb-0.5 text-[9px]">
              {["ALL", "OPEN", "INVESTIGATING", "VALIDATING", "MONITORING", "RESOLVED"].map((state) => (
                <button
                  key={state}
                  onClick={() => setLifecycleFilter(state)}
                  className={`px-1.5 py-0.5 rounded whitespace-nowrap ${
                    lifecycleFilter === state
                      ? "bg-[#334155] text-white font-bold"
                      : "bg-[#121620] text-[#64748b] hover:text-[#cbd5e1]"
                  }`}
                >
                  {state}
                </button>
              ))}
            </div>
          </div>

          {/* Queue List */}
          <div className="flex-1 overflow-y-auto p-1.5 space-y-1">
            {isLoading ? (
              <div className="p-8 text-center text-xs text-[#64748b]">
                <span className="inline-block animate-spin mr-2">⟳</span>
                Correlating operational incidents...
              </div>
            ) : filteredIncidents.length === 0 ? (
              <div className="p-8 text-center text-xs text-[#64748b]">
                No operational incidents matching current filters.
              </div>
            ) : (
              filteredIncidents.map((inc) => {
                const isSelected = selectedIncidentId === inc.id;
                const sevStyle = SEVERITY_STYLES[inc.currentSeverity] || SEVERITY_STYLES.INFO;
                const lifeStyle = LIFECYCLE_STYLES[inc.lifecycleState] || LIFECYCLE_STYLES.OPEN;
                const decStyle = DECISION_STYLES[inc.decisionRecommendation] || DECISION_STYLES.NO_ACTION;

                return (
                  <div
                    key={inc.id}
                    onClick={() => setSelectedIncidentId(inc.id)}
                    className={`p-2.5 rounded border transition-all cursor-pointer ${
                      isSelected
                        ? "bg-[#182030] border-[#3b82f6] shadow-sm"
                        : "bg-[#10141e] border-[#1e2536] hover:bg-[#141a26] hover:border-[#2d374d]"
                    }`}
                  >
                    <div className="flex items-center justify-between mb-1">
                      <div className="flex items-center gap-1.5">
                        <span className={`px-1.5 py-0.2 text-[9px] font-bold rounded border ${sevStyle.bg} ${sevStyle.text} ${sevStyle.border}`}>
                          {inc.currentSeverity}
                        </span>
                        <span className="text-xs font-bold text-white">{inc.incidentCode}</span>
                      </div>

                      <div className="flex items-center gap-1.5">
                        <span className="text-[10px] text-[#64748b]">PRIORITY:</span>
                        <span className="text-xs font-bold text-white bg-[#1e293b] px-1.5 py-0.2 rounded border border-[#334155] tabular-nums">
                          {inc.priorityScore}
                        </span>
                      </div>
                    </div>

                    <div className="text-xs font-semibold text-[#38bdf8] truncate mb-1">
                      {inc.primaryTarget}
                    </div>

                    <div className="flex items-center justify-between text-[10px] text-[#94a3b8] mb-1.5">
                      <div className="flex items-center gap-2">
                        <span>{inc.independentModuleCount} mod</span>
                        <span>•</span>
                        <span>{inc.relatedAlertsCount} alerts</span>
                        {inc.hasContradictoryEvidence && (
                          <span className="text-[#f59e0b] font-bold" title="Contradictory evidence detected">
                            ⚠️ CONFLICT
                          </span>
                        )}
                      </div>

                      <span className={`px-1.5 py-0.2 rounded text-[9px] border font-semibold ${lifeStyle.bg} ${lifeStyle.text} ${lifeStyle.border}`}>
                        {inc.lifecycleState}
                      </span>
                    </div>

                    {/* Recommendation Pill */}
                    <div className="flex items-center justify-between pt-1 border-t border-[#1e2536] text-[10px]">
                      <span className="text-[#64748b]">DECISION:</span>
                      <span className={`px-1.5 py-0.2 rounded font-bold border ${decStyle.bg} ${decStyle.text} ${decStyle.border}`}>
                        {inc.decisionRecommendation}
                      </span>
                    </div>
                  </div>
                );
              })
            )}
          </div>
        </div>

        {/* RIGHT COLUMN: INCIDENT EVIDENCE DOSSIER & DECISION CONSOLE */}
        <div className="flex-1 flex flex-col bg-[#0a0c10] overflow-y-auto">
          {isDossierLoading && !dossier ? (
            <div className="flex-1 flex items-center justify-center text-xs text-[#64748b]">
              <span className="inline-block animate-spin mr-2">⟳</span>
              Loading comprehensive incident evidence dossier...
            </div>
          ) : !dossier ? (
            <div className="flex-1 flex items-center justify-center text-xs text-[#64748b]">
              Select an incident from the queue to inspect evidence and operator decision state.
            </div>
          ) : (
            <div className="p-4 space-y-4 max-w-6xl">
              {/* Incident Header Card */}
              <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d]">
                <div className="flex items-start justify-between">
                  <div>
                    <div className="flex items-center gap-2 mb-1">
                      <span className="text-base font-bold text-white">{dossier.incidentCode}</span>
                      <span className="text-sm font-semibold text-[#94a3b8]">— {dossier.title}</span>
                    </div>

                    <div className="flex items-center gap-3 text-xs text-[#64748b]">
                      <div>
                        CATEGORY: <span className="text-[#cbd5e1] font-semibold">{dossier.category}</span>
                      </div>
                      <div>•</div>
                      <div>
                        PRIMARY TARGET:{" "}
                        <button
                          onClick={() => onSelectFeature && onSelectFeature(dossier.primaryTarget.replace("FEATURE::", ""))}
                          className="text-[#38bdf8] font-bold hover:underline cursor-pointer"
                        >
                          {dossier.primaryTarget}
                        </button>
                      </div>
                      <div>•</div>
                      <div>
                        LINEAGE: <span className="text-[#94a3b8]">{dossier.modelLineageId}</span>
                      </div>
                    </div>
                  </div>

                  {/* Priority and Severity HUD */}
                  <div className="flex items-center gap-3">
                    <div className="text-right">
                      <div className="text-[10px] text-[#64748b]">PRIORITY SCORE</div>
                      <div className="text-xl font-bold text-white tabular-nums">
                        {dossier.priorityScore}
                        <span className="text-xs text-[#64748b]">/100</span>
                      </div>
                    </div>

                    <div className="flex flex-col gap-1 items-end">
                      <span className={`px-2 py-0.5 text-xs font-bold rounded border ${SEVERITY_STYLES[dossier.currentSeverity]?.bg} ${SEVERITY_STYLES[dossier.currentSeverity]?.text} ${SEVERITY_STYLES[dossier.currentSeverity]?.border}`}>
                        {dossier.currentSeverity}
                      </span>
                      <span className={`px-2 py-0.5 text-[10px] font-bold rounded border ${LIFECYCLE_STYLES[dossier.lifecycleState]?.bg} ${LIFECYCLE_STYLES[dossier.lifecycleState]?.text} ${LIFECYCLE_STYLES[dossier.lifecycleState]?.border}`}>
                        {dossier.lifecycleState}
                      </span>
                    </div>
                  </div>
                </div>

                {/* Priority Breakdown Chips */}
                {dossier.priorityBreakdown && (
                  <div className="mt-3 pt-3 border-t border-[#1e2536] flex items-center gap-2 flex-wrap text-[11px]">
                    <span className="text-[#64748b] font-bold">SCORE COMPOSITION:</span>
                    <span className="bg-[#182030] text-[#cbd5e1] px-2 py-0.5 rounded border border-[#2d374d]">
                      Severity: +{dossier.priorityBreakdown.severityContribution}
                    </span>
                    <span className="bg-[#182030] text-[#cbd5e1] px-2 py-0.5 rounded border border-[#2d374d]">
                      Independent Modules ({dossier.independentModuleCount}): +{dossier.priorityBreakdown.independentEvidenceContribution}
                    </span>
                    <span className="bg-[#182030] text-[#cbd5e1] px-2 py-0.5 rounded border border-[#2d374d]">
                      Persistence/Escalation: +{dossier.priorityBreakdown.persistenceContribution}
                    </span>
                    <span className="bg-[#182030] text-[#cbd5e1] px-2 py-0.5 rounded border border-[#2d374d]">
                      Health Impact: +{dossier.priorityBreakdown.healthImpactContribution}
                    </span>
                  </div>
                )}
              </div>

              {/* Contradictory Evidence Alert (If Present) */}
              {dossier.contradictoryEvidence?.hasConflict && (
                <div className="p-3.5 rounded bg-[#78350f]/20 border border-[#f59e0b]/50 text-xs text-[#fbbf24] space-y-1.5">
                  <div className="flex items-center gap-2 font-bold">
                    <span>⚠️ CONTRADICTORY EVIDENCE DETECTED</span>
                    <span className="text-[10px] px-1.5 py-0.2 rounded bg-[#f59e0b]/30 text-white font-mono">
                      CONFIDENCE PENALTY APPLIED
                    </span>
                  </div>
                  <p className="text-[#fef3c7]">{dossier.contradictoryEvidence.summary}</p>
                  <ul className="list-disc list-inside space-y-0.5 text-[11px] text-[#fde68a]">
                    {dossier.contradictoryEvidence.conflictingSignals.map((sig, idx) => (
                      <li key={idx}>{sig}</li>
                    ))}
                  </ul>
                  <div className="text-[11px] text-[#fbbf24] pt-1 border-t border-[#f59e0b]/20">
                    Impact: {dossier.contradictoryEvidence.confidenceImpact} • Recommended Action: {dossier.contradictoryEvidence.recommendedAction}
                  </div>
                </div>
              )}

              {/* Evidence Synthesis Summary */}
              <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d] space-y-2">
                <div className="text-xs font-bold text-[#94a3b8] uppercase tracking-wider">
                  EVIDENCE SYNTHESIS & ASSOCIATIVE SUMMARY
                </div>
                <div className="text-xs font-mono bg-[#080a0f] p-3 rounded border border-[#1a2030] text-[#e2e8f0] whitespace-pre-wrap leading-relaxed">
                  {dossier.evidenceSummary}
                </div>
              </div>

              {/* DENSE EVIDENCE MATRIX TABLE */}
              <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d] space-y-2">
                <div className="flex items-center justify-between">
                  <div className="text-xs font-bold text-[#94a3b8] uppercase tracking-wider">
                    EVIDENCE MATRIX ({dossier.evidenceMatrix?.length || 0} SIGNALS)
                  </div>
                  <span className="text-[10px] text-[#64748b]">
                    {dossier.independentModuleCount} INDEPENDENT DIAGNOSTIC MODULES
                  </span>
                </div>

                <div className="overflow-x-auto border border-[#1e2536] rounded">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="bg-[#141a26] text-[#64748b] text-[10px] font-bold uppercase border-b border-[#1e2536]">
                        <th className="p-2">EVIDENCE TYPE</th>
                        <th className="p-2">MODULE</th>
                        <th className="p-2">METRIC</th>
                        <th className="p-2 text-right">VALUE</th>
                        <th className="p-2 text-right">REFERENCE</th>
                        <th className="p-2 text-center">SEVERITY</th>
                        <th className="p-2">RUN PROVENANCE</th>
                        <th className="p-2">TRIGGER REASON</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-[#1e2536] text-[11px]">
                      {dossier.evidenceMatrix?.map((row, idx) => {
                        const sev = SEVERITY_STYLES[row.severity] || SEVERITY_STYLES.INFO;
                        return (
                          <tr key={idx} className="hover:bg-[#141b2b] transition-colors">
                            <td className="p-2 font-semibold text-white">{row.alertType}</td>
                            <td className="p-2">
                              <span className="px-1.5 py-0.2 rounded bg-[#1e293b] text-[#38bdf8] text-[10px] font-bold border border-[#334155]">
                                {row.module}
                              </span>
                            </td>
                            <td className="p-2 text-[#94a3b8] font-mono">{row.metric}</td>
                            <td className="p-2 text-right font-bold text-white tabular-nums">{row.value}</td>
                            <td className="p-2 text-right text-[#64748b] tabular-nums">{row.threshold}</td>
                            <td className="p-2 text-center">
                              <span className={`px-1.5 py-0.2 rounded text-[9px] font-bold border ${sev.bg} ${sev.text} ${sev.border}`}>
                                {row.severity}
                              </span>
                            </td>
                            <td className="p-2 text-[#94a3b8] font-mono text-[10px]">{row.runId}</td>
                            <td className="p-2 text-[#cbd5e1] max-w-xs truncate" title={row.triggerDescription}>
                              {row.triggerDescription}
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </div>

              {/* CROSS-PHASE PIPELINE INTEGRATION CHAIN */}
              <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d] space-y-3">
                <div className="text-xs font-bold text-[#94a3b8] uppercase tracking-wider">
                  CROSS-PHASE DIAGNOSTIC & REMEDIATION PIPELINE
                </div>

                <div className="grid grid-cols-3 gap-3">
                  {/* Phase 6 Investigation Link */}
                  <div className="p-3 rounded bg-[#0a0d14] border border-[#1c2436] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#64748b]">
                      <span className="font-bold text-[#38bdf8]">PHASE 6 ROOT-CAUSE</span>
                      {dossier.investigationTarget ? (
                        <span className="text-[#34d399] font-bold">LINKED</span>
                      ) : (
                        <span className="text-[#64748b]">NO TARGET</span>
                      )}
                    </div>
                    {dossier.investigationTarget ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-white truncate">
                          {dossier.investigationTarget.targetKey}
                        </div>
                        <p className="text-[11px] text-[#94a3b8] line-clamp-2">
                          {dossier.investigationTarget.hypothesis}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => {
                              if (onSelectInvestigationTarget) onSelectInvestigationTarget(dossier.investigationTarget!.targetKey);
                              navigate("06_INVESTIGATION");
                            }}
                            className="text-[10px] text-[#38bdf8] hover:underline block pt-1"
                          >
                            Inspect in Phase 6 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#64748b] italic">
                        No active Phase 6 investigation target formulated for this target.
                      </p>
                    )}
                  </div>

                  {/* Phase 7 Remediation Link */}
                  <div className="p-3 rounded bg-[#0a0d14] border border-[#1c2436] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#64748b]">
                      <span className="font-bold text-[#a78bfa]">PHASE 7 REMEDIATION</span>
                      {dossier.remediation ? (
                        <span className="text-[#34d399] font-bold">PROPOSED</span>
                      ) : (
                        <span className="text-[#64748b]">NO REMEDIATION</span>
                      )}
                    </div>
                    {dossier.remediation ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-white truncate">
                          {dossier.remediation.title}
                        </div>
                        <p className="text-[11px] text-[#94a3b8] line-clamp-2">
                          {dossier.remediation.hypothesis}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => navigate("07_REMEDIATION")}
                            className="text-[10px] text-[#a78bfa] hover:underline block pt-1"
                          >
                            Review in Phase 7 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#64748b] italic">
                        No remediation proposal formulated yet. Requires investigation review.
                      </p>
                    )}
                  </div>

                  {/* Phase 8 Experiment Link */}
                  <div className="p-3 rounded bg-[#0a0d14] border border-[#1c2436] space-y-1.5">
                    <div className="flex items-center justify-between text-[10px] text-[#64748b]">
                      <span className="font-bold text-[#22d3ee]">PHASE 8 VALIDATION</span>
                      {dossier.experiment ? (
                        <span className="text-[#34d399] font-bold">{dossier.experiment.status}</span>
                      ) : (
                        <span className="text-[#64748b]">NO EXPERIMENT</span>
                      )}
                    </div>
                    {dossier.experiment ? (
                      <div className="space-y-1">
                        <div className="text-xs font-bold text-white truncate">
                          {dossier.experiment.id} — {dossier.experiment.title}
                        </div>
                        <p className="text-[11px] text-[#94a3b8] line-clamp-2">
                          Conclusion: {dossier.experiment.conclusion || "In validation"}
                        </p>
                        {navigate && (
                          <button
                            onClick={() => navigate("08_EXPERIMENT")}
                            className="text-[10px] text-[#22d3ee] hover:underline block pt-1"
                          >
                            Inspect in Phase 8 →
                          </button>
                        )}
                      </div>
                    ) : (
                      <p className="text-[11px] text-[#64748b] italic">
                        No experimental validation runs executed for this remediation.
                      </p>
                    )}
                  </div>
                </div>
              </div>

              {/* OPERATOR DECISION CONSOLE */}
              {dossier.decision && (
                <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d] space-y-3">
                  <div className="flex items-center justify-between">
                    <div className="text-xs font-bold text-[#94a3b8] uppercase tracking-wider">
                      OPERATOR DECISION RECOMMENDATION
                    </div>

                    <div className="flex items-center gap-2">
                      <span className="text-xs text-[#64748b]">CONFIDENCE:</span>
                      <span className={`px-2 py-0.5 text-xs font-bold rounded border ${CONFIDENCE_BADGES[dossier.decision.confidence]?.bg} ${CONFIDENCE_BADGES[dossier.decision.confidence]?.text} ${CONFIDENCE_BADGES[dossier.decision.confidence]?.border}`}>
                        {dossier.decision.confidence}
                      </span>
                    </div>
                  </div>

                  <div className="p-3 rounded bg-[#080b11] border border-[#1e2536] space-y-2">
                    <div className="flex items-center gap-2">
                      <span className="text-xs text-[#64748b]">RECOMMENDED ACTION:</span>
                      <span className={`px-2.5 py-1 text-xs font-bold rounded border ${DECISION_STYLES[dossier.decision.recommendation]?.bg} ${DECISION_STYLES[dossier.decision.recommendation]?.text} ${DECISION_STYLES[dossier.decision.recommendation]?.border}`}>
                        {dossier.decision.recommendation}
                      </span>
                    </div>

                    <div className="text-xs text-[#e2e8f0]">
                      <span className="text-[#64748b] font-semibold">RATIONALE:</span>{" "}
                      {dossier.decision.rationale}
                    </div>

                    <div className="text-xs text-[#38bdf8]">
                      <span className="text-[#64748b] font-semibold">NEXT ACTION:</span>{" "}
                      {dossier.decision.nextAction}
                    </div>

                    <div className="text-[10px] text-[#64748b] italic pt-1 border-t border-[#1a202c]">
                      {dossier.decision.constraints}
                    </div>
                  </div>

                  {/* LIFECYCLE ACTION BUTTONS */}
                  <div className="pt-2 border-t border-[#1e2536] flex items-center justify-between flex-wrap gap-2">
                    <div className="text-[11px] text-[#64748b]">OPERATOR ACTIONS:</div>
                    <div className="flex items-center gap-2 flex-wrap">
                      {dossier.lifecycleState === "OPEN" && (
                        <button
                          onClick={() => {
                            setActiveModal("ACKNOWLEDGE");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#f59e0b]/20 hover:bg-[#f59e0b]/30 text-[#fbbf24] border border-[#f59e0b]/40 rounded transition-colors"
                        >
                          ACKNOWLEDGE
                        </button>
                      )}

                      {(dossier.lifecycleState === "OPEN" || dossier.lifecycleState === "ACKNOWLEDGED") && (
                        <button
                          onClick={() => {
                            setActiveModal("INVESTIGATE");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#3b82f6]/20 hover:bg-[#3b82f6]/30 text-[#60a5fa] border border-[#3b82f6]/40 rounded transition-colors"
                        >
                          INVESTIGATE
                        </button>
                      )}

                      {dossier.lifecycleState === "INVESTIGATING" && (
                        <button
                          onClick={() => {
                            setActiveModal("PLAN_MITIGATION");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#8b5cf6]/20 hover:bg-[#8b5cf6]/30 text-[#a78bfa] border border-[#8b5cf6]/40 rounded transition-colors"
                        >
                          PLAN MITIGATION
                        </button>
                      )}

                      {dossier.lifecycleState === "MITIGATION_PLANNED" && (
                        <button
                          onClick={() => {
                            setActiveModal("START_VALIDATION");
                            setModalText("");
                          }}
                          className="px-2.5 py-1 text-xs font-bold bg-[#06b6d4]/20 hover:bg-[#06b6d4]/30 text-[#22d3ee] border border-[#06b6d4]/40 rounded transition-colors"
                        >
                          START VALIDATION
                        </button>
                      )}

                      {dossier.lifecycleState !== "RESOLVED" && (
                        <>
                          <button
                            onClick={() => {
                              setActiveModal("RESOLVE");
                              setModalText("");
                            }}
                            className="px-2.5 py-1 text-xs font-bold bg-[#10b981]/20 hover:bg-[#10b981]/30 text-[#34d399] border border-[#10b981]/40 rounded transition-colors"
                          >
                            RESOLVE
                          </button>

                          <button
                            onClick={() => {
                              setActiveModal("SUPPRESS");
                              setModalText("");
                            }}
                            className="px-2.5 py-1 text-xs font-bold bg-[#64748b]/20 hover:bg-[#64748b]/30 text-[#94a3b8] border border-[#64748b]/40 rounded transition-colors"
                          >
                            SUPPRESS
                          </button>
                        </>
                      )}
                    </div>
                  </div>
                </div>
              )}

              {/* AUDIT EVENT LOG */}
              {dossier.auditEvents && dossier.auditEvents.length > 0 && (
                <div className="p-4 rounded bg-[#0f1420] border border-[#1f293d] space-y-2">
                  <div className="text-xs font-bold text-[#94a3b8] uppercase tracking-wider">
                    INCIDENT AUDIT LOG ({dossier.auditEvents.length} EVENTS)
                  </div>
                  <div className="space-y-1.5 max-h-48 overflow-y-auto">
                    {dossier.auditEvents.map((evt) => (
                      <div
                        key={evt.id}
                        className="p-2 rounded bg-[#090c12] border border-[#1a2030] flex items-center justify-between text-[11px]"
                      >
                        <div className="flex items-center gap-2">
                          <span className="text-[#38bdf8] font-bold">{evt.action}</span>
                          <span className="text-[#64748b]">({evt.previousState} → {evt.newState})</span>
                          <span className="text-[#cbd5e1]">{evt.reason}</span>
                        </div>

                        <div className="flex items-center gap-2 text-[10px] text-[#64748b]">
                          <span>by {evt.actor}</span>
                          <span>•</span>
                          <span>{new Date(evt.timestamp).toLocaleTimeString()}</span>
                        </div>
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      </div>

      {/* OPERATOR ACTION MODAL */}
      {activeModal && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-xs flex items-center justify-center z-50 p-4">
          <div className="bg-[#0f1420] border border-[#1f293d] rounded-lg p-5 max-w-md w-full space-y-4 shadow-2xl">
            <div className="flex items-center justify-between border-b border-[#1f293d] pb-2">
              <span className="text-sm font-bold text-white">
                TRANSITION INCIDENT: {activeModal}
              </span>
              <button
                onClick={() => setActiveModal(null)}
                className="text-[#64748b] hover:text-white"
              >
                ✕
              </button>
            </div>

            <div className="space-y-3 text-xs">
              <div>
                <label className="text-[#64748b] block mb-1">ACTOR</label>
                <input
                  type="text"
                  value={modalActor}
                  onChange={(e) => setModalActor(e.target.value)}
                  className="w-full bg-[#141822] text-white px-2.5 py-1.5 border border-[#263044] rounded"
                />
              </div>

              <div>
                <label className="text-[#64748b] block mb-1">
                  {activeModal === "RESOLVE" ? "RESOLUTION REASON" : "OPERATOR NOTE / RATIONALE"}
                </label>
                <textarea
                  rows={3}
                  value={modalText}
                  onChange={(e) => setModalText(e.target.value)}
                  placeholder="Enter detailed engineering justification..."
                  className="w-full bg-[#141822] text-white px-2.5 py-1.5 border border-[#263044] rounded focus:outline-none focus:border-[#3b82f6]"
                />
              </div>

              {activeModal === "SUPPRESS" && (
                <div>
                  <label className="text-[#64748b] block mb-1">SUPPRESSION DURATION (HOURS)</label>
                  <input
                    type="number"
                    value={modalDuration}
                    onChange={(e) => setModalDuration(parseInt(e.target.value) || 24)}
                    className="w-full bg-[#141822] text-white px-2.5 py-1.5 border border-[#263044] rounded"
                  />
                </div>
              )}
            </div>

            <div className="flex items-center justify-end gap-2 pt-2 border-t border-[#1f293d]">
              <button
                onClick={() => setActiveModal(null)}
                className="px-3 py-1.5 text-xs text-[#94a3b8] hover:bg-[#1a202c] rounded"
              >
                CANCEL
              </button>
              <button
                onClick={() => {
                  if (activeModal === "ACKNOWLEDGE") handleAcknowledge();
                  else if (activeModal === "INVESTIGATE") handleInvestigate();
                  else if (activeModal === "PLAN_MITIGATION") handlePlanMitigation();
                  else if (activeModal === "START_VALIDATION") handleStartValidation();
                  else if (activeModal === "RESOLVE") handleResolve();
                  else if (activeModal === "SUPPRESS") handleSuppress();
                }}
                className="px-4 py-1.5 text-xs font-bold bg-[#3b82f6] hover:bg-[#2563eb] text-white rounded"
              >
                CONFIRM TRANSITION
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
